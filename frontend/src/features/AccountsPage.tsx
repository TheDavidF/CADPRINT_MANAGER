import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import {
  Button,
  ErrorMessage,
  Field,
  PanelHeader,
} from '../components/WorkflowUi';
import { request } from '../services/api';
import { body, message } from '../services/commercial';

type Account = {
  username: string;
  role: 'ADMIN' | 'OPERADOR';
  enabled: boolean;
};

export function AccountsPage({
  token,
  username,
}: {
  token: string;
  username: string;
}) {
  const [accounts, setAccounts] = useState<Account[]>();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    void request<Account[]>(token, '/accounts', {
      signal: controller.signal,
    }).then(
      (items) => {
        if (!controller.signal.aborted) setAccounts(items);
      },
      (failure: unknown) => {
        if (!controller.signal.aborted) setError(message(failure));
      },
    );
    return () => controller.abort();
  }, [token]);

  async function save(path: string, data: unknown, method: string) {
    setBusy(true);
    setError('');
    try {
      const saved = await request<Account>(token, path, body(data, method));
      setAccounts((items) =>
        [
          ...(items ?? []).filter((item) => item.username !== saved.username),
          saved,
        ].sort((left, right) => left.username.localeCompare(right.username)),
      );
      return true;
    } catch (failure) {
      setError(message(failure));
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    if (await save('/accounts', Object.fromEntries(data), 'POST')) form.reset();
  }

  return (
    <section className="panel">
      <PanelHeader title="Cuentas individuales" />
      {error && <ErrorMessage>{error}</ErrorMessage>}
      <form onSubmit={(event) => void create(event)}>
        <fieldset
          className="formGrid commercialFields"
          disabled={busy || !accounts}
        >
          <legend>Crear cuenta</legend>
          <Field label="Nombre de usuario">
            {(attributes) => (
              <input
                {...attributes}
                name="username"
                required
                maxLength={40}
                autoComplete="off"
              />
            )}
          </Field>
          <Field label="Contraseña inicial">
            {(attributes) => (
              <input
                {...attributes}
                name="password"
                type="password"
                required
                minLength={12}
                maxLength={72}
                autoComplete="new-password"
              />
            )}
          </Field>
          <Field label="Rol">
            {(attributes) => (
              <select {...attributes} name="role" defaultValue="OPERADOR">
                <option value="OPERADOR">Operador</option>
                <option value="ADMIN">Administrador</option>
              </select>
            )}
          </Field>
          <Button type="submit" tone="primary">
            Crear cuenta
          </Button>
        </fieldset>
      </form>
      {accounts?.length === 0 && <p>Sin cuentas individuales.</p>}
      {!!accounts?.length && (
        <div className="tableWrap">
          <table>
            <thead>
              <tr>
                <th>Usuario</th>
                <th>Rol</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((account) => (
                <tr key={account.username}>
                  <td>{account.username}</td>
                  <td>{account.role}</td>
                  <td>{account.enabled ? 'Activa' : 'Desactivada'}</td>
                  <td>
                    <Button
                      disabled={busy || account.username === username}
                      aria-label={
                        (account.enabled ? 'Desactivar ' : 'Activar ') +
                        account.username
                      }
                      onClick={() =>
                        void save(
                          '/accounts/' + account.username + '/status',
                          { enabled: !account.enabled },
                          'PUT',
                        )
                      }
                    >
                      {account.enabled ? 'Desactivar' : 'Activar'}
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
