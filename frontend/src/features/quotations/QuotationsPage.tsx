import type { Material } from '../../services/types';
import { useEffect, useState } from 'react';
import {
  Button,
  ErrorMessage,
  Field,
  PanelHeader,
  StatusBadge,
  SummaryGrid,
} from '../../components/WorkflowUi';
import { ApiError } from '../../services/api';
import { gtq, message } from '../../services/commercial';
import { quotationApi, statuses } from './quotationApi';
import type {
  Action,
  Detail,
  Draft,
  Event,
  QuotationPage,
  Summary,
} from './quotationApi';
import { QuotationForm } from './QuotationForm';
import { CustomerQuotation, QuotationDetails } from './QuotationDetails';
import { QuotationOrder } from './QuotationOrder';
import { QuotationDocument } from './QuotationDocument';

const actions: Record<Action, string> = {
  emit: 'Emitir revisión',
  revise: 'Crear revisión',
  accept: 'Aceptar emisión',
  reject: 'Rechazar emisión',
  expire: 'Registrar vencimiento',
  cancel: 'Cancelar emisión',
};
const today = () =>
  new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Guatemala',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
const statusLabels: Record<Summary['status'], string> = {
  BORRADOR: 'Borrador',
  EMITIDA: 'Emitida',
  ACEPTADA: 'Aceptada',
  RECHAZADA: 'Rechazada',
  VENCIDA: 'Vencida',
  CANCELADA: 'Cancelada',
};
const emptyFilters = { number: '', recipient: '', status: '' };

