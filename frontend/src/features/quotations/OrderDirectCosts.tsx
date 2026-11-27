import { useState } from 'react';
import type { FormEvent } from 'react';
import {
  Button,
  ErrorMessage,
  Field,
  SummaryGrid,
} from '../../components/WorkflowUi';
import { ApiError, request } from '../../services/api';
import { gtq, message } from '../../services/commercial';

const categories = {
  MANO_DE_OBRA: 'Mano de obra',
  SUBCONTRATACION: 'Subcontratación',
  ENVIO: 'Envío pagado',
  OTRO: 'Otro costo directo',
};
type Category = keyof typeof categories;
type CostInput = {
  requestId: string;
  category: Category;
  description: string;
  amount: string;
  incurredOn: string;
};
export type DirectCost = Omit<CostInput, 'requestId'> & {
  id: number;
  recordedBy: string;
};
const localDate = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'America/Guatemala',
});

function readPending(key: string): CostInput | null {
  try {
    return JSON.parse(
      sessionStorage.getItem(key) ?? 'null',
    ) as CostInput | null;
  } catch {
    return null;
  }
}

export function OrderDirectCosts({
  token,
  path,
  costs,
  directTotal,
  recordedTotal,
  refresh,
}: {
  token: string;
  path: string;
  costs: DirectCost[];
  directTotal: string;
  recordedTotal: string;
  refresh: () => void;
}) {
  const key = 'cadprint:pending-direct-cost:' + path;
  const [pending, setPending] = useState(() => readPending(key));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function record(input: CostInput) {
    setError('');
    setBusy(true);
    try {
      await request(token, path, {
        method: 'POST',
        body: JSON.stringify(input),
      });
      sessionStorage.removeItem(key);
      setPending(null);
      refresh();
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
    if (busy || pending) return;
    const fields = new FormData(event.currentTarget);
    const amount = String(fields.get('amount') ?? '')
      .trim()
      .replace(',', '.');
    const description = String(fields.get('description') ?? '').trim();
    if (
      !description ||
      !/^[0-9]{1,25}(?:\.[0-9]{1,2})?$/.test(amount) ||
      !/[1-9]/.test(amount)
    ) {
      setError(
        'Ingresa un concepto y un monto mayor que cero con hasta dos decimales.',
      );
      return;
    }
    const input = {
      requestId: crypto.randomUUID(),
      category: fields.get('category') as Category,
      description,
      amount,
      incurredOn: String(fields.get('incurredOn') ?? ''),
    };
    try {
      sessionStorage.setItem(key, JSON.stringify(input));
    } catch {
      setError(
        'El navegador no pudo guardar la solicitud para reintentar. El costo no se envió.',
      );
      return;
    }
    setPending(input);
    void record(input);
  }

  return (
    <section aria-label="Costos directos adicionales">
      <h4>Costos directos adicionales</h4>
      <SummaryGrid
        items={[
          { label: 'Costos adicionales registrados', value: gtq(directTotal) },
          {
            label: 'Total registrado con materiales valorados',
            value: gtq(recordedTotal),
          },
        ]}
      />
      {error && <ErrorMessage>{error}</ErrorMessage>}
      {pending ? (
        <>
          <p role="status">
            Costo pendiente de confirmación · {pending.description}
          </p>
          <Button disabled={busy} onClick={() => void record(pending)}>
            Reintentar costo
          </Button>
        </>
      ) : (
        <form onSubmit={submit}>
          <fieldset className="formGrid commercialFields" disabled={busy}>
            <legend>Registrar un costo adicional</legend>
            <Field label="Categoría del costo">
              {(attributes) => (
                <select {...attributes} name="category">
                  {Object.entries(categories).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              )}
            </Field>
            <Field label="Concepto del costo">
              {(attributes) => (
                <input
                  {...attributes}
                  name="description"
                  maxLength={255}
                  required
                />
              )}
            </Field>
            <Field label="Monto del costo en quetzales">
              {(attributes) => (
                <input
                  {...attributes}
                  name="amount"
                  inputMode="decimal"
                  required
                />
              )}
            </Field>
            <Field label="Fecha del costo">
              {(attributes) => (
                <input
                  {...attributes}
                  name="incurredOn"
                  type="date"
                  defaultValue={localDate.format(new Date())}
                  required
                />
              )}
            </Field>
            <Button type="submit" tone="primary">
              Registrar costo
            </Button>
          </fieldset>
        </form>
      )}
      {costs.length > 0 && (
        <div className="tableWrap">
          <table>
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Categoría</th>
                <th>Concepto</th>
                <th>Monto</th>
                <th>Registrado por</th>
              </tr>
            </thead>
            <tbody>
              {costs.map((cost) => (
                <tr key={cost.id}>
                  <td>{cost.incurredOn}</td>
                  <td>{categories[cost.category]}</td>
                  <td>{cost.description}</td>
                  <td>{gtq(cost.amount)}</td>
                  <td>{cost.recordedBy}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
