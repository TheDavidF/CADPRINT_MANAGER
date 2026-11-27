import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../../services/api';
import type { Material } from '../../services/types';
import { message } from '../../services/commercial';
import { CatalogPicker } from '../products/CatalogPicker';
import { costApi, techniques } from './costApi';
import type {
  CostInput,
  HistoryDetail,
  Machine,
  Preview,
  Profile,
  Technique,
} from './costApi';
import { CostResult } from './CostResult';

export function CostCalculator({
  token,
  technique,
  materials,
  profiles,
  machines,
  onSaved,
}: {
  token: string;
  technique: Technique;
  materials: Material[];
  profiles: Profile[];
  machines: Machine[];
  onSaved: (saved: HistoryDetail) => void;
}) {
  const next = useRef(1);
  const [rows, setRows] = useState([{ key: 0, material: '' }]);
  const [catalog, setCatalog] = useState({ productId: '', variantId: '' });
  const [quotePrice, setQuotePrice] = useState(false);
  const [preview, setPreview] = useState<{
    result: Preview;
    input: CostInput;
  } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  function invalidate() {
    setPreview(null);
    setError('');
  }
  async function calculate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const value = (key: string) => String(data.get(key) ?? '').trim();
    const seconds = (key: string) => Number(value(key));
    const common = {
      jobDescription: value('jobDescription'),
      expectedGoodUnits: seconds('expectedGoodUnits'),
      costProfileId: seconds('costProfileId'),
      machineId: seconds('machineId'),
      productiveSeconds: seconds('productiveSeconds'),
      powerWatts: value('powerWatts') || null,
      targetMarginPct: value('targetMarginPct'),
      chosenUnitPrice: quotePrice ? value('chosenUnitPrice') : null,
      ...(catalog.variantId
        ? { variantId: Number(catalog.variantId) }
        : catalog.productId
          ? { productId: Number(catalog.productId) }
          : {}),
      materials: rows.map(({ key }) => ({
        materialId: seconds('material' + key),
        quantity: value('quantity' + key),
        unit: value('unit' + key),
        acceptZeroCost: data.has('zero' + key),
        zeroCostReason: value('reason' + key) || null,
      })),
    };
    const laborSeconds = seconds('laborSeconds');
    const input: CostInput =
      technique === 'FDM'
        ? {
            common,
            laborSeconds,
            printSeconds: seconds('printSeconds'),
            partMassGrams: value('partMassGrams'),
            supportMassGrams: value('supportMassGrams') || null,
          }
        : {
            common,
            laborSeconds,
            cycleCount: seconds('cycleCount'),
            pressSecondsPerCycle: seconds('pressSecondsPerCycle'),
            preparationSeconds: seconds('preparationSeconds'),
          };
    setBusy(true);
    setError('');
    setPreview(null);
    try {
      setPreview({
        input,
        result: await costApi.preview(token, technique, input),
      });
    } catch (failure) {
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }
  async function save() {
    if (!preview) return;
    setBusy(true);
    setError('');
    try {
      onSaved(
        await costApi.save(
          token,
          technique,
          preview.input,
          preview.result.previewToken,
        ),
      );
      setPreview(null);
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 409) {
        setPreview(null);
        setError(
          'La vista previa quedó obsoleta. Recalcula con los datos del formulario.',
        );
      } else setError(message(failure));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <section className="panel">
        <h2>{techniques[technique]}</h2>
        <form onSubmit={(event) => void calculate(event)} onChange={invalidate}>
          <fieldset className="formGrid commercialFields" disabled={busy}>
            <label>
              Descripción del trabajo
              <input name="jobDescription" required maxLength={1000} />
            </label>
            <Integer
              name="expectedGoodUnits"
              label="Unidades buenas previstas"
              minimum={1}
              initial="1"
            />
            <label>
              Perfil de costos
              <select name="costProfileId" required>
                <option value="">Selecciona perfil</option>
                {profiles.map((profile) => (
                  <option key={profile.id} value={profile.id}>
                    {profile.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Máquina
              <select name="machineId" required>
                <option value="">Selecciona máquina</option>
                {machines
                  .filter((machine) => machine.technique === technique)
                  .map((machine) => (
                    <option key={machine.id} value={machine.id}>
                      {machine.name}
                    </option>
                  ))}
              </select>
            </label>
            {technique === 'FDM' ? (
              <>
                <Integer
                  name="printSeconds"
                  label="Tiempo de impresión (s)"
                  minimum={1}
                />
                <Decimal
                  name="partMassGrams"
                  label="Masa de piezas (g)"
                  required
                />
                <Decimal name="supportMassGrams" label="Masa de soportes (g)" />
              </>
            ) : (
              <>
                <Integer
                  name="cycleCount"
                  label="Ciclos de prensado"
                  minimum={1}
                />
                <Integer
                  name="pressSecondsPerCycle"
                  label="Tiempo por ciclo (s)"
                  minimum={1}
                />
                <Integer
                  name="preparationSeconds"
                  label="Preparación (s)"
                  initial="0"
                />
              </>
            )}
            <Integer
              name="laborSeconds"
              label="Trabajo humano total (s)"
              initial="0"
            />
            <Integer
              name="productiveSeconds"
              label="Tiempo productivo para indirectos (s)"
              initial="0"
            />
            <Decimal
              name="powerWatts"
              label="Potencia específica (W, opcional)"
            />
            <Decimal
              name="targetMarginPct"
              label="Margen objetivo (%)"
              initial="20"
              required
            />
            <label>
              Precio elegido
              <select
                value={quotePrice ? 'custom' : 'suggested'}
                onChange={(event) =>
                  setQuotePrice(event.target.value === 'custom')
                }
              >
                <option value="suggested">Usar sugerido</option>
                <option value="custom">Indicar otro</option>
              </select>
            </label>
            {quotePrice && (
              <Decimal
                name="chosenUnitPrice"
                label="Precio elegido en GTQ (admite 0)"
                required
              />
            )}
            <CatalogPicker
              token={token}
              value={catalog}
              onChange={(value) => {
                setCatalog(value);
                invalidate();
              }}
            />
          </fieldset>
          <fieldset className="commercialFields" disabled={busy}>
            <legend>Materiales consumidos</legend>
            {rows.map(({ key, material }) => {
              const source = materials.find(
                (entry) => String(entry.id) === material,
              );
              return (
                <div className="formGrid commercialLine" key={key}>
                  <label>
                    Material
                    <select
                      name={'material' + key}
                      value={material}
                      required
                      onChange={(event) =>
                        setRows(
                          rows.map((row) =>
                            row.key === key
                              ? { ...row, material: event.target.value }
                              : row,
                          ),
                        )
                      }
                    >
                      <option value="">Selecciona material</option>
                      {materials
                        .filter((entry) => entry.active)
                        .map((entry) => (
                          <option value={entry.id} key={entry.id}>
                            {entry.sku} · {entry.name}
                          </option>
                        ))}
                    </select>
                  </label>
                  <Decimal
                    name={'quantity' + key}
                    label="Cantidad consumida"
                    required
                  />
                  <label>
                    Unidad de consumo
                    <input
                      name={'unit' + key}
                      key={material}
                      defaultValue={source?.unit ?? ''}
                      required
                      maxLength={64}
                    />
                  </label>
                  {source?.unitCost === 0 && (
                    <>
                      <label>
                        <span>
                          <input type="checkbox" name={'zero' + key} required />{' '}
                          Confirmar costo cero
                        </span>
                      </label>
                      <label>
                        Motivo del costo cero
                        <input name={'reason' + key} required maxLength={500} />
                      </label>
                    </>
                  )}
                  <button
                    type="button"
                    disabled={rows.length === 1}
                    onClick={() => {
                      setRows(rows.filter((row) => row.key !== key));
                      invalidate();
                    }}
                  >
                    Quitar material
                  </button>
                </div>
              );
            })}
            <div className="commercialActions">
              <button
                type="button"
                disabled={rows.length >= 200}
                onClick={() => {
                  setRows([...rows, { key: next.current++, material: '' }]);
                  invalidate();
                }}
              >
                Agregar material
              </button>
              <button type="submit">Calcular vista previa</button>
            </div>
          </fieldset>
        </form>
        {error && (
          <p role="alert" className="warning">
            {error}
          </p>
        )}
      </section>
      {preview && (
        <>
          <CostResult result={preview.result} />
          <div className="commercialActions">
            <button type="button" disabled={busy} onClick={() => void save()}>
              Guardar cálculo
            </button>
          </div>
        </>
      )}
    </>
  );
}
function Integer({
  name,
  label,
  minimum = 0,
  initial,
}: {
  name: string;
  label: string;
  minimum?: number;
  initial?: string;
}) {
  return (
    <label>
      {label}
      <input
        name={name}
        type="number"
        min={minimum}
        max={Number.MAX_SAFE_INTEGER}
        step="1"
        defaultValue={initial}
        required
      />
    </label>
  );
}
function Decimal({
  name,
  label,
  initial,
  required,
}: {
  name: string;
  label: string;
  initial?: string;
  required?: boolean;
}) {
  return (
    <label>
      {label}
      <input
        name={name}
        inputMode="decimal"
        pattern="[0-9]+(\.[0-9]+)?"
        defaultValue={initial}
        required={required}
      />
    </label>
  );
}
