import { useState } from 'react';
import type { FormEvent } from 'react';
import { message } from '../../services/commercial';
import { costApi, techniques } from './costApi';
import type { Machine, Profile, Technique } from './costApi';

export function CostSettings({
  token,
  profiles,
  machines,
  refresh,
}: {
  token: string;
  profiles: Profile[];
  machines: Machine[];
  refresh: () => void;
}) {
  const [kind, setKind] = useState<'profile' | 'machine'>('profile');
  const [selected, setSelected] = useState('');
  const current = (kind === 'profile' ? profiles : machines).find(
    (item) => String(item.id) === selected,
  );
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const field = (name: string) => String(form.get(name) ?? '').trim();
    const base = {
      code: field('code'),
      name: field('name'),
      active: form.has('active'),
    };
    setBusy(true);
    setError('');
    try {
      if (kind === 'profile') {
        await costApi.saveProfile(
          token,
          {
            ...base,
            electricityRatePerKwh: field('electricityRatePerKwh'),
            laborRatePerHour: field('laborRatePerHour'),
            indirectRatePerHour: field('indirectRatePerHour'),
          },
          current as Profile | undefined,
        );
      } else {
        await costApi.saveMachine(
          token,
          {
            ...base,
            technique: machine?.technique ?? (field('technique') as Technique),
            nominalPowerWatts: field('nominalPowerWatts') || null,
            machineRatePerHour: field('machineRatePerHour'),
          },
          current as Machine | undefined,
        );
      }
      setSelected('');
      refresh();
      setError('Configuración guardada.');
    } catch (failure) {
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }
  const profile =
    current && 'electricityRatePerKwh' in current ? current : undefined;
  const machine = current && 'technique' in current ? current : undefined;
  return (
    <section className="panel">
      <h2>Perfiles y máquinas</h2>
      <p className="hint">
        La API lista configuraciones activas. Al desactivar un registro dejará
        de aparecer en esta lista.
      </p>
      <div className="formGrid">
        <label>
          Configuración
          <select
            value={kind}
            disabled={busy}
            onChange={(event) => {
              setKind(event.target.value as typeof kind);
              setSelected('');
            }}
          >
            <option value="profile">Perfil</option>
            <option value="machine">Máquina</option>
          </select>
        </label>
        <label>
          Registro
          <select
            value={selected}
            disabled={busy}
            onChange={(event) => setSelected(event.target.value)}
          >
            <option value="">Crear nuevo</option>
            {(kind === 'profile' ? profiles : machines).map((item) => (
              <option key={item.id} value={item.id}>
                {item.code} · {item.name}
              </option>
            ))}
          </select>
        </label>
      </div>
      <form
        onSubmit={(event) => void submit(event)}
        key={kind + ':' + selected + ':' + current?.version}
      >
        <fieldset className="formGrid commercialFields" disabled={busy}>
          <label>
            Código
            <input
              name="code"
              defaultValue={current?.code}
              required
              maxLength={64}
            />
          </label>
          <label>
            Nombre
            <input
              name="name"
              defaultValue={current?.name}
              required
              maxLength={255}
            />
          </label>
          {kind === 'profile' ? (
            <>
              <Rate
                name="electricityRatePerKwh"
                label="Electricidad (Q/kWh)"
                value={profile?.electricityRatePerKwh}
              />
              <Rate
                name="laborRatePerHour"
                label="Trabajo humano (Q/h)"
                value={profile?.laborRatePerHour}
              />
              <Rate
                name="indirectRatePerHour"
                label="Indirectos (Q/h)"
                value={profile?.indirectRatePerHour}
              />
            </>
          ) : (
            <>
              <label>
                Técnica
                <select
                  name="technique"
                  disabled={Boolean(machine)}
                  defaultValue={machine?.technique ?? 'FDM'}
                >
                  {Object.entries(techniques).map(([key, label]) => (
                    <option key={key} value={key}>
                      {label}
                    </option>
                  ))}
                </select>
              </label>
              <Rate
                name="nominalPowerWatts"
                label="Potencia nominal (W)"
                value={machine?.nominalPowerWatts ?? ''}
                optional
              />
              <Rate
                name="machineRatePerHour"
                label="Máquina (Q/h)"
                value={machine?.machineRatePerHour}
              />
            </>
          )}
          <label>
            <span>
              <input
                name="active"
                type="checkbox"
                defaultChecked={current?.active ?? true}
              />{' '}
              Activo
            </span>
          </label>
          <button type="submit">Guardar configuración</button>
        </fieldset>
      </form>
      {error && <p role="status">{error}</p>}
    </section>
  );
}
function Rate({
  name,
  label,
  value = '0',
  optional,
}: {
  name: string;
  label: string;
  value?: string;
  optional?: boolean;
}) {
  return (
    <label>
      {label}
      <input
        name={name}
        defaultValue={value}
        inputMode="decimal"
        pattern="[0-9]+(\.[0-9]+)?"
        required={!optional}
      />
    </label>
  );
}
