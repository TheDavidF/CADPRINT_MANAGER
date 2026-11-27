import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import {
  Button,
  ErrorMessage,
  Field,
  SummaryGrid,
} from '../../components/WorkflowUi';
import { ApiError } from '../../services/api';
import { exactRequest, gtq, message } from '../../services/commercial';
import type { Summary } from './quotationApi';

const methods = { EFECTIVO: 'Efectivo', TRANSFERENCIA: 'Transferencia' };
type Method = keyof typeof methods;
type PaymentInput = { requestId: string; amount: string; method: Method };
type Payment = {
  id: number;
  amount: string;
  method: Method;
  receivedBy: string;
};
type PaymentSummary = {
  total: string;
  paid: string;
  balance: string;
  payments: Payment[];
};
const moneyFields = ['total', 'paid', 'balance', 'amount'];

function pendingPayment(key: string): PaymentInput | null {
  try {
    return JSON.parse(
      sessionStorage.getItem(key) ?? 'null',
    ) as PaymentInput | null;
  } catch {
    return null;
  }
}

export function OrderPayments({
  token,
  current,
}: {
  token: string;
  current: Summary;
}) {
  const path =
    '/quotations/' +
    current.quotationId +
    '/revisions/' +
    current.revisionNumber +
    '/order/payments';
  const key = 'cadprint:pending-payment:' + path;
  const [pending, setPending] = useState(() => pendingPayment(key));
  const [summary, setSummary] = useState<PaymentSummary>();
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState<Method>('EFECTIVO');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);

  useEffect(() => {
    const active = new AbortController();
    void exactRequest<PaymentSummary>(token, path, moneyFields, {
      signal: active.signal,
    }).then(
      (value) => {
        if (!active.signal.aborted) setSummary(value);
      },
      (failure: unknown) => {
        if (!active.signal.aborted) setError(message(failure));
      },
    );
    return () => active.abort();
  }, [path, token, reload]);

  async function record(input: PaymentInput) {
    setError('');
    setBusy(true);
    try {
      const updated = await exactRequest<PaymentSummary>(
        token,
        path,
        moneyFields,
        {
          method: 'POST',
          body: JSON.stringify(input),
        },
      );
      setSummary(updated);
      sessionStorage.removeItem(key);
      setPending(null);
      setAmount('');
    } catch (failure) {
      if (
        failure instanceof ApiError &&
        [400, 404, 409, 422].includes(failure.status)
      ) {
        sessionStorage.removeItem(key);
        setPending(null);
      }
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || pending || !amount) return;
    const input = { requestId: crypto.randomUUID(), amount, method };
    try {
      sessionStorage.setItem(key, JSON.stringify(input));
    } catch {
      setError(
        'El navegador no pudo conservar la solicitud para reintentar. El cobro no se envió.',
      );
      return;
    }
    setPending(input);
    void record(input);
  }

  return (
    <section aria-label="Cobros del pedido">
      <h4>Cobros del pedido</h4>
      {error && <ErrorMessage>{error}</ErrorMessage>}
      {!summary &&
        (error ? (
          <Button
            onClick={() => {
              setError('');
              setReload((previous) => previous + 1);
            }}
          >
            Reintentar consulta
          </Button>
        ) : (
          <p role="status">Consultando cobros…</p>
        ))}
      {summary && (
        <>
          <SummaryGrid
            items={[
              { label: 'Total', value: gtq(summary.total) },
              { label: 'Cobrado', value: gtq(summary.paid) },
              { label: 'Saldo pendiente', value: gtq(summary.balance) },
            ]}
          />
          {pending ? (
            <>
              <p role="status">
                Cobro pendiente de confirmación · {gtq(pending.amount)} ·{' '}
                {methods[pending.method]}
              </p>
              <Button disabled={busy} onClick={() => void record(pending)}>
                Reintentar cobro
              </Button>
            </>
          ) : (
            Number(summary.balance) > 0 && (
              <form onSubmit={submit}>
                <fieldset className="formGrid commercialFields" disabled={busy}>
                  <legend>Registrar cobro</legend>
                  <Field label="Monto en quetzales">
                    {(attributes) => (
                      <input
                        {...attributes}
                        type="number"
                        min="0.01"
                        step="0.01"
                        required
                        value={amount}
                        onChange={(event) => setAmount(event.target.value)}
                      />
                    )}
                  </Field>
                  <Field label="Forma de pago">
                    {(attributes) => (
                      <select
                        {...attributes}
                        value={method}
                        onChange={(event) =>
                          setMethod(event.target.value as Method)
                        }
                      >
                        <option value="EFECTIVO">Efectivo</option>
                        <option value="TRANSFERENCIA">Transferencia</option>
                      </select>
                    )}
                  </Field>
                  <Button type="submit" tone="primary">
                    Registrar cobro
                  </Button>
                </fieldset>
              </form>
            )
          )}
          {!!summary.payments.length && (
            <div className="tableWrap">
              <table>
                <thead>
                  <tr>
                    <th>Monto</th>
                    <th>Forma de pago</th>
                    <th>Registrado por</th>
                  </tr>
                </thead>
                <tbody>
                  {summary.payments.map((payment) => (
                    <tr key={payment.id}>
                      <td>{gtq(payment.amount)}</td>
                      <td>{methods[payment.method]}</td>
                      <td>{payment.receivedBy}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </section>
  );
}
