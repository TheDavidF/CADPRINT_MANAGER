import { ProductionConsumptions } from './ProductionConsumptions';
import type { Material } from '../../services/types';
import { useEffect, useState } from 'react';
import { Button, ErrorMessage, SummaryGrid } from '../../components/WorkflowUi';
import { ApiError } from '../../services/api';
import { gtq, message } from '../../services/commercial';
import { quotationApi } from './quotationApi';
import type { CustomerOrder, Summary } from './quotationApi';

export function QuotationOrder({
  token,
  current,
  materials,
  refreshInventory,
}: {
  token: string;
  current: Summary;
  materials: Material[];
  refreshInventory: () => Promise<void>;
}) {
  const [order, setOrder] = useState<CustomerOrder | null>();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    const active = new AbortController();
    void quotationApi.order(token, current, active.signal).then(
      (result) => {
        if (!active.signal.aborted) setOrder(result);
      },
      (failure: unknown) => {
        if (active.signal.aborted) return;
        if (failure instanceof ApiError && failure.status === 404)
          setOrder(null);
        else setError(message(failure));
      },
    );
    return () => active.abort();
  }, [token, current, reload]);

  async function create() {
    setBusy(true);
    setError('');
    try {
      setOrder(await quotationApi.order(token, current, undefined, true));
    } catch (failure) {
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }

  async function startProduction() {
    setBusy(true);
    setError('');
    try {
      setOrder(await quotationApi.startProduction(token, current));
    } catch (failure) {
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel" aria-label="Pedido de esta cotización">
      <h3>{order ? 'Pedido #' + order.id : 'Pedido'}</h3>
      {error && <ErrorMessage>{error}</ErrorMessage>}
      {order === undefined && !error && (
        <p role="status">Consultando pedido…</p>
      )}
      {order === undefined && error && (
        <Button
          onClick={() => {
            setError('');
            setReload((previous) => previous + 1);
          }}
        >
          Reintentar consulta
        </Button>
      )}
      {order === null && (
        <Button tone="primary" disabled={busy} onClick={() => void create()}>
          {busy ? 'Creando pedido…' : 'Crear pedido'}
        </Button>
      )}
      {order && (
        <>
          <SummaryGrid
            items={[
              {
                label: 'Cotización de origen',
                value:
                  order.quotation.quotationNumber +
                  ' · Revisión ' +
                  order.quotation.revisionNumber,
              },
              {
                label: 'Cliente',
                value: order.quotation.recipient.name ?? '—',
              },
              {
                label: 'Total acordado',
                value: gtq(order.quotation.totals.totalAmount),
              },
            ]}
          />
          {order.productionStartedAt ? (
            <p role="status">
              En producción desde{' '}
              {new Intl.DateTimeFormat('es-GT', {
                dateStyle: 'medium',
                timeStyle: 'short',
                timeZone: 'America/Guatemala',
              }).format(new Date(order.productionStartedAt))}
              {' · ' + order.productionStartedBy}
            </p>
          ) : (
            <Button disabled={busy} onClick={() => void startProduction()}>
              {busy ? 'Iniciando producción…' : 'Iniciar producción'}
            </Button>
          )}
          <div className="tableWrap">
            <table>
              <thead>
                <tr>
                  <th>Artículo</th>
                  <th>Cantidad</th>
                  <th>Precio unitario</th>
                  <th>Neto de línea</th>
                </tr>
              </thead>
              <tbody>
                {order.quotation.lines.map((line) => (
                  <tr key={line.position}>
                    <td>
                      {line.name}
                      {line.description && line.description !== line.name && (
                        <div>{line.description}</div>
                      )}
                    </td>
                    <td>{line.quantity}</td>
                    <td>{gtq(line.unitPrice)}</td>
                    <td>{gtq(line.netAmount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {order.productionStartedAt && (
            <ProductionConsumptions
              token={token}
              current={current}
              materials={materials}
              refreshInventory={refreshInventory}
            />
          )}
        </>
      )}
    </section>
  );
}
