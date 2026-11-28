"""Exercise the temporary Compose instance used by GitHub Actions."""

import base64
import hashlib
import json
import os
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


BASE = "http://127.0.0.1:" + os.getenv("CADPRINT_WEB_PORT", "8088")
STOREFRONT = "http://127.0.0.1:" + os.getenv("CADPRINT_STOREFRONT_PORT", "8089")
PASSWORDS = {
    "admin": os.environ["CADPRINT_ADMIN_PASSWORD"],
    "operador": os.environ["CADPRINT_OPERATOR_PASSWORD"],
    "operador.prueba": "clave-temporal-ci-2026",
}


def api(path, user="operador", method="GET", data=None, expected=200, *, content_type="application/json", binary=False):
    headers = {"Content-Type": content_type}
    if user is not None:
        credentials = f"{user}:{PASSWORDS[user]}".encode()
        headers["Authorization"] = "Basic " + base64.b64encode(credentials).decode()
    request = Request(
        BASE + "/api" + path,
        method=method,
        headers=headers,
        data=data if data is None or isinstance(data, bytes) else json.dumps(data).encode(),
    )
    try:
        response = urlopen(request, timeout=5)
    except HTTPError as error:
        assert error.code == expected, (path, error.code, error.read().decode())
        return None
    with response:
        assert response.status == expected, (path, response.status)
        return response.read() if binary else json.load(response)


def wait_for_app():
    for attempt in range(45):
        try:
            return api("/me")
        except (AssertionError, URLError):
            if attempt == 44:
                raise
            time.sleep(2)


def upload_image():
    category = api("/product-categories", user="admin", method="POST",
                   data={"name": "Categoría de prueba"}, expected=201)
    product = api("/products", method="POST", data={
        "sku": "COMPOSE-SMOKE",
        "name": "Producto de prueba",
        "categoryId": category["id"],
        "productionMode": "BAJO_PEDIDO",
        "pricingMode": "REQUIERE_COTIZACION",
    }, expected=201)
    image = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAEElEQVR4nGP4z8AARAwQCgAf7gP9i18U1AAAAABJRU5ErkJggg==")
    boundary = "cadprint-smoke-boundary"
    multipart = (
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.png\"\r\n"
        "Content-Type: image/png\r\n\r\n"
    ).encode() + image + f"\r\n--{boundary}--\r\n".encode()
    path = f"/products/{product['id']}/image"
    api(path + f"?version={product['version']}", method="PUT", data=multipart,
        content_type=f"multipart/form-data; boundary={boundary}")
    assert api(path, binary=True) == image
    return path


def wait_for_pdf(revision):
    path = revision + "/document"
    for _ in range(30):
        status = api(path)["status"]
        if status == "READY":
            pdf = api(path + "/download", binary=True)
            assert pdf.startswith(b"%PDF-")
            return path + "/download"
        assert status != "FAILED", "La generación del PDF falló"
        time.sleep(2)
    raise AssertionError("El PDF no estuvo listo a tiempo")


def checksum(path):
    return hashlib.sha256(api(path, binary=True)).hexdigest()


def check_storefront(expected_title):
    with urlopen(STOREFRONT + "/", timeout=5) as page:
        assert page.url.endswith("/catalogo")
        html = page.read().decode()
        assert "<title>Catálogo CADPRINT</title>" in html
        assert "<title>CADPRINT Manager</title>" not in html
    with urlopen(STOREFRONT + "/api/storefront", timeout=5) as result:
        catalog = json.load(result)
    assert catalog["headline"] == expected_title
    assert [card["title"] for card in catalog["cards"]] == ["Llavero"]
    for path, method, expected in (
        ("/api/storefront-admin", "GET", 404),
        ("/api/me", "GET", 404),
        ("/api/quotations", "GET", 404),
        ("/index.html", "GET", 404),
        ("/api/storefront", "PUT", 405),
    ):
        try:
            with urlopen(Request(STOREFRONT + path, method=method), timeout=5) as result:
                raise AssertionError((path, method, result.status))
        except HTTPError as error:
            assert error.code == expected, (path, method, error.code)


def publish_storefront():
    editor = api("/storefront-admin", user="admin")
    visible = {"title": "Llavero", "category": "", "description": "", "fixedPrice": True,
               "price": "25.00", "visible": True, "image": ""}
    catalog = {"brand": "CADPRINT", "headline": "Catálogo de prueba", "introduction": "",
               "whatsapp": "50255555555", "accent": "#126b60",
               "cards": [visible, {**visible, "title": "Borrador privado", "visible": False}]}
    api("/storefront-admin", user="admin", method="PUT",
        data={"version": editor["version"], "publish": True, "catalog": catalog})
    check_storefront(catalog["headline"])
    return catalog["headline"]


def main():
    assert wait_for_app()["roles"] == ["ROLE_OPERADOR"]
    assert api("/me", user="admin")["roles"] == ["ROLE_ADMIN"]
    api("/me", user=None, expected=401)
    with urlopen(BASE, timeout=5) as page:
        assert "CADPRINT" in page.read().decode()

    api("/accounts", user="admin", method="POST", data={
        "username": "operador.prueba",
        "password": PASSWORDS["operador.prueba"],
        "role": "OPERADOR",
    }, expected=201)
    assert api("/me", user="operador.prueba")["roles"] == ["ROLE_OPERADOR"]
    image_path = upload_image()

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
    (original_payment,) = payment["payments"]
    assert original_payment["method"] == "EFECTIVO"
    api(rev + "/order/profitability", expected=403)
    assert api(rev + "/order/profitability", user="admin")["salesExcludingShipping"] == 25

    storefront_title = publish_storefront()
    pdf_path = wait_for_pdf(rev)
    files = {path: checksum(path) for path in (image_path, pdf_path)}
    with open("compose-smoke-order.json", "w", encoding="utf-8") as saved:
        json.dump({
            "path": rev,
            "orderId": order["id"],
            "paymentId": original_payment["id"],
            "paymentMethod": original_payment["method"],
            "files": files,
            "storefrontTitle": storefront_title,
        }, saved)
    print("PASS interfaz, roles, cliente, cotización, pedido, cobro y permisos")


def verify_restart():
    wait_for_app()
    with open("compose-smoke-order.json", encoding="utf-8") as saved:
        order = json.load(saved)
    assert api(order["path"] + "/order")["id"] == order["orderId"]
    payment = api(order["path"] + "/order/payments")
    assert payment["paid"] == 10 and payment["balance"] == 15 and len(payment["payments"]) == 1
    (restored_payment,) = payment["payments"]
    assert restored_payment["id"] == order["paymentId"], "El ID del cobro cambió"
    assert restored_payment["method"] == order["paymentMethod"], "El método del cobro cambió"
    individual = api("/me", user="operador.prueba")
    assert individual["username"] == "operador.prueba" and individual["roles"] == ["ROLE_OPERADOR"]
    for path, expected_hash in order["files"].items():
        assert checksum(path) == expected_hash, path
    check_storefront(order["storefrontTitle"])
    print("PASS pedido, cobro, cuenta individual, imagen, PDF y catálogo tras recrear los contenedores")


if __name__ == "__main__":
    import sys

    if sys.argv[1:] == ["--after-restart"]:
        verify_restart()
    else:
        main()
