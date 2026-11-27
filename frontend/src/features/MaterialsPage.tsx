import { useState } from 'react';
import { MaterialHistoryDialog } from './MaterialHistoryDialog';
import { Plus } from 'lucide-react';
import type { Category, Material } from '../services/types';
import type { SubmitForm } from '../services/form';
export function MaterialsPage({
  token,
  materials,
  categories,
  busy,
  submit,
}: {
  token: string;
  materials: Material[];
  categories: Category[];
  busy: boolean;
  submit: SubmitForm;
}) {
  const defaults = {
    sku: '',
    name: '',
    categoryId: String(
      categories.find((category) => category.name === 'Filamento')?.id ??
        categories[0]?.id ??
        '',
    ),
    unit: 'g',
    minStock: '100',
    unitCost: '0',
    active: true,
  };
  const [materialForm, setMaterialForm] = useState(defaults);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [search, setSearch] = useState('');
  const [historyMaterial, setHistoryMaterial] = useState<Material | null>(null);
  const [categoryFilter, setCategoryFilter] = useState('');
  const units = ['unidad', 'g', 'kg', 'ml', 'l', 'hoja', 'metro', 'cm'];
  if (editingId !== null && !units.includes(materialForm.unit))
    units.push(materialForm.unit);
  const query = search.trim().toLocaleLowerCase('es-GT');
  const visible = materials.filter(
    (material) =>
      (!categoryFilter || String(material.categoryId) === categoryFilter) &&
      [material.name, material.sku].some((value) =>
        value.toLocaleLowerCase('es-GT').includes(query),
      ),
  );

  function resetForm() {
    setEditingId(null);
    setMaterialForm(defaults);
  }

  function editMaterial(material: Material) {
    setEditingId(material.id);
    setMaterialForm({
      sku: material.sku,
      name: material.name,
      categoryId: String(material.categoryId),
      unit: material.unit,
      minStock: String(material.minStock),
      unitCost: String(material.unitCost),
      active: material.active,
    });
  }
  return (
    <>
      <section>
        <h2>
          <Plus size={18} />{' '}
          {editingId === null ? 'Nuevo material' : 'Editar material'}
        </h2>
        <form
          className="formGrid"
          onSubmit={(e) => {
            e.preventDefault();
            const { sku, categoryId, ...fields } = materialForm;
            const values = { ...fields, categoryId: Number(categoryId) };
            const editing = editingId !== null;
            void submit(
              editing ? '/materials/' + editingId : '/materials',
              editing ? values : { sku, ...values },
              resetForm,
              editing ? 'PUT' : 'POST',
            );
          }}
        >
          <label>
            SKU
            <input
              required
              readOnly={editingId !== null}
              value={materialForm.sku}
              onChange={(e) =>
                setMaterialForm({ ...materialForm, sku: e.target.value })
              }
            />
          </label>
          <label>
            Nombre
            <input
              required
              value={materialForm.name}
              onChange={(e) =>
                setMaterialForm({ ...materialForm, name: e.target.value })
              }
            />
          </label>
          <label>
            Categoría
            <select
              required
              value={materialForm.categoryId}
              onChange={(e) =>
                setMaterialForm({ ...materialForm, categoryId: e.target.value })
              }
            >
              <option value="" disabled>
                Selecciona una categoría
              </option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Unidad
            <select
              value={materialForm.unit}
              onChange={(e) =>
                setMaterialForm({ ...materialForm, unit: e.target.value })
              }
            >
              {units.map((x) => (
                <option key={x}>{x}</option>
              ))}
            </select>
          </label>
          <label>
            Stock mínimo
            <input
              type="number"
              min="0"
              step="0.001"
              required
              value={materialForm.minStock}
              onChange={(e) =>
                setMaterialForm({ ...materialForm, minStock: e.target.value })
              }
            />
          </label>
          <label>
            Costo unitario de referencia (Q)
            <input
              type="number"
              min="0"
              step="0.0001"
              required
              value={materialForm.unitCost}
              onChange={(e) =>
                setMaterialForm({ ...materialForm, unitCost: e.target.value })
              }
            />
          </label>
          {editingId !== null && (
            <label>
              Estado
              <select
                value={materialForm.active ? 'active' : 'inactive'}
                onChange={(event) =>
                  setMaterialForm({
                    ...materialForm,
                    active: event.target.value === 'active',
                  })
                }
              >
                <option value="active">Activo</option>
                <option value="inactive">Inactivo</option>
              </select>
            </label>
          )}
          <button disabled={busy}>
            {editingId === null ? 'Registrar material' : 'Guardar cambios'}
          </button>
          {editingId !== null && (
            <button type="button" disabled={busy} onClick={resetForm}>
              Cancelar
            </button>
          )}
        </form>
        <p className="hint">
          El stock inicial es 0. Registra una ENTRADA para añadir existencias
          con trazabilidad.
        </p>
      </section>
      <section>
        <h2>Inventario</h2>
        <div className="formGrid">
          <label>
            Buscar por nombre o SKU
            <input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </label>
          <label>
            Filtrar por categoría
            <select
              value={categoryFilter}
              onChange={(event) => setCategoryFilter(event.target.value)}
            >
              <option value="">Todas</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className="tableWrap">
          <table>
            <thead>
              <tr>
                <th>SKU</th>
                <th>Nombre</th>
                <th>Categoría</th>
                <th>Existencia</th>
                <th>Mínimo</th>
                <th>Costo/u</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((m) => (
                <tr key={m.id}>
                  <td>{m.sku}</td>
                  <td>{m.name}</td>
                  <td>{m.category}</td>
                  <td className={m.stock <= m.minStock ? 'warning' : ''}>
                    {m.stock} {m.unit}
                  </td>
                  <td>{m.minStock}</td>
                  <td>Q {Number(m.unitCost).toFixed(4)}</td>
                  <td>
                    <div className="materialActions">
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => editMaterial(m)}
                      >
                        Editar
                      </button>
                      <button
                        type="button"
                        onClick={() => setHistoryMaterial(m)}
                      >
                        Ver historial
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      {historyMaterial && (
        <MaterialHistoryDialog
          key={historyMaterial.id}
          material={historyMaterial}
          token={token}
          onClose={() => setHistoryMaterial(null)}
        />
      )}
    </>
  );
}
