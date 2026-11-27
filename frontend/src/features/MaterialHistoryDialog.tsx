import { useEffect, useId, useRef, useState } from 'react';
import { request } from '../services/api';
import type { Material, MaterialChange } from '../services/types';

const fieldLabels: Record<string, string> = {
  name: 'Nombre',
  category: 'Categoría',
  unit: 'Unidad',
  minStock: 'Stock mínimo',
  unitCost: 'Costo unitario de referencia',
  active: 'Estado',
};

const dateTime = new Intl.DateTimeFormat('es-GT', {
  dateStyle: 'medium',
  timeStyle: 'medium',
  timeZone: 'America/Guatemala',
});

type HistoryState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; changes: MaterialChange[] };

export function MaterialHistoryDialog({
  material,
  token,
  onClose,
}: {
  material: Material;
  token: string;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const [history, setHistory] = useState<HistoryState>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (dialog.current && !dialog.current.open) dialog.current.showModal();
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void request<MaterialChange[]>(
      token,
      '/materials/' + material.id + '/changes',
      {
        signal: controller.signal,
      },
    )
      .then((changes) => {
        if (!controller.signal.aborted)
          setHistory({ status: 'ready', changes });
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setHistory({
          status: 'error',
          message:
            error instanceof Error
              ? error.message
              : 'No fue posible consultar el historial.',
        });
      });
    return () => controller.abort();
  }, [material.id, token, attempt]);

  function retry() {
    setHistory({ status: 'loading' });
    setAttempt((current) => current + 1);
  }

  return (
    <dialog
      className="materialHistory"
      ref={dialog}
      aria-labelledby={titleId}
      onClose={onClose}
    >
      <div className="materialHistoryHeader">
        <div>
          <h2 id={titleId}>Historial de modificaciones</h2>
          <p>
            <strong>{material.name}</strong>{' '}
            <span className="hint">SKU {material.sku}</span>
          </p>
        </div>
        <form method="dialog">
          <button type="submit">Cerrar</button>
        </form>
      </div>
      {history.status === 'loading' && <p role="status">Cargando historial…</p>}
      {history.status === 'error' && (
        <div className="notice">
          <span role="alert">{history.message}</span>
          <button type="button" onClick={retry}>
            Reintentar
          </button>
        </div>
      )}
      {history.status === 'ready' &&
        (history.changes.length === 0 ? (
          <p role="status">
            Este material todavía no tiene historial de modificaciones.
          </p>
        ) : (
          <div className="tableWrap">
            <table aria-label="Cambios del material">
              <thead>
                <tr>
                  <th scope="col">Campo</th>
                  <th scope="col">Valor anterior</th>
                  <th scope="col">Valor nuevo</th>
                  <th scope="col">Usuario</th>
                  <th scope="col">Fecha y hora</th>
                </tr>
              </thead>
              <tbody>
                {history.changes.map((change) => (
                  <tr key={change.id}>
                    <td>{fieldLabels[change.field] ?? change.field}</td>
                    <td>{formatValue(change.field, change.previousValue)}</td>
                    <td>{formatValue(change.field, change.newValue)}</td>
                    <td>{change.username}</td>
                    <td>
                      <time dateTime={change.changedAt}>
                        {dateTime.format(new Date(change.changedAt))}
                      </time>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
    </dialog>
  );
}

function formatValue(field: string, value: string | null) {
  if (value === null || value === '') return 'Sin valor';
  if (field === 'active') {
    if (value === 'true') return 'Activo';
    if (value === 'false') return 'Inactivo';
  }
  return value;
}
