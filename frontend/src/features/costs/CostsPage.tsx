import { useEffect, useState } from 'react';
import type { Material } from '../../services/types';
import { gtq, message } from '../../services/commercial';
import { costApi, techniques } from './costApi';
import type {
  HistoryDetail,
  HistoryItem,
  Machine,
  Profile,
  Technique,
} from './costApi';
import { CostCalculator } from './CostCalculator';
import { CostResult } from './CostResult';
import { CostSettings } from './CostSettings';

export function CostsPage({
  token,
  materials,
  admin,
}: {
  token: string;
  materials: Material[];
  admin: boolean;
}) {
  const [tab, setTab] = useState<Technique | 'history' | 'settings'>('FDM');
  const [sources, setSources] = useState<{
    profiles: Profile[];
    machines: Machine[];
  } | null>(null);
  const [reload, setReload] = useState(0);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState<HistoryDetail | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    void Promise.all([
      costApi.profiles(token, controller.signal),
      costApi.machines(token, 'FDM', controller.signal),
      costApi.machines(token, 'SUBLIMATION', controller.signal),
    ])
      .then(([profiles, fdm, sublimation]) => {
        if (!controller.signal.aborted) {
          setSources({ profiles, machines: [...fdm, ...sublimation] });
          setError('');
        }
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) setError(message(failure));
      });
    return () => controller.abort();
  }, [token, reload]);
  return (
    <>
      <div className="commercialActions">
        {Object.entries(techniques).map(([key, label]) => (
          <button
            key={key}
            aria-pressed={tab === key}
            onClick={() => setTab(key as Technique)}
          >
            {label}
          </button>
        ))}
        <button
          aria-pressed={tab === 'history'}
          onClick={() => setTab('history')}
        >
          Historial de cálculos
        </button>
        {admin && (
          <button
            aria-pressed={tab === 'settings'}
            onClick={() => setTab('settings')}
          >
            Perfiles y máquinas
          </button>
        )}
      </div>
      {error && (
        <p role="alert">
          {error}{' '}
          <button onClick={() => setReload(reload + 1)}>Reintentar</button>
        </p>
      )}
      {!sources && !error && tab !== 'history' && (
        <p role="status">Cargando configuración…</p>
      )}
      {sources && (tab === 'FDM' || tab === 'SUBLIMATION') && (
        <CostCalculator
          key={tab}
          token={token}
          technique={tab}
          materials={materials}
          {...sources}
          onSaved={(result) => {
            setSaved(result);
            setTab('history');
          }}
        />
      )}
      {tab === 'history' && <CostHistory token={token} initial={saved} />}
      {tab === 'settings' && sources && admin && (
        <CostSettings
          token={token}
          {...sources}
          refresh={() => setReload(reload + 1)}
        />
      )}
    </>
  );
}

function CostHistory({
  token,
  initial,
}: {
  token: string;
  initial: HistoryDetail | null;
}) {
  const [filter, setFilter] = useState<Technique | ''>('');
  const [page, setPage] = useState(0);
  const [list, setList] = useState<{
    content: HistoryItem[];
    totalPages: number;
  } | null>(null);
  const [detail, setDetail] = useState(initial);
  const [selected, setSelected] = useState<number>();
  const [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    void costApi
      .list(token, page, filter || undefined, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setList(result);
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) setError(message(failure));
      });
    return () => controller.abort();
  }, [token, page, filter]);
  useEffect(() => {
    if (selected === undefined) return;
    const controller = new AbortController();
    void costApi
      .detail(token, selected, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setDetail(result);
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) setError(message(failure));
      });
    return () => controller.abort();
  }, [token, selected]);
  return (
    <>
      <section className="panel">
        <h2>Historial de cálculos</h2>
        <div className="formGrid">
          <label>
            Técnica
            <select
              value={filter}
              onChange={(event) => {
                setFilter(event.target.value as typeof filter);
                setPage(0);
              }}
            >
              <option value="">Todas</option>
              {Object.entries(techniques).map(([key, label]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>
          </label>
        </div>
        {error && <p role="alert">{error}</p>}
        {!list && !error && <p role="status">Cargando historial…</p>}
        {list?.content.length === 0 && (
          <p>Todavía no hay cálculos en esta consulta.</p>
        )}
        {list && (
          <>
            <ul>
              {list.content.map((item) => (
                <li key={item.id}>
                  <button onClick={() => setSelected(item.id)}>
                    {item.jobDescription} · {techniques[item.technique]} ·{' '}
                    {gtq(item.totalCost)} ·{' '}
                    {new Date(item.createdAt).toLocaleString('es-GT')}
                  </button>
                </li>
              ))}
            </ul>
            <div className="commercialActions">
              <button disabled={page === 0} onClick={() => setPage(page - 1)}>
                Anterior
              </button>
              <span>
                Página {page + 1} de {Math.max(1, list.totalPages)}
              </span>
              <button
                disabled={page + 1 >= list.totalPages}
                onClick={() => setPage(page + 1)}
              >
                Siguiente
              </button>
            </div>
          </>
        )}
      </section>
      {detail && <CostResult result={detail} />}
    </>
  );
}
