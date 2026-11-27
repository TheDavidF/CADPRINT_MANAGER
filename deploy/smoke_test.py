"""Exercise the temporary Compose instance used by GitHub Actions."""

import base64
import json
import os
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


BASE = "http://127.0.0.1:" + os.getenv("CADPRINT_WEB_PORT", "8088")
PASSWORDS = {
    "admin": os.environ["CADPRINT_ADMIN_PASSWORD"],
    "operador": os.environ["CADPRINT_OPERATOR_PASSWORD"],
}


def api(path, user="operador", method="GET", data=None, expected=200):
    headers = {"Content-Type": "application/json"}
    if user is not None:
        credentials = f"{user}:{PASSWORDS[user]}".encode()
        headers["Authorization"] = "Basic " + base64.b64encode(credentials).decode()
    request = Request(
        BASE + "/api" + path,
        method=method,
        headers=headers,
        data=None if data is None else json.dumps(data).encode(),
    )
    try:
        response = urlopen(request, timeout=5)
    except HTTPError as error:
        assert error.code == expected, (path, error.code, error.read().decode())
        return None
    with response:
        assert response.status == expected, (path, response.status)
        return json.load(response)


def wait_for_app():
    for attempt in range(45):
        try:
            return api("/me")
        except (AssertionError, URLError):
            if attempt == 44:
                raise
            time.sleep(2)


def main():
    assert wait_for_app()["roles"] == ["ROLE_OPERADOR"]
    assert api("/me", user="admin")["roles"] == ["ROLE_ADMIN"]
    api("/me", user=None, expected=401)
    with urlopen(BASE, timeout=5) as page:
        assert "CADPRINT" in page.read().decode()

    customer = api("/customers", method="POST", data={"name": "Cliente de prueba"}, expected=201)
    quote = api(
        "/quotations",
        method="POST",
        data={
            "customerId": customer["id"],
            "lines": [{"type": "CUSTOM", "description": "Taza de prueba", "quantity": 1, "unitPrice": 25}],
        },
        expected=201,
    )
    revision = quote["revision"]
    rev = f"/quotations/{revision['quotationId']}/revisions/1"
    api(rev + "/emit", method="POST", data={"version": revision["version"]})
    api(rev + "/accept", method="POST")
    order = api(rev + "/order", method="POST")
    payment = api(
        rev + "/order/payments",
        method="POST",
        data={"requestId": "17fef011-8feb-47bc-befe-2177e47d9b84", "amount": 10, "method": "EFECTIVO"},
    )
    assert payment["paid"] == 10 and payment["balance"] == 15
    api(rev + "/order/profitability", expected=403)
    assert api(rev + "/order/profitability", user="admin")["salesExcludingShipping"] == 25

    with open("compose-smoke-order.json", "w", encoding="utf-8") as saved:
        json.dump({"path": rev, "orderId": order["id"]}, saved)
    print("PASS interfaz, roles, cliente, cotización, pedido, cobro y permisos")


def verify_restart():
    wait_for_app()
    with open("compose-smoke-order.json", encoding="utf-8") as saved:
        order = json.load(saved)
    assert api(order["path"] + "/order")["id"] == order["orderId"]
    payment = api(order["path"] + "/order/payments")
    assert payment["paid"] == 10 and payment["balance"] == 15 and len(payment["payments"]) == 1
    print("PASS pedido y cobro persistieron tras recrear los contenedores")


if __name__ == "__main__":
    import sys

    if sys.argv[1:] == ["--after-restart"]:
        verify_restart()
    else:
        main()
