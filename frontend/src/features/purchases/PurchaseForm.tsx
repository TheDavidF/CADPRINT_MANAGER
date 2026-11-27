import { useRef, useState } from 'react';
import type { Material, Supplier } from '../../services/types';
import { money, type DraftPayload, type PurchaseDetail } from './purchaseApi';

type FormLine = {
  key: number;
  materialId: string;
  quantity: string;
  purchaseUnit: string;
  unitPrice: string;
};

const units = ['unidad', 'g', 'kg', 'ml', 'l', 'hoja', 'metro', 'cm'];
const today = () =>
  new Intl.DateTimeFormat('sv-SE', { timeZone: 'America/Guatemala' }).format(
    new Date(),
  );

export function PurchaseForm({
  detail,
  materials,
  suppliers,
  busy,
  canAuthorize,
  onSave,
  onCancel,
}: {
  detail?: PurchaseDetail;
  materials: Material[];
  suppliers: Supplier[];
  busy: boolean;
  canAuthorize: boolean;
  onSave: (input: DraftPayload, authorizeDuplicate: boolean) => void;
  onCancel: () => void;
}) {
  const [supplier, setSupplier] = useState(
    String(detail?.purchase.supplierId ?? ''),
  );
  const [date, setDate] = useState(detail?.purchase.purchaseDate ?? today());
  const [document, setDocument] = useState(
    detail?.purchase.documentNumber ?? '',
  );
  const [notes, setNotes] = useState(detail?.purchase.notes ?? '');
  const [lines, setLines] = useState<FormLine[]>(
    () =>
      detail?.lines.map((line, index) => ({
        key: index,
        materialId: String(line.materialId),
        quantity: String(line.quantity),
        purchaseUnit: line.purchaseUnit,
        unitPrice: String(line.unitPrice),
      })) ?? [],
  );
  const nextKey = useRef(lines.length);
  const subtotal = (line: FormLine) =>
    Math.round(Number(line.quantity) * Number(line.unitPrice) * 100) / 100;

  function update(key: number, changes: Partial<FormLine>) {
    setLines((current) =>
      current.map((line) =>
        line.key === key ? { ...line, ...changes } : line,
      ),
    );
  }

  return (
    <section>
      <h2>
        {detail ? 'Editar compra #' + detail.purchase.id : 'Nueva compra'}
      </h2>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          onSave(
            {
              supplierId: supplier ? Number(supplier) : null,
              purchaseDate: date,
              documentNumber: document.trim() || null,
              notes: notes.trim() || null,
              lines: lines.map((line) => ({
                materialId: Number(line.materialId),
                quantity: line.quantity,
                purchaseUnit: line.purchaseUnit,
                unitPrice: line.unitPrice,
              })),
            },
            (event.nativeEvent as SubmitEvent).submitter?.getAttribute(
              'value',
            ) === 'authorize',
          );
        }}
      >
        <fieldset disabled={busy} className="purchaseFields">
          <div className="formGrid">
            <label>
              Proveedor
              <select
                value={supplier}
                onChange={(event) => setSupplier(event.target.value)}
              >
                <option value="">Sin proveedor</option>
                {suppliers.map((option) => (
                  <option key={option.id} value={option.id}>
                    {option.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Fecha de compra
              <input
                required
                type="date"
                value={date}
                onChange={(event) => setDate(event.target.value)}
              />
            </label>
            <label>
              Documento (opcional)
              <input
                maxLength={255}
                value={document}
                onChange={(event) => setDocument(event.target.value)}
              />
            </label>
            <label>
              Observaciones
              <input
                maxLength={1000}
                value={notes}
                onChange={(event) => setNotes(event.target.value)}
              />
            </label>
          </div>
          {lines.length > 0 && (
            <div className="tableWrap">
              <table>
                <thead>
                  <tr>
                    <th>Material</th>
                    <th>Cantidad</th>
                    <th>Unidad de compra</th>
                    <th>Precio unitario (Q)</th>
                    <th>Subtotal</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {lines.map((line, index) => {
                    const materialUnit = materials.find(
                      (material) => String(material.id) === line.materialId,
                    )?.unit;
                    const options = [
                      ...new Set(
                        [...units, line.purchaseUnit, materialUnit].filter(
                          (unit): unit is string => Boolean(unit),
                        ),
                      ),
                    ];
                    return (
                      <tr key={line.key}>
                        <td>
                          <select
                            aria-label={'Material de línea ' + (index + 1)}
                            required
                            value={line.materialId}
                            onChange={(event) => {
                              const option = materials.find(
                                (item) =>
                                  String(item.id) === event.target.value,
                              );
                              update(line.key, {
                                materialId: event.target.value,
                                purchaseUnit: option?.unit ?? '',
                              });
                            }}
                          >
                            <option value="">Selecciona un material</option>
                            {materials.map((material) => (
                              <option key={material.id} value={material.id}>
                                {material.sku} · {material.name}
                              </option>
                            ))}
                          </select>
                        </td>
                        <td>
                          <input
                            aria-label={'Cantidad de línea ' + (index + 1)}
                            required
                            type="number"
                            min="0.001"
                            step="0.001"
                            value={line.quantity}
                            onChange={(event) =>
                              update(line.key, { quantity: event.target.value })
                            }
                          />
                        </td>
                        <td>
                          <select
                            aria-label={'Unidad de línea ' + (index + 1)}
                            required
                            value={line.purchaseUnit}
                            onChange={(event) =>
                              update(line.key, {
                                purchaseUnit: event.target.value,
                              })
                            }
                          >
                            <option value="">Selecciona una unidad</option>
                            {options.map((unit) => (
                              <option key={unit}>{unit}</option>
                            ))}
                          </select>
                        </td>
                        <td>
                          <input
                            aria-label={'Precio de línea ' + (index + 1)}
                            required
                            type="number"
                            min="0.0001"
                            step="0.0001"
                            value={line.unitPrice}
                            onChange={(event) =>
                              update(line.key, {
                                unitPrice: event.target.value,
                              })
                            }
                          />
                        </td>
                        <td>{money.format(subtotal(line))}</td>
                        <td>
                          <button
                            type="button"
                            onClick={() =>
                              setLines((current) =>
                                current.filter(
                                  (entry) => entry.key !== line.key,
                                ),
                              )
                            }
                          >
                            Quitar línea
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
          <div className="purchaseActions">
            <button
              type="button"
              disabled={lines.length >= 200}
              onClick={() => {
                const key = nextKey.current++;
                setLines((current) => [
                  ...current,
                  {
                    key,
                    materialId: '',
                    quantity: '',
                    purchaseUnit: '',
                    unitPrice: '',
                  },
                ]);
              }}
            >
              Agregar material
            </button>
            <strong>
              Total estimado{' '}
              {money.format(
                lines.reduce((total, line) => total + subtotal(line), 0),
              )}
            </strong>
          </div>
          <div className="purchaseActions">
            <button type="submit">
              {busy ? 'Guardando…' : 'Guardar borrador'}
            </button>
            {canAuthorize && (
              <button type="submit" value="authorize">
                Autorizar duplicado y guardar
              </button>
            )}
            <button type="button" onClick={onCancel}>
              Volver
            </button>
          </div>
        </fieldset>
      </form>
    </section>
  );
}
