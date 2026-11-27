import { useEffect, useState } from 'react';
import {
  Button,
  ErrorMessage,
  Field,
  PanelHeader,
  SummaryGrid,
} from '../../components/WorkflowUi';
import { exactRequest, gtq, message } from '../../services/commercial';

type DailyCash = {
  date: string;
  cash: string;
  transfers: string;
  total: string;
  payments: {
    id: number;
    orderId: number;
    quotationNumber: string;
    amount: string;
    method: 'EFECTIVO' | 'TRANSFERENCIA';
    receivedAt: string;
    receivedBy: string;
  }[];
};

const moneyFields = ['cash', 'transfers', 'total', 'amount'];
const guatemala = 'America/Guatemala';
const dateFormat = new Intl.DateTimeFormat('sv-SE', { timeZone: guatemala });
const timeFormat = new Intl.DateTimeFormat('es-GT', {
  timeZone: guatemala,
  timeStyle: 'short',
});

export function DailyCashPage({ token }: { token: string }) {
  const [date, setDate] = useState(() => dateFormat.format(new Date()));
  const [report, setReport] = useState<DailyCash>();
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);

  useEffect(() => {
    if (!date) return;
    const active = new AbortController();
    void exactRequest<DailyCash>(
      token,
      '/cash/daily?date=' + encodeURIComponent(date),
      moneyFields,
      {
        signal: active.signal,
      },
    ).then(
      (result) => {
        if (!active.signal.aborted) setReport(result);
      },
      (failure: unknown) => {
        if (!active.signal.aborted) setError(message(failure));
      },
    );
    return () => active.abort();
  }, [token, date, reload]);

  return (
    <section className="panel">
      <PanelHeader title="Cobros del día" />
      <Field label="Fecha de caja">
        {(attributes) => (
          <input
            {...attributes}
            type="date"
            value={date}
            onChange={(event) => {
              setReport(undefined);
              setError('');
              setDate(event.target.value);
            }}
          />
        )}
      </Field>
      {!date && <p>Selecciona una fecha.</p>}
      {error && (
        <>
          <ErrorMessage>{error}</ErrorMessage>
          <Button
            onClick={() => {
              setError('');
              setReload((previous) => previous + 1);
            }}
          >
            Reintentar consulta
          </Button>
        </>
      )}
      {date && !report && !error && <p role="status">Consultando cobros…</p>}
      {report && (
        <>
          <SummaryGrid
            items={[
              { label: 'Efectivo recibido', value: gtq(report.cash) },
              {
                label: 'Transferencias recibidas',
                value: gtq(report.transfers),
              },
              { label: 'Total cobrado', value: gtq(report.total) },
            ]}
          />
          {report.payments.length ? (
            <div className="tableWrap">
              <table>
                <thead>
                  <tr>
                    <th>Hora</th>
                    <th>Pedido</th>
                    <th>Cotización</th>
                    <th>Forma de pago</th>
                    <th>Monto</th>
                    <th>Registrado por</th>
                  </tr>
                </thead>
                <tbody>
                  {report.payments.map((payment) => (
                    <tr key={payment.id}>
                      <td>{timeFormat.format(new Date(payment.receivedAt))}</td>
                      <td>#{payment.orderId}</td>
                      <td>{payment.quotationNumber}</td>
                      <td>
                        {payment.method === 'EFECTIVO'
                          ? 'Efectivo'
                          : 'Transferencia'}
                      </td>
                      <td>{gtq(payment.amount)}</td>
                      <td>{payment.receivedBy}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p>No hubo cobros en esta fecha.</p>
          )}
        </>
      )}
    </section>
  );
}
