import { useEffect, useState } from 'react';
import { gtq, message } from '../../services/commercial';
import { quotationApi } from './quotationApi';
import type {
  CommercialLine,
  CommercialView,
  Detail,
  Summary,
  Totals,
} from './quotationApi';

export function QuotationDetails({ detail }: { detail: Detail }) {
  const { revision, totals, lines } = detail;
  return (
    <section className="panel">
      <h2>Artículos e importes</h2>
      {revision.validUntil && <p>Último día válido: {revision.validUntil}</p>}
      {detail.commercialTerms && (
        <p className="commercialText">{detail.commercialTerms}</p>
      )}
      {detail.notes && (
        <p>
          <strong>Nota interna:</strong> {detail.notes}
        </p>
      )}
      <LineTable lines={lines.map((line) => line.commercial)} />
      {lines.some((line) => line.belowHistoricalCost) && (
        <p className="warning">
          Precio bajo costo en artículos{' '}
          {lines
            .filter((line) => line.belowHistoricalCost)
            .map((line) => line.commercial.position)
            .join(', ')}
          . La comparación usa el costo histórico y excluye el descuento
          general.
        </p>
      )}
      <Amounts totals={totals} />
    </section>
  );
}
export function CustomerQuotation({
  token,
  current,
}: {
  token: string;
  current: Summary;
}) {
  const [view, setView] = useState<CommercialView>();
  const [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    void quotationApi
      .commercial(token, current, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setView(result);
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) setError(message(failure));
      });
    return () => controller.abort();
  }, [token, current]);
  return (
    <section className="panel">
      <h2>Vista comercial</h2>
      {error && <p role="alert">{error}</p>}
      {!view && !error && <p role="status">Cargando vista comercial…</p>}
      {view && (
        <>
          <h3>
            {view.quotationNumber} · Revisión {view.revisionNumber}
          </h3>
          <p>
            Emisión:{' '}
            {new Date(view.issuedAt).toLocaleDateString('es-GT', {
              timeZone: 'America/Guatemala',
            })}{' '}
            · Último día válido: {view.validUntil}
          </p>
          <p>
            <strong>Destinatario:</strong>{' '}
            {Object.values(view.recipient).filter(Boolean).join(' · ')}
          </p>
          <LineTable lines={view.lines} />
          <Amounts totals={view.totals} />
          {view.commercialTerms && (
            <p className="commercialText">{view.commercialTerms}</p>
          )}
          <p>
            <strong>Emisor:</strong>{' '}
            {Object.values(view.issuer).filter(Boolean).join(' · ')}
          </p>
        </>
      )}
    </section>
  );
}
function LineTable({ lines }: { lines: CommercialLine[] }) {
  if (lines.length === 0) return <p>Sin artículos.</p>;
  return (
    <div className="tableWrap">
      <table>
        <thead>
          <tr>
            <th>Artículo</th>
            <th>Cantidad</th>
            <th>Precio unitario</th>
            <th>Descuento</th>
            <th>Neto</th>
          </tr>
        </thead>
        <tbody>
          {lines.map((line) => (
            <tr key={line.position}>
              <td className="commercialText">
                <strong>{line.name}</strong>
                {line.sku && ' · ' + line.sku}
                {line.description && <div>{line.description}</div>}
              </td>
              <td>{line.quantity}</td>
              <td>{gtq(line.unitPrice)}</td>
              <td>{gtq(line.discountAmount)}</td>
              <td>{gtq(line.netAmount)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
function Amounts({ totals }: { totals: Totals }) {
  return (
    <dl className="commercialTotals">
      <dt>Bruto</dt>
      <dd>{gtq(totals.grossAmount)}</dd>
      <dt>Descuentos de línea</dt>
      <dd>{gtq(totals.lineDiscountTotal)}</dd>
      <dt>Subtotal</dt>
      <dd>{gtq(totals.subtotal)}</dd>
      <dt>Descuento general</dt>
      <dd>{gtq(totals.generalDiscountAmount)}</dd>
      <dt>Envío</dt>
      <dd>{gtq(totals.shippingAmount)}</dd>
      <dt>Total en GTQ</dt>
      <dd>
        <strong>{gtq(totals.totalAmount)}</strong>
      </dd>
    </dl>
  );
}
