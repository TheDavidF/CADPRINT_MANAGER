import { useState } from 'react';
import { Plus } from 'lucide-react';
import type { Material, Movement } from '../services/types';
import type { SubmitForm } from '../services/form';
export function MovementsPage({
  materials,
  movements,
  busy,
  submit,
}: {
  materials: Material[];
  movements: Movement[];
  busy: boolean;
  submit: SubmitForm;
}) {
  const [moveForm, setMoveForm] = useState({
    materialId: '',
    type: 'ENTRADA',
    quantity: '',
    reason: '',
  });
  return (
    <>
      <section>
        <h2>
          <Plus size={18} /> Registrar movimiento
        </h2>
        <form
          className="formGrid"
          onSubmit={(e) => {
            e.preventDefault();
            void submit(
              '/movements',
              {
                ...moveForm,
                materialId: Number(moveForm.materialId),
                quantity: Number(moveForm.quantity),
              },
              () =>
                setMoveForm({
                  materialId: '',
                  type: 'ENTRADA',
                  quantity: '',
                  reason: '',
                }),
            );
          }}
        >
          <label>
            Material
            <select
              required
              value={moveForm.materialId}
              onChange={(e) =>
                setMoveForm({ ...moveForm, materialId: e.target.value })
              }
            >
              <option value="">Seleccionar</option>
              {materials
                .filter((m) => m.active)
                .map((m) => (
                  <option value={m.id} key={m.id}>
                    {m.name} ({m.stock} {m.unit})
                  </option>
                ))}
            </select>
          </label>
          <label>
            Tipo
            <select
              value={moveForm.type}
              onChange={(e) =>
                setMoveForm({ ...moveForm, type: e.target.value })
              }
            >
              <option>ENTRADA</option>
              <option>SALIDA</option>
              <option>AJUSTE_MAS</option>
              <option>AJUSTE_MENOS</option>
            </select>
          </label>
          <label>
            Cantidad
            <input
              type="number"
              min="0.001"
              step="0.001"
              required
              value={moveForm.quantity}
              onChange={(e) =>
                setMoveForm({ ...moveForm, quantity: e.target.value })
              }
            />
          </label>
          <label>
            Motivo
            <input
              required
              value={moveForm.reason}
              onChange={(e) =>
                setMoveForm({ ...moveForm, reason: e.target.value })
              }
            />
          </label>
          <button disabled={busy}>Guardar movimiento</button>
        </form>
      </section>
      <section>
        <h2>Últimos 100 movimientos</h2>
        <div className="tableWrap">
          <table>
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Material</th>
                <th>Tipo</th>
                <th>Cantidad</th>
                <th>Antes → Después</th>
                <th>Usuario</th>
                <th>Motivo</th>
              </tr>
            </thead>
            <tbody>
              {movements.map((m) => (
                <tr key={m.id}>
                  <td>{new Date(m.createdAt).toLocaleString('es-GT')}</td>
                  <td>{m.material.name}</td>
                  <td>{m.type}</td>
                  <td>{m.quantity}</td>
                  <td>
                    {m.beforeStock} → {m.afterStock}
                  </td>
                  <td>{m.username}</td>
                  <td>{m.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
