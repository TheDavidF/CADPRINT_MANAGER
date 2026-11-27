import { useState } from 'react';
import type { SubmitForm } from '../services/form';
import type { Category } from '../services/types';

export function CategoriesPanel({
  categories,
  busy,
  submit,
}: {
  categories: Category[];
  busy: boolean;
  submit: SubmitForm;
}) {
  const [name, setName] = useState('');
  const [editing, setEditing] = useState<Category | null>(null);

  function reset() {
    setEditing(null);
    setName('');
  }

  return (
    <section>
      <h2>Categorías</h2>
      <form
        className="formGrid"
        onSubmit={(event) => {
          event.preventDefault();
          void submit(
            editing ? '/categories/' + editing.id : '/categories',
            { name: name.trim() },
            reset,
            editing ? 'PUT' : 'POST',
          );
        }}
      >
        <label>
          {editing ? 'Nuevo nombre' : 'Nueva categoría'}
          <input
            required
            maxLength={255}
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        </label>
        <button disabled={busy || !name.trim()}>
          {editing ? 'Guardar nombre' : 'Crear categoría'}
        </button>
        {editing && (
          <button type="button" disabled={busy} onClick={reset}>
            Cancelar
          </button>
        )}
      </form>
      <div className="tableWrap">
        <table>
          <thead>
            <tr>
              <th>Categoría</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {categories.map((category) => (
              <tr key={category.id}>
                <td>{category.name}</td>
                <td>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => {
                      setEditing(category);
                      setName(category.name);
                    }}
                  >
                    Renombrar
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
