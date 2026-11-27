import { useEffect, useRef, useState } from 'react';
import type { Material } from '../../services/types';
import { PurchaseLines } from './PurchaseDetails';
import { money, type PurchaseAction, type PurchaseDetail } from './purchaseApi';

const titles: Record<PurchaseAction, string> = {
  confirm: 'Confirmar compra',
  receipt: 'Recibir compra',
  cancel: 'Cancelar compra',
  'return-to-draft': 'Devolver a borrador',
};

export function PurchaseActionDialog({
  action,
  detail,
  materials,
  busy,
  error,
  canAuthorize,
  versionConflict,
  onAccept,
  onClose,
  onReload,
}: {
  action: PurchaseAction;
  detail: PurchaseDetail;
  materials: Material[];
  busy: boolean;
  error: string;
  canAuthorize: boolean;
  versionConflict: boolean;
  onAccept: (reason: string, authorizeDuplicate: boolean) => void;
  onClose: () => void;
  onReload: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [reason, setReason] = useState('');
  useEffect(() => {
    if (dialog.current && !dialog.current.open) dialog.current.showModal();
  }, []);

  const requiresReason = action === 'cancel' || action === 'return-to-draft';
  const title = titles[action];
  return (
    <dialog
      ref={dialog}
      className="materialHistory"
      aria-labelledby="purchase-action-title"
      onClose={onClose}
      onCancel={(event) => {
        if (busy) event.preventDefault();
      }}
    >
      <h2 id="purchase-action-title">
        {title} #{detail.purchase.id}
      </h2>
      <p>Proveedor: {detail.purchase.supplierName ?? 'Sin proveedor'}</p>
      {action === 'confirm' && (
        <>
          <PurchaseLines detail={detail} materials={materials} />
          <p>
            <strong>Total {money.format(detail.purchase.total)}</strong>
          </p>
          <p>
            Confirmar pasará la compra a PENDIENTE. Las existencias se
            actualizarán al recibirla.
          </p>
        </>
      )}
      {action === 'receipt' && (
        <>
          <PurchaseLines detail={detail} materials={materials} receiving />
          <p>
            La recepción completa registrará entradas y actualizará las
            existencias.
          </p>
        </>
      )}
      {error && (
        <p role="alert" className="notice">
          {error}
        </p>
      )}
      <form
        onSubmit={(event) => {
          event.preventDefault();
          onAccept(
            reason.trim(),
            (event.nativeEvent as SubmitEvent).submitter?.getAttribute(
              'value',
            ) === 'authorize',
          );
        }}
      >
        {requiresReason && (
          <div className="formGrid">
            <label>
              Motivo
              <input
                required
                maxLength={1000}
                value={reason}
                disabled={busy}
                onChange={(event) => setReason(event.target.value)}
              />
            </label>
          </div>
        )}
        <div className="purchaseActions">
          {versionConflict ? (
            <button type="button" disabled={busy} onClick={onReload}>
              Recargar detalle
            </button>
          ) : (
            <button
              type="submit"
              disabled={busy || (requiresReason && !reason.trim())}
            >
              {busy ? 'Procesando…' : title}
            </button>
          )}
          {canAuthorize && (
            <button type="submit" value="authorize" disabled={busy}>
              Autorizar duplicado y confirmar
            </button>
          )}
          <button
            type="button"
            disabled={busy}
            onClick={() => dialog.current?.close()}
          >
            Cerrar
          </button>
        </div>
      </form>
    </dialog>
  );
}