export function QuotationsPage({
  token,
  admin,
  materials,
  refreshInventory,
}: {
  token: string;
  admin: boolean;
  materials: Material[];
  refreshInventory: () => Promise<void>;
}) {
  const [filters, setFilters] = useState(emptyFilters);
  const [page, setPage] = useState(0);
  const [list, setList] = useState<QuotationPage>();
  const [listError, setListError] = useState('');
  const [reload, setReload] = useState(0);
  const [selected, setSelected] = useState<{ id: number; revision?: number }>();
  const [record, setRecord] = useState<{
    detail: Detail;
    revisions: Summary[];
    events: Event[];
  }>();
  const [editor, setEditor] = useState<'new' | 'edit'>();
  const [customer, setCustomer] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [fieldError, setFieldError] = useState('');
  const [conflict, setConflict] = useState(false);
  const [server, setServer] = useState<Detail>();
  const [action, setAction] = useState<Action>();
  const [reason, setReason] = useState('');

  useEffect(() => {
    const active = new AbortController();
    const timer = window.setTimeout(
      () =>
        void quotationApi
          .list(token, page, filters, active.signal)
          .then((result) => {
            if (!active.signal.aborted) {
              setList(result);
              setListError('');
            }
          })
          .catch((failure: unknown) => {
            if (!active.signal.aborted) setListError(message(failure));
          }),
      200,
    );
    return () => {
      active.abort();
      clearTimeout(timer);
    };
  }, [token, page, filters, reload]);

  useEffect(() => {
    if (!selected) return;
    const active = new AbortController();
    void Promise.all([
      quotationApi.detail(token, selected.id, selected.revision, active.signal),
      quotationApi.revisions(token, selected.id, active.signal),
      quotationApi.events(token, selected.id, active.signal),
    ])
      .then(([detail, revisions, events]) => {
        if (!active.signal.aborted) setRecord({ detail, revisions, events });
      })
      .catch((failure: unknown) => {
        if (!active.signal.aborted) setError(message(failure));
      });
    return () => active.abort();
  }, [token, selected]);

  function select(id: number, revision?: number) {
    setRecord(undefined);
    setSelected({ id, revision });
    setFieldError('');
    window.scrollTo(0, 0);
    setEditor(undefined);
    setCustomer(false);
    setError('');
    setAction(undefined);
    setConflict(false);
    setServer(undefined);
  }

  function closeEditor() {
    setFieldError('');
    setEditor(undefined);
    setError('');
    setConflict(false);
    setServer(undefined);
  }

  async function save(draft: Draft) {
    setFieldError('');
    setBusy(true);
    setError('');
    try {
      const saved =
        editor === 'edit' && record
          ? await quotationApi.edit(token, record.detail.revision, draft)
          : await quotationApi.create(token, draft);
      select(saved.revision.quotationId, saved.revision.revisionNumber);
      setReload(reload + 1);
    } catch (failure) {
      const stale = failure instanceof ApiError && failure.status === 409;
      setConflict(stale);
      if (failure instanceof ApiError && failure.status === 400)
        setFieldError(failure.message);
      setError(
        stale
          ? 'El servidor rechazó el cambio. Se conserva el formulario. Consulta la versión actual antes de continuar. ' +
              message(failure)
          : message(failure),
      );
    } finally {
      setBusy(false);
    }
  }

  async function loadServer() {
    if (!record) return;
    setBusy(true);
    try {
      const { quotationId, revisionNumber } = record.detail.revision;
      setServer(await quotationApi.detail(token, quotationId, revisionNumber));
    } catch (failure) {
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }

  async function transition() {
    if (!record || !action) return;
    setBusy(true);
    setError('');
    try {
      const result = await quotationApi.action(
        token,
        record.detail.revision,
        action,
        reason.trim(),
      );
      select(result.revision.quotationId, result.revision.revisionNumber);
      setReload(reload + 1);
    } catch (failure) {
      setError(message(failure));
      setConflict(failure instanceof ApiError && failure.status === 409);
    } finally {
      setBusy(false);
    }
  }

  const current = record?.detail.revision;
  const latest = record?.revisions[0];
  const accepted = record?.revisions.find(
    (revision) => revision.status === 'ACEPTADA',
  );
  const closed = Boolean(accepted);
  const issued = record?.revisions.find(
    (revision) => revision.issuedAt && !revision.replacedAt,
  );
  const expired = Boolean(issued?.validUntil && issued.validUntil < today());
  const active = issued?.status === 'EMITIDA' && !expired ? issued : undefined;
  const allowed: Action[] = [];
  if (current?.status === 'BORRADOR' && !closed) allowed.push('emit');
  if (
    current?.issuedAt &&
    !closed &&
    !record?.revisions.some((revision) => revision.status === 'BORRADOR')
  )
    allowed.push('revise');
  if (current && active?.revisionNumber === current.revisionNumber) {
    allowed.push('accept', 'reject');
    if (admin) allowed.push('cancel');
  }
  if (
    current &&
    issued?.revisionNumber === current.revisionNumber &&
    issued.status === 'EMITIDA' &&
    expired
  )
    allowed.push('expire');

  return (
    <div className="quotationWorkspace">
      {(selected || editor) && (
        <Button
          tone="quiet"
          disabled={busy}
          onClick={() => {
            if (editor) closeEditor();
            else {
              setSelected(undefined);
              setRecord(undefined);
              setError('');
              setConflict(false);
              setServer(undefined);
            }
            window.scrollTo(0, 0);
          }}
        >
          {editor ? 'Volver sin guardar' : 'Volver a cotizaciones'}
        </Button>
      )}
      {!selected && !editor && (
        <section className="panel quotePanel">
          <PanelHeader title="Listado de cotizaciones">
            <Button tone="primary" onClick={() => setEditor('new')}>
              Nueva cotización
            </Button>
          </PanelHeader>
          <div className="formGrid">
            <label>
              Número comercial
              <input
                value={filters.number}
                onChange={(event) => {
                  setFilters({ ...filters, number: event.target.value });
                  setPage(0);
                }}
              />
            </label>
            <label>
              Destinatario
              <input
                value={filters.recipient}
                onChange={(event) => {
                  setFilters({ ...filters, recipient: event.target.value });
                  setPage(0);
                }}
              />
            </label>
            <label>
              Estado de la revisión más reciente
              <select
                value={filters.status}
                onChange={(event) => {
                  setFilters({ ...filters, status: event.target.value });
                  setPage(0);
                }}
              >
                <option value="">Todos</option>
                {statuses.map((state) => (
                  <option key={state}>{state}</option>
                ))}
              </select>
            </label>
          </div>
          {listError && (
            <p role="alert">
              {listError}{' '}
              <button onClick={() => setReload(reload + 1)}>Reintentar</button>
            </p>
          )}
          {!list && !listError && <p role="status">Cargando cotizaciones…</p>}
          {list?.items.length === 0 && (
            <p>No hay cotizaciones para estos filtros.</p>
          )}
          {!!list?.items.length && (
            <div className="tableWrap quoteList">
              <table>
                <thead>
                  <tr>
                    <th>Número</th>
                    <th>Destinatario</th>
                    <th>Última revisión</th>
                    <th>Total</th>
                    <th>Detalle</th>
                  </tr>
                </thead>
                <tbody>
                  {list.items.map((item) => (
                    <tr key={item.quotationId}>
                      <td>
                        {item.quotationNumber ??
                          'Borrador #' + item.quotationId}
                      </td>
                      <td className="quoteListRecipient">
                        {item.recipientName ?? '—'}
                      </td>
                      <td>
                        {item.revisionNumber} · {statusLabels[item.status]}
                      </td>
                      <td>{gtq(item.totalAmount)}</td>
                      <td>
                        <Button
                          aria-label={
                            'Abrir cotización de ' +
                            (item.recipientName ?? 'destinatario pendiente')
                          }
                          disabled={busy || Boolean(editor)}
                          onClick={() => select(item.quotationId)}
                        >
                          Abrir
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {list && (
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
          )}
        </section>
      )}
      {error && !fieldError && <ErrorMessage>{error}</ErrorMessage>}
      {conflict && current && (
        <div className="commercialActions">
          <button disabled={busy} onClick={() => void loadServer()}>
            Consultar versión actual
          </button>
          {!editor && (
            <button
              disabled={busy}
              onClick={() =>
                select(current.quotationId, current.revisionNumber)
              }
            >
              Recargar revisión
            </button>
          )}
        </div>
      )}
      {server && (
        <section className="panel">
          <h2>Versión guardada en el servidor</h2>
          <p>
            Versión {server.revision.version} · {server.revision.status} ·{' '}
            {server.recipient.name ?? 'Sin destinatario'} ·{' '}
            {gtq(server.totals.totalAmount)}
          </p>
          {editor && current && (
            <button
              disabled={busy}
              onClick={() => {
                if (
                  window.confirm(
                    '¿Descartar los cambios locales y recargar la revisión guardada?',
                  )
                )
                  select(current.quotationId, current.revisionNumber);
              }}
            >
              Descartar cambios locales y recargar
            </button>
          )}
          <QuotationDetails detail={server} />
        </section>
      )}
      {editor && (
        <QuotationForm
          key={
            editor + ':' + current?.quotationId + ':' + current?.revisionNumber
          }
          token={token}
          initial={editor === 'edit' ? record?.detail : undefined}
          busy={busy}
          serverError={fieldError}
          clearServerError={() => setFieldError('')}
          onSave={(draft) => void save(draft)}
          onClose={closeEditor}
        />
      )}
      {!editor && selected && !record && !error && (
        <p role="status">Cargando revisión…</p>
      )}
      {!editor && current && record && (
        <>
          <section className="panel quotePanel quoteOverview">
            <PanelHeader
              title={current.recipientName ?? 'Destinatario pendiente'}
              meta={
                <>
                  <span>
                    {current.quotationNumber ?? 'Número pendiente de emisión'}
                  </span>
                  <span>Revisión {current.revisionNumber}</span>
                  <StatusBadge
                    tone={
                      current.status === 'EMITIDA' ||
                      current.status === 'ACEPTADA'
                        ? 'positive'
                        : current.status === 'CANCELADA'
                          ? 'danger'
                          : 'neutral'
                    }
                  >
                    {current.replacedAt
                      ? 'Sustituida'
                      : statusLabels[current.status]}
                  </StatusBadge>
                </>
              }
            >
              {current.status === 'BORRADOR' && !closed && (
                <>
                  <Button
                    tone="primary"
                    disabled={busy || conflict}
                    onClick={() => {
                      setError('');
                      setEditor('edit');
                      window.scrollTo(0, 0);
                    }}
                  >
                    Editar borrador
                  </Button>
                  <Button
                    disabled={busy || conflict}
                    onClick={() => {
                      setAction('emit');
                      setReason('');
                    }}
                  >
                    Emitir revisión
                  </Button>
                </>
              )}
              {current.issuedAt && (
                <QuotationDocument
                  key={current.quotationId + ':' + current.revisionNumber}
                  token={token}
                  current={current}
                />
              )}
            </PanelHeader>
            <SummaryGrid
              items={[
                {
                  label: 'Revisión seleccionada',
                  value: 'Revisión ' + current.revisionNumber,
                },
                {
                  label: 'Emisión vigente',
                  value: active ? (
                    <Button
                      tone="quiet"
                      disabled={
                        busy || active.revisionNumber === current.revisionNumber
                      }
                      onClick={() =>
                        select(current.quotationId, active.revisionNumber)
                      }
                    >
                      Revisión {active.revisionNumber}
                    </Button>
                  ) : (
                    'Ninguna'
                  ),
                },
                {
                  label: 'Borrador',
                  value: record.revisions.some(
                    (revision) => revision.status === 'BORRADOR',
                  )
                    ? 'Revisión ' +
                      record.revisions.find(
                        (revision) => revision.status === 'BORRADOR',
                      )?.revisionNumber
                    : 'Ninguno',
                },
                {
                  label: 'Más reciente',
                  value: latest
                    ? 'Revisión ' +
                      latest.revisionNumber +
                      ' · ' +
                      statusLabels[latest.status]
                    : '—',
                },
              ]}
            />
            {accepted && (
              <QuotationOrder
                key={accepted.quotationId + ':' + accepted.revisionNumber}
                token={token}
                current={accepted}
                materials={materials}
                refreshInventory={refreshInventory}
              />
            )}
            {closed && (
              <p>
                Ciclo cerrado por aceptación. Los borradores se conservan en el
                historial.
              </p>
            )}
            <div className="quoteRevisionTools">
              <Field label="Cambiar de revisión">
                {(accessibility) => (
                  <select
                    {...accessibility}
                    value={current.revisionNumber}
                    disabled={busy}
                    onChange={(event) =>
                      select(current.quotationId, Number(event.target.value))
                    }
                  >
                    {record.revisions.map((item) => (
                      <option
                        key={item.revisionNumber}
                        value={item.revisionNumber}
                      >
                        Revisión {item.revisionNumber} ·{' '}
                        {item.replacedAt
                          ? 'Sustituida'
                          : statusLabels[item.status]}
                      </option>
                    ))}
                  </select>
                )}
              </Field>
              <div className="workflowActions">
                {allowed.includes('revise') && (
                  <Button
                    disabled={busy || conflict}
                    onClick={() => {
                      setAction('revise');
                      setReason('');
                    }}
                  >
                    Crear revisión
                  </Button>
                )}
                {current.issuedAt && (
                  <Button
                    tone="quiet"
                    disabled={busy}
                    onClick={() => setCustomer(!customer)}
                  >
                    {customer
                      ? 'Volver al detalle interno'
                      : 'Ver vista comercial'}
                  </Button>
                )}
              </div>
            </div>
            {allowed.some(
              (entry) => entry !== 'emit' && entry !== 'revise',
            ) && (
              <details className="quoteMore">
                <summary>Otras acciones de la revisión</summary>
                <div className="workflowActions">
                  {allowed
                    .filter((entry) => entry !== 'emit' && entry !== 'revise')
                    .map((entry) => (
                      <Button
                        tone={entry === 'cancel' ? 'danger' : 'secondary'}
                        key={entry}
                        disabled={busy || conflict}
                        onClick={() => {
                          setAction(entry);
                          setReason('');
                        }}
                      >
                        {actions[entry]}
                      </Button>
                    ))}
                </div>
              </details>
            )}
            {action && (
              <form
                className="formGrid"
                onSubmit={(event) => {
                  event.preventDefault();
                  void transition();
                }}
              >
                <p>
                  ¿Confirmar «{actions[action]}» en la revisión{' '}
                  {current.revisionNumber}?
                </p>
                {(action === 'cancel' ||
                  action === 'reject' ||
                  action === 'accept') && (
                  <label>
                    Motivo{action === 'cancel' ? ' obligatorio' : ' (opcional)'}
                    <input
                      value={reason}
                      maxLength={1000}
                      required={action === 'cancel'}
                      onChange={(event) => setReason(event.target.value)}
                    />
                  </label>
                )}
                <Button
                  tone={action === 'cancel' ? 'danger' : 'primary'}
                  type="submit"
                  disabled={
                    busy || conflict || (action === 'cancel' && !reason.trim())
                  }
                >
                  Confirmar acción
                </Button>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => setAction(undefined)}
                >
                  Cerrar acción
                </button>
              </form>
            )}
          </section>
          {customer ? (
            <CustomerQuotation token={token} current={current} />
          ) : (
            <QuotationDetails detail={record.detail} />
          )}
          {!customer && (
            <section className="panel">
              <h2>Historial de eventos</h2>
              {record.events.length ? (
                <div className="tableWrap">
                  <table>
                    <thead>
                      <tr>
                        <th>Revisión</th>
                        <th>Evento</th>
                        <th>Estados</th>
                        <th>Usuario</th>
                        <th>Fecha</th>
                        <th>Motivo</th>
                      </tr>
                    </thead>
                    <tbody>
                      {record.events.map((event, index) => (
                        <tr key={index}>
                          <td>{event.revisionNumber}</td>
                          <td>{event.type}</td>
                          <td>
                            {event.previousStatus ?? '—'} →{' '}
                            {event.newStatus ?? '—'}
                          </td>
                          <td>{event.actor}</td>
                          <td>
                            {new Date(event.occurredAt).toLocaleString('es-GT')}
                          </td>
                          <td>{event.reason ?? event.details ?? '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p>Sin eventos registrados.</p>
              )}
            </section>
          )}
        </>
      )}
    </div>
  );
}
