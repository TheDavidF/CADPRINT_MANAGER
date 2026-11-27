import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Button, ErrorMessage, Field } from '../../components/WorkflowUi';
import { ApiError, request } from '../../services/api';
import { message } from '../../services/commercial';
import type { Material } from '../../services/types';
import type { Summary } from './quotationApi';

type Consumption = {
  id: number;
  materialName: string;
  unit: string;
  quantity: number;
  afterStock: number;
  username: string;
};

type PendingConsumption = {
  requestId: string;
  materialId: number;
  quantity: string;
};

function readPending(key: string): PendingConsumption | null {
  try {
    const saved = sessionStorage.getItem(key);
    return saved ? (JSON.parse(saved) as PendingConsumption) : null;
  } catch {
    return null;
  }
}

export function ProductionConsumptions({
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
  const path =
    '/quotations/' +
    current.quotationId +
    '/revisions/' +
    current.revisionNumber +
    '/order/production/consumptions';
  const [items, setItems] = useState<Consumption[]>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);
  const [materialId, setMaterialId] = useState('');
  const [quantity, setQuantity] = useState('');
  const storageKey = 'cadprint:pending-consumption:' + path;
  const [pending, setPending] = useState(() => readPending(storageKey));
  const pendingMaterial = materials.find(
    (item) => item.id === pending?.materialId,
  );
  const material = materials.find(
    (item) => String(item.id) === materialId && item.active,
  );

  useEffect(() => {
    const active = new AbortController();
    void request<Consumption[]>(token, path, { signal: active.signal }).then(
      (result) => {
        if (!active.signal.aborted) setItems(result);
      },
      (failure: unknown) => {
        if (!active.signal.aborted) setError(message(failure));
      },
    );
    return () => active.abort();
  }, [token, path, reload]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || pending || !material || !quantity) return;

    const input = {
      requestId: crypto.randomUUID(),
      materialId: material.id,
      quantity,
    };
    try {
      sessionStorage.setItem(storageKey, JSON.stringify(input));
    } catch {
      setError(
        'El navegador no pudo guardar la solicitud para reintentar. El consumo no se envió.',
      );
      return;
    }
    setPending(input);
    await register(input);
  }

  async function register(input: PendingConsumption) {
    setError('');
    setBusy(true);
    try {
      const result = await request<Consumption>(token, path, {
        method: 'POST',
        body: JSON.stringify(input),
      });
      setItems((previous) =>
        previous?.some((item) => item.id === result.id)
          ? previous
          : [...(previous ?? []), result],
      );
      sessionStorage.removeItem(storageKey);
      setPending(null);
      setQuantity('');
      try {
        await refreshInventory();
      } catch {
        setError(
          'El consumo se guardó. No se pudo actualizar el resumen de inventario.',
        );
      }
    } catch (failure) {
      if (
        failure instanceof ApiError &&
        [400, 404, 409, 422].includes(failure.status)
      ) {
        sessionStorage.removeItem(storageKey);
        setPending(null);
      }
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-label="Consumo de materiales">
      <h4>Consumo de materiales</h4>
      {error && <ErrorMessage>{error}</ErrorMessage>}
      {!items &&
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
          <p role="status">Consultando consumo…</p>
        ))}
      {items && (
        <>
          {pending ? (
            <div>
              <p role="status">
                Consumo pendiente de confirmación ·{' '}
                {pendingMaterial?.name ?? 'Material #' + pending.materialId} ·{' '}
                {pending.quantity}
                {pendingMaterial ? ' ' + pendingMaterial.unit : ''}
              </p>
              <Button
                tone="primary"
                disabled={busy}
                onClick={() => void register(pending)}
              >
                {busy ? 'Confirmando…' : 'Reintentar consumo'}
              </Button>
            </div>
          ) : (
            <form onSubmit={(event) => void submit(event)}>
              <fieldset disabled={busy} className="formGrid commercialFields">
                <legend>Registrar salida de inventario</legend>
                <Field label="Material consumido">
                  {(accessibility) => (
                    <select
                      {...accessibility}
                      required
                      value={materialId}
                      onChange={(event) => setMaterialId(event.target.value)}
                    >
                      <option value="">Selecciona un material</option>
                      {materials
                        .filter((item) => item.active)
                        .map((item) => (
                          <option key={item.id} value={item.id}>
                            {item.sku} · {item.name} ({item.unit})
                          </option>
                        ))}
                    </select>
                  )}
                </Field>
                <Field
                  label={
                    material
                      ? 'Cantidad consumida (' + material.unit + ')'
                      : 'Cantidad consumida'
                  }
                >
                  {(accessibility) => (
                    <input
                      {...accessibility}
                      type="number"
                      step="0.001"
                      min="0.001"
                      required
                      value={quantity}
                      onChange={(event) => setQuantity(event.target.value)}
                    />
                  )}
                </Field>
                <Button
                  type="submit"
                  tone="primary"
                  disabled={!material || busy}
                >
                  {busy ? 'Registrando…' : 'Registrar consumo'}
                </Button>
              </fieldset>
            </form>
          )}
          {items.length === 0 ? (
            <p>Sin consumos registrados.</p>
          ) : (
            <div className="tableWrap">
              <table>
                <thead>
                  <tr>
                    <th>Material</th>
                    <th>Cantidad</th>
                    <th>Stock tras la salida</th>
                    <th>Operador</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((item) => (
                    <tr key={item.id}>
                      <td>{item.materialName}</td>
                      <td>
                        {item.quantity} {item.unit}
                      </td>
                      <td>
                        {item.afterStock} {item.unit}
                      </td>
                      <td>{item.username}</td>
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
