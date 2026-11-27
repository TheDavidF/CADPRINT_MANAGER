import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import {
  Button,
  ErrorMessage,
  Field,
  PanelHeader,
} from '../../components/WorkflowUi';
import { message } from '../../services/commercial';
import { customerApi, customerFields } from './customerApi';
import type { Customer } from './customerApi';

export function CustomersPage({ token }: { token: string }) {
  const [customers, setCustomers] = useState<Customer[]>();
  const [editing, setEditing] = useState<Customer | null>();
  const [search, setSearch] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    const active = new AbortController();
    void customerApi.list(token, active.signal).then(
      (items) => {
        if (!active.signal.aborted) setCustomers(items);
      },
      (failure: unknown) => {
        if (!active.signal.aborted) setError(message(failure));
      },
    );
    return () => active.abort();
  }, [token, reload]);

  function open(customer: Customer | null | undefined) {
    setEditing(customer);
    setError('');
    setNotice('');
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    const value = (name: string) => String(fields.get(name) ?? '').trim();
    setBusy(true);
    setError('');
    try {
      await customerApi.save(
        token,
        {
          name: value('name'),
          contact: value('contact') || null,
          email: value('email') || null,
          phone: value('phone') || null,
          address: value('address') || null,
        },
        editing?.id,
      );
      setEditing(undefined);
      setNotice('Cliente guardado');
      setReload((previous) => previous + 1);
    } catch (failure) {
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }

  const query = search.trim().toLocaleLowerCase('es-GT');
  const matches = customers?.filter((customer) =>
    [customer.name, customer.contact, customer.email, customer.phone].some(
      (value) => value?.toLocaleLowerCase('es-GT').includes(query),
    ),
  );
  return (
    <section className="panel">
      <PanelHeader title="Registro de clientes">
        <Button tone="primary" disabled={busy} onClick={() => open(null)}>
          Nuevo cliente
        </Button>
      </PanelHeader>
      {error && <ErrorMessage>{error}</ErrorMessage>}
      {notice && <p role="status">{notice}</p>}
      {editing !== undefined && (
        <form key={editing?.id ?? 'new'} onSubmit={(event) => void save(event)}>
          <fieldset className="formGrid commercialFields" disabled={busy}>
            <legend>{editing ? 'Editar cliente' : 'Nuevo cliente'}</legend>
            {customerFields.map(([name, label, maxLength]) => (
              <Field key={name} label={label}>
                {(accessibility) => (
                  <input
                    {...accessibility}
                    name={name}
                    type={name === 'email' ? 'email' : 'text'}
                    maxLength={maxLength}
                    required={name === 'name'}
                    defaultValue={editing?.[name] ?? ''}
                  />
                )}
              </Field>
            ))}
            <Button type="submit" tone="primary">
              Guardar cliente
            </Button>
            <Button onClick={() => open(undefined)}>Cancelar</Button>
          </fieldset>
        </form>
      )}
      <Field label="Buscar cliente">
        {(accessibility) => (
          <input
            {...accessibility}
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        )}
      </Field>
      {!matches && !error && <p role="status">Cargando clientes…</p>}
      {!matches && error && (
        <Button
          onClick={() => {
            setError('');
            setReload((previous) => previous + 1);
          }}
        >
          Reintentar
        </Button>
      )}
      {matches?.length === 0 && <p>No se encontraron clientes.</p>}
      {!!matches?.length && (
        <div className="tableWrap">
          <table>
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Contacto</th>
                <th>Correo</th>
                <th>Teléfono</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {matches.map((customer) => (
                <tr key={customer.id}>
                  <td>{customer.name}</td>
                  <td>{customer.contact ?? '—'}</td>
                  <td>{customer.email ?? '—'}</td>
                  <td>{customer.phone ?? '—'}</td>
                  <td>
                    <Button
                      disabled={busy}
                      onClick={() => open(customer)}
                      aria-label={'Editar ' + customer.name}
                    >
                      Editar
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
