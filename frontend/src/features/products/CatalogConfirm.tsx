import { useEffect, useRef } from 'react';

export function CatalogConfirm({
  label,
  busy,
  error,
  onConfirm,
  onClose,
}: {
  label: string;
  busy: boolean;
  error: string;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    if (dialog.current && !dialog.current.open) dialog.current.showModal();
  }, []);
  return (
    <dialog
      ref={dialog}
      className="materialHistory"
      aria-labelledby="catalog-confirm-title"
      onClose={onClose}
      onCancel={(event) => {
        if (busy) event.preventDefault();
      }}
    >
      <h2 id="catalog-confirm-title">{label}</h2>
      {error && (
        <p className="notice" role="alert">
          {error}
        </p>
      )}
      <div className="purchaseActions">
        <button
          type="button"
          disabled={busy}
          onClick={() => dialog.current?.close()}
        >
          Volver
        </button>
        <button type="button" disabled={busy} onClick={onConfirm}>
          {busy ? 'Procesando…' : 'Confirmar'}
        </button>
      </div>
    </dialog>
  );
}
