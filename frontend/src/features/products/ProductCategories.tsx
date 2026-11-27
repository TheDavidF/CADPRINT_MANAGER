import { useState } from 'react';
import type { ProductCategory } from './productTypes';

export function ProductCategories({
  categories,
  admin,
  busy,
  onSave,
  onStatus,
}: {
  categories: ProductCategory[];
  admin: boolean;
  busy: boolean;
  onSave: (name: string, category?: ProductCategory) => Promise<boolean>;
  onStatus: (category: ProductCategory) => void;
}) {
  const [name, setName] = useState('');
  const [editing, setEditing] = useState<ProductCategory>();
  function reset() {
    setName('');
    setEditing(undefined);
  }
  return (
    <section>
      <h2>Categorías comerciales</h2>
      {admin && (
        <form
          className="formGrid"
          onSubmit={(event) => {
            event.preventDefault();
            if (name.trim())
              void onSave(name.trim(), editing).then((saved) => {
                if (saved) reset();
              });
          }}
        >
          <label>
            {editing ? 'Nuevo nombre' : 'Nueva categoría'}
            <input
              required
              maxLength={255}
              disabled={busy}
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </label>
          <button type="submit" disabled={busy || !name.trim()}>
            {editing ? 'Guardar nombre' : 'Crear categoría'}
          </button>
          {editing && (
            <button type="button" disabled={busy} onClick={reset}>
              Cancelar edición
            </button>
          )}
        </form>
      )}
      {!categories.length ? (
        <p>Todavía no hay categorías comerciales.</p>
      ) : (
        <div className="tableWrap purchaseTable">
          <table>
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Estado</th>
                {admin && <th>Acciones</th>}
              </tr>
            </thead>
            <tbody>
              {categories.map((category) => (
                <tr key={category.id}>
                  <td>{category.name}</td>
                  <td>{category.active ? 'Activa' : 'Inactiva'}</td>
                  {admin && (
                    <td>
                      <div className="materialActions">
                        <button
                          disabled={busy}
                          onClick={() => {
                            setEditing(category);
                            setName(category.name);
                          }}
                        >
                          Renombrar
                        </button>
                        <button
                          disabled={busy}
                          onClick={() => onStatus(category)}
                        >
                          {category.active ? 'Desactivar' : 'Reactivar'}
                        </button>
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
