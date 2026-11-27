import { useState } from 'react';
import { Plus } from 'lucide-react';
import type { Supplier } from '../services/types';
import type { SubmitForm } from '../services/form';
export function SuppliersPage({
  suppliers,
  busy,
  submit,
}: {
  suppliers: Supplier[];
  busy: boolean;
  submit: SubmitForm;
}) {
  const [supplierForm, setSupplierForm] = useState({
    name: '',
    phone: '',
    email: '',
    notes: '',
  });
  return (
    <>
      <section>
        <h2>
          <Plus size={18} /> Nuevo proveedor
        </h2>
        <form
          className="formGrid"
          onSubmit={(e) => {
            e.preventDefault();
            void submit('/suppliers', supplierForm, () =>
              setSupplierForm({ name: '', phone: '', email: '', notes: '' }),
            );
          }}
        >
          <label>
            Nombre
            <input
              required
              value={supplierForm.name}
              onChange={(e) =>
                setSupplierForm({ ...supplierForm, name: e.target.value })
              }
            />
          </label>
          <label>
            Teléfono
            <input
              value={supplierForm.phone}
              onChange={(e) =>
                setSupplierForm({ ...supplierForm, phone: e.target.value })
              }
            />
          </label>
          <label>
            Correo
            <input
              type="email"
              value={supplierForm.email}
              onChange={(e) =>
                setSupplierForm({ ...supplierForm, email: e.target.value })
              }
            />
          </label>
          <label>
            Notas
            <input
              value={supplierForm.notes}
              onChange={(e) =>
                setSupplierForm({ ...supplierForm, notes: e.target.value })
              }
            />
          </label>
          <button disabled={busy}>Guardar proveedor</button>
        </form>
      </section>
      <section>
        <h2>Proveedores registrados</h2>
        <div className="tableWrap">
          <table>
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Teléfono</th>
                <th>Correo</th>
                <th>Notas</th>
              </tr>
            </thead>
            <tbody>
              {suppliers.map((s) => (
                <tr key={s.id}>
                  <td>{s.name}</td>
                  <td>{s.phone}</td>
                  <td>{s.email}</td>
                  <td>{s.notes}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
