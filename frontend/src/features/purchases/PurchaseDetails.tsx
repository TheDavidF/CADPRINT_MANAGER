import { useEffect, useState } from 'react';
import type { Material } from '../../services/types';
import {
  dateTime,
  getPurchaseEvents,
  money,
  purchaseError,
  type PurchaseAction,
  type PurchaseDetail,
  type PurchaseEvent,
} from './purchaseApi';

export function materialLabel(materials: Material[], id: number) {
  const material = materials.find((item) => item.id === id);
  return material ? material.sku + ' · ' + material.name : 'Material #' + id;
}

export function PurchaseLines({
  detail,
  materials,
  receiving = false,
}: {
  detail: PurchaseDetail;
  materials: Material[];
  receiving?: boolean;
}) {
  return (
    <div className="tableWrap">
      <table>
        <thead>
          <tr>
            <th>Material</th>
            <th>
              {receiving ? 'Cantidad que ingresará' : 'Cantidad de compra'}
            </th>
            {!receiving && (
              <>
                <th>Precio unitario</th>
                <th>Subtotal</th>
              </>
            )}
          </tr>
        </thead>
        <tbody>
          {detail.lines.map((line) => (
            <tr key={line.id}>
              <td>{materialLabel(materials, line.materialId)}</td>
              <td>
                {receiving
                  ? line.inventoryQuantity === null
                    ? '—'
                    : line.inventoryQuantity + ' ' + line.inventoryUnit
                  : line.quantity + ' ' + line.purchaseUnit}
              </td>
              {!receiving && (
                <>
                  <td>{line.unitPrice} Q</td>
                  <td>{money.format(line.subtotal)}</td>
                </>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function PurchaseDetails({
  detail,
  materials,
  token,
  admin,
  busy,
  receiptUncertain,
  onEdit,
  onAction,
  onBack,
  onReload,
}: {
  detail: PurchaseDetail;
  materials: Material[];
  token: string;
  admin: boolean;
  busy: boolean;
  receiptUncertain: boolean;
  onEdit: () => void;
  onAction: (action: PurchaseAction) => void;
  onBack: () => void;
  onReload: () => void;
}) {
  const { purchase, receipt } = detail;
  const [showEvents, setShowEvents] = useState(false);
  const draft = purchase.status === 'BORRADOR';
  const pending = purchase.status === 'PENDIENTE';

  return (
    <>
      <section>
        <h2>
          Compra #{purchase.id} · {purchase.status}
        </h2>
        <dl className="purchaseSummary">
          <div>
            <dt>Proveedor</dt>
            <dd>{purchase.supplierName ?? 'Sin proveedor'}</dd>
          </div>
          <div>
            <dt>Fecha de compra</dt>
            <dd>{purchase.purchaseDate}</dd>
          </div>
          <div>
            <dt>Documento</dt>
            <dd>{purchase.documentNumber ?? '—'}</dd>
          </div>
          <div>
            <dt>Registrada por</dt>
            <dd>{purchase.createdBy}</dd>
          </div>
          {purchase.notes && (
            <div>
              <dt>Observaciones</dt>
              <dd>{purchase.notes}</dd>
            </div>
          )}
        </dl>
        {detail.lines.length ? (
          <PurchaseLines detail={detail} materials={materials} />
        ) : (
          <p>Este borrador todavía no tiene líneas.</p>
        )}
        <p>
          <strong>Total {money.format(purchase.total)}</strong>
        </p>
        <div className="purchaseActions">
          <button type="button" disabled={busy} onClick={onBack}>
            Volver al listado
          </button>
          <button type="button" disabled={busy} onClick={onReload}>
            {receiptUncertain ? 'Comprobar recepción' : 'Recargar detalle'}
          </button>
          {draft && (
            <>
              <button type="button" disabled={busy} onClick={onEdit}>
                Editar borrador
              </button>
              <button
                type="button"
                disabled={
                  busy || purchase.supplierId === null || !detail.lines.length
                }
                onClick={() => onAction('confirm')}
              >
                Confirmar compra
              </button>
            </>
          )}
          {pending && (
            <button
              type="button"
              disabled={busy || receiptUncertain}
              onClick={() => onAction('receipt')}
            >
              Recibir compra
            </button>
          )}
          {admin && (draft || pending) && (
            <button
              type="button"
              disabled={busy || receiptUncertain}
              onClick={() => onAction('cancel')}
            >
              Cancelar compra
            </button>
          )}
          {admin && pending && (
            <button
              type="button"
              disabled={busy || receiptUncertain}
              onClick={() => onAction('return-to-draft')}
            >
              Devolver a borrador
            </button>
          )}
        </div>
      </section>
      {receipt && (
        <section>
          <h2>Recepción #{receipt.id}</h2>
          <p>
            {receipt.receivedBy} ·{' '}
            {dateTime.format(new Date(receipt.receivedAt))}
          </p>
          <div className="tableWrap">
            <table>
              <thead>
                <tr>
                  <th>Movimiento</th>
                  <th>Material</th>
                  <th>Cantidad</th>
                  <th>Saldo anterior</th>
                  <th>Saldo posterior</th>
                  <th>Usuario y fecha</th>
                  <th>Motivo</th>
                </tr>
              </thead>
              <tbody>
                {receipt.movements.map((movement) => {
                  const unit =
                    detail.lines.find(
                      (line) => line.id === movement.purchaseLineId,
                    )?.inventoryUnit ?? '';
                  return (
                    <tr key={movement.id}>
                      <td>
                        #{movement.id} · {movement.type}
                      </td>
                      <td>{materialLabel(materials, movement.materialId)}</td>
                      <td>
                        {movement.quantity} {unit}
                      </td>
                      <td>{movement.beforeStock}</td>
                      <td>{movement.afterStock}</td>
                      <td>
                        {movement.username} ·{' '}
                        {dateTime.format(new Date(movement.createdAt))}
                      </td>
                      <td>{movement.reason}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>
      )}
      <section>
        <button
          type="button"
          aria-expanded={showEvents}
          onClick={() => setShowEvents(!showEvents)}
        >
          {showEvents ? 'Ocultar historial' : 'Ver historial'}
        </button>
        {showEvents && (
          <PurchaseEvents
            key={purchase.status + ':' + purchase.version}
            token={token}
            id={purchase.id}
            version={purchase.version}
          />
        )}
      </section>
    </>
  );
}

const actionLabels: Record<string, string> = {
  CREATED: 'Creación',
  EDITED: 'Edición',
  CONFIRMED: 'Confirmación',
  RECEIVED: 'Recepción',
  CANCELLED: 'Cancelación',
  RETURNED_TO_DRAFT: 'Devolución a borrador',
  DUPLICATE_DOCUMENT_AUTHORIZED: 'Autorización de documento duplicado',
};

function PurchaseEvents({
  token,
  id,
  version,
}: {
  token: string;
  id: number;
  version: number;
}) {
  const [events, setEvents] = useState<PurchaseEvent[] | null>(null);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    void getPurchaseEvents(token, id, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setEvents(result);
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(purchaseError(reason));
      });
    return () => controller.abort();
  }, [token, id, version, attempt]);

  if (error)
    return (
      <p role="alert">
        {error}{' '}
        <button
          type="button"
          onClick={() => {
            setError('');
            setAttempt((value) => value + 1);
          }}
        >
          Reintentar historial
        </button>
      </p>
    );
  if (!events) return <p role="status">Cargando historial…</p>;
  if (!events.length) return <p>Esta compra todavía no tiene eventos.</p>;
  return (
    <div className="tableWrap purchaseTable">
      <table>
        <thead>
          <tr>
            <th>Acción</th>
            <th>Usuario</th>
            <th>Fecha y hora</th>
            <th>Estado anterior</th>
            <th>Estado nuevo</th>
            <th>Motivo</th>
          </tr>
        </thead>
        <tbody>
          {events.map((event) => (
            <tr key={event.id}>
              <td>{actionLabels[event.eventType] ?? event.eventType}</td>
              <td>{event.username}</td>
              <td>{dateTime.format(new Date(event.createdAt))}</td>
              <td>{event.previousStatus ?? '—'}</td>
              <td>{event.newStatus ?? '—'}</td>
              <td>{event.reason ?? '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
