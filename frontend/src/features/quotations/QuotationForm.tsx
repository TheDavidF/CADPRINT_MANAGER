import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import {
  Button,
  ErrorMessage,
  Field,
  PanelHeader,
} from '../../components/WorkflowUi';
import { gtq, message } from '../../services/commercial';
import { CatalogPicker } from '../products/CatalogPicker';
import { costApi, techniques } from '../costs/costApi';
import type { HistoryDetail, HistoryItem } from '../costs/costApi';
import { customerApi, customerFields } from '../customers/customerApi';
import type { Customer } from '../customers/customerApi';
import type {
  Detail,
  Discount,
  Draft,
  LineInput,
  Recipient,
} from './quotationApi';

type Row = {
  key: number;
  type: LineInput['type'];
  productId: string;
  variantId: string;
  calculationId: string;
  name: string;
  description: string;
  quantity: string;
  unitPrice: string;
  discount: Discount;
};
const noDiscount: Discount = { type: 'NONE', value: null };
const blank = (key: number): Row => ({
  key,
  type: 'CATALOG',
  productId: '',
  variantId: '',
  calculationId: '',
  name: '',
  description: '',
  quantity: '1',
  unitPrice: '',
  discount: noDiscount,
});
export function QuotationForm({
  token,
  initial,
  busy,
  serverError,
  clearServerError,
  onSave,
  onClose,
}: {
  token: string;
  initial?: Detail;
  busy: boolean;
  serverError: string;
  clearServerError: () => void;
  onSave: (draft: Draft) => void;
  onClose: () => void;
}) {
  const form = useRef<HTMLFormElement>(null);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [customerError, setCustomerError] = useState('');
  const [customerId, setCustomerId] = useState(initial?.customerId ?? null);
  const [recipient, setRecipient] = useState<Partial<Recipient>>(
    initial?.recipient ?? {},
  );
  useEffect(() => {
    const active = new AbortController();
    void customerApi.list(token, active.signal).then(
      (items) => {
        if (!active.signal.aborted) setCustomers(items);
      },
      (failure: unknown) => {
        if (!active.signal.aborted) setCustomerError(message(failure));
      },
    );
    return () => active.abort();
  }, [token]);
  useEffect(() => {
    if (serverError) {
      form.current
        ?.querySelector('[aria-invalid="true"], [role="alert"]')
        ?.scrollIntoView({ block: 'center' });
    }
  }, [serverError]);
  const next = useRef((initial?.lines.length ?? 0) + 1);
  const [rows, setRows] = useState<Row[]>(
    () =>
      initial?.lines.map((line, index) => ({
        key: index,
        type: line.type,
        productId: String(line.productId ?? ''),
        variantId: String(line.variantId ?? ''),
        calculationId: String(line.productionCalculationId ?? ''),
        name: line.commercial.name,
        description: line.commercial.description ?? '',
        quantity: String(line.commercial.quantity),
        unitPrice: line.commercial.unitPrice,
        discount: line.commercial.discount,
      })) ?? [blank(0)],
  );
  const [generalDiscount, setDiscount] = useState(
    initial?.totals.generalDiscount ?? noDiscount,
  );
  function edit(key: number, patch: Partial<Row>) {
    setRows((previous) =>
      previous.map((row) => (row.key === key ? { ...row, ...patch } : row)),
    );
  }
  function move(index: number, offset: number) {
    const reordered = [...rows];
    [reordered[index], reordered[index + offset]] = [
      reordered[index + offset],
      reordered[index],
    ];
    setRows(reordered);
  }
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const get = (name: string) => String(data.get(name) ?? '').trim();
    onSave({
      customerId,
      recipient: {
        name: get('name') || null,
        contact: get('contact') || null,
        email: get('email') || null,
        phone: get('phone') || null,
        address: get('address') || null,
      },
      commercialTerms: get('commercialTerms'),
      notes: get('notes'),
      validUntil: get('validUntil') || null,
      generalDiscount,
      shippingAmount: get('shippingAmount') || null,
      lines: rows.map((row) => ({
        type: row.type,
        productId: row.productId ? Number(row.productId) : null,
        variantId: row.variantId ? Number(row.variantId) : null,
        productionCalculationId: row.calculationId
          ? Number(row.calculationId)
          : null,
        name: row.name || null,
        description: row.description || null,
        quantity: row.quantity,
        unitPrice: row.unitPrice || null,
        discount: row.discount,
      })),
    });
  }
  const fieldPath = serverError.split(':')[0].replace(/^draft\./, '');
  const errorFor = (path: string) =>
    fieldPath === path || fieldPath.startsWith(path + '.')
      ? serverError
      : undefined;
  const numbered = serverError.match(/(?:línea) (\d+)$/i);
  const priceError =
    numbered && /precio/i.test(serverError) ? Number(numbered[1]) - 1 : -1;
  const calculationError =
    numbered && /cálculo/i.test(serverError) ? Number(numbered[1]) - 1 : -1;
  const discountError =
    /descuento/i.test(serverError) &&
    !fieldPath.includes('[') &&
    !fieldPath.includes('.') &&
    !errorFor('generalDiscount');
  const mapped =
    /^(recipient\.(?:name|contact|email|phone|address)|lines\[\d+\]\.|generalDiscount|shippingAmount|validUntil|commercialTerms|notes)/.test(
      fieldPath,
    ) ||
    priceError >= 0 ||
    calculationError >= 0;
  return (
    <section className="panel quotePanel quoteEditor">
      <form ref={form} onSubmit={submit} onInput={clearServerError}>
        <div className="quoteSaveBar">
          <PanelHeader
            title={initial ? 'Editar borrador' : 'Nueva cotización'}
            meta={
              initial && (
                <span>
                  Revisión {initial.revision.revisionNumber} ·{' '}
                  {initial.recipient.name ?? 'Destinatario pendiente'}
                </span>
              )
            }
          >
            <Button tone="primary" type="submit" disabled={busy}>
              {busy ? 'Guardando…' : 'Guardar borrador'}
            </Button>
          </PanelHeader>
        </div>
        {serverError && !mapped && !discountError && (
          <ErrorMessage>{serverError}</ErrorMessage>
        )}
        <fieldset disabled={busy} className="formGrid commercialFields">
          <legend>Destinatario y condiciones</legend>
          <Field label="Cliente registrado" error={customerError}>
            {(accessibility) => (
              <select
                {...accessibility}
                value={customerId ?? ''}
                onChange={(event) => {
                  const customer = customers.find(
                    (item) => String(item.id) === event.target.value,
                  );
                  setCustomerId(customer?.id ?? null);
                  if (customer) setRecipient(customer);
                }}
              >
                <option value="">Sin vincular</option>
                {customerId &&
                  !customers.some((item) => item.id === customerId) && (
                    <option value={customerId}>
                      {initial?.recipient.name ?? 'Cliente vinculado'}
                    </option>
                  )}
                {customers.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                    {item.phone ? ' · ' + item.phone : ''}
                  </option>
                ))}
              </select>
            )}
          </Field>
          {customerFields.map(([name, label, max]) => (
            <Field
              key={name}
              label={label}
              error={errorFor('recipient.' + name)}
            >
              {(accessibility) => (
                <input
                  {...accessibility}
                  type={name === 'email' ? 'email' : 'text'}
                  name={name}
                  maxLength={max}
                  value={recipient[name] ?? ''}
                  onChange={(event) =>
                    setRecipient({ ...recipient, [name]: event.target.value })
                  }
                />
              )}
            </Field>
          ))}
          <Field label="Último día válido" error={errorFor('validUntil')}>
            {(accessibility) => (
              <input
                {...accessibility}
                name="validUntil"
                type="date"
                defaultValue={initial?.revision.validUntil ?? ''}
              />
            )}
          </Field>
          <Field
            label="Condiciones comerciales"
            error={errorFor('commercialTerms')}
          >
            {(accessibility) => (
              <textarea
                {...accessibility}
                name="commercialTerms"
                maxLength={4000}
                defaultValue={initial?.commercialTerms ?? ''}
              />
            )}
          </Field>
          <Field label="Notas internas" error={errorFor('notes')}>
            {(accessibility) => (
              <textarea
                {...accessibility}
                name="notes"
                maxLength={4000}
                defaultValue={initial?.notes ?? ''}
              />
            )}
          </Field>
        </fieldset>
        <fieldset
          disabled={busy}
          className="formGrid commercialFields quoteCharges"
        >
          <legend>Envío y descuento general</legend>
          <Field label="Envío en GTQ" error={errorFor('shippingAmount')}>
            {(accessibility) => (
              <input
                {...accessibility}
                name="shippingAmount"
                inputMode="decimal"
                pattern="[0-9]+(\.[0-9]{1,2})?"
                defaultValue={initial?.totals.shippingAmount ?? ''}
              />
            )}
          </Field>
          <DiscountFields
            label="Descuento general"
            error={errorFor('generalDiscount')}
            value={generalDiscount}
            onChange={setDiscount}
          />
        </fieldset>
        <div className="quoteLinesHeading">
          <h3>
            Artículos <span>({rows.length})</span>
          </h3>
          <Button
            disabled={busy || rows.length >= 200}
            onClick={() => setRows([...rows, blank(next.current++)])}
          >
            Agregar artículo
          </Button>
        </div>
        {discountError && (
          <ErrorMessage>
            {serverError} Revisa los descuentos fijos de los artículos y el
            descuento general.
          </ErrorMessage>
        )}
        {rows.map((row, index) => (
          <fieldset
            disabled={busy}
            key={row.key}
            className="formGrid commercialFields quoteLineCard"
          >
            <legend>
              Artículo {index + 1}
              {row.name && ' · ' + row.name}
            </legend>
            <label>
              Tipo de artículo
              <select
                value={row.type}
                onChange={(event) =>
                  edit(row.key, {
                    type: event.target.value as Row['type'],
                    productId: '',
                    variantId: '',
                    calculationId: '',
                    name: '',
                    description: '',
                  })
                }
              >
                <option value="CATALOG">Catálogo</option>
                <option value="CUSTOM">Personalizado</option>
              </select>
            </label>
            {row.type === 'CATALOG' ? (
              <CatalogPicker
                token={token}
                value={row}
                onChange={(change) =>
                  edit(row.key, { ...change, calculationId: '' })
                }
              />
            ) : (
              <>
                <label>
                  Nombre del artículo
                  <input
                    maxLength={255}
                    value={row.name}
                    onChange={(event) =>
                      edit(row.key, { name: event.target.value })
                    }
                  />
                </label>
                <Field
                  label="Descripción del artículo"
                  error={errorFor('lines[' + index + '].description')}
                >
                  {(accessibility) => (
                    <textarea
                      {...accessibility}
                      maxLength={4000}
                      required
                      value={row.description}
                      onChange={(event) =>
                        edit(row.key, { description: event.target.value })
                      }
                    />
                  )}
                </Field>
              </>
            )}
            <Field
              label="Cantidad cotizada"
              error={errorFor('lines[' + index + '].quantity')}
            >
              {(accessibility) => (
                <input
                  {...accessibility}
                  type="number"
                  min={1}
                  max={2147483647}
                  step={1}
                  required
                  value={row.quantity}
                  onChange={(event) =>
                    edit(row.key, { quantity: event.target.value })
                  }
                />
              )}
            </Field>
            <Field
              label="Precio unitario en GTQ"
              error={
                priceError === index
                  ? serverError
                  : errorFor('lines[' + index + '].unitPrice')
              }
            >
              {(accessibility) => (
                <input
                  {...accessibility}
                  inputMode="decimal"
                  pattern="[0-9]+(\.[0-9]{1,2})?"
                  required={row.type === 'CUSTOM'}
                  placeholder={
                    row.type === 'CATALOG'
                      ? 'Usar precio del catálogo'
                      : undefined
                  }
                  value={row.unitPrice}
                  onChange={(event) =>
                    edit(row.key, { unitPrice: event.target.value })
                  }
                />
              )}
            </Field>
            <DiscountFields
              label="Descuento de línea"
              error={errorFor('lines[' + index + '].discount')}
              value={row.discount}
              onChange={(discount) => edit(row.key, { discount })}
            />
            {calculationError === index && (
              <ErrorMessage>{serverError}</ErrorMessage>
            )}
            {fieldPath.startsWith('lines[' + index + '].') &&
              !['description', 'quantity', 'unitPrice', 'discount'].some(
                (key) => Boolean(errorFor('lines[' + index + '].' + key)),
              ) && <ErrorMessage>{serverError}</ErrorMessage>}
            <CalculationPicker
              token={token}
              row={row}
              edit={(patch) => edit(row.key, patch)}
            />
            <div className="commercialActions">
              <button
                type="button"
                disabled={index === 0}
                onClick={() => move(index, -1)}
              >
                Subir artículo
              </button>
              <button
                type="button"
                disabled={index === rows.length - 1}
                onClick={() => move(index, 1)}
              >
                Bajar artículo
              </button>
              <button
                type="button"
                onClick={() =>
                  setRows(rows.filter((item) => item.key !== row.key))
                }
              >
                Quitar artículo
              </button>
            </div>
          </fieldset>
        ))}
        <div className="quoteEditorFooter">
          <div>
            {initial && (
              <p>
                <strong>Total del último guardado:</strong>{' '}
                {gtq(initial.totals.totalAmount)}
              </p>
            )}
            <p>
              El servidor calcula el total al guardar. La alerta de precio bajo
              costo excluye el descuento general.
            </p>
          </div>
          {rows.length > 1 && (
            <Button
              disabled={busy || rows.length >= 200}
              onClick={() => setRows([...rows, blank(next.current++)])}
            >
              Agregar otro artículo
            </Button>
          )}
          <Button tone="quiet" disabled={busy} onClick={onClose}>
            Cerrar edición
          </Button>
        </div>
      </form>
    </section>
  );
}
function DiscountFields({
  label,
  error,
  value,
  onChange,
}: {
  label: string;
  error?: string;
  value: Discount;
  onChange: (value: Discount) => void;
}) {
  return (
    <>
      <label>
        {label}
        <select
          value={value.type}
          onChange={(event) =>
            onChange({
              type: event.target.value as Discount['type'],
              value: null,
            })
          }
        >
          <option value="NONE">Sin descuento</option>
          <option value="FIXED">Importe fijo</option>
          <option value="PERCENTAGE">Porcentaje</option>
        </select>
      </label>
      {value.type === 'NONE' && error && <ErrorMessage>{error}</ErrorMessage>}
      {value.type !== 'NONE' && (
        <Field
          label={label + (value.type === 'FIXED' ? ' (GTQ)' : ' (%)')}
          error={error}
        >
          {(accessibility) => (
            <input
              {...accessibility}
              inputMode="decimal"
              required
              pattern={
                value.type === 'FIXED'
                  ? '[0-9]+(\\.[0-9]{1,2})?'
                  : '[0-9]+(\\.[0-9]{1,4})?'
              }
              value={value.value ?? ''}
              onChange={(event) =>
                onChange({ ...value, value: event.target.value })
              }
            />
          )}
        </Field>
      )}
    </>
  );
}

function compatible(item: HistoryItem, row: Row) {
  if (item.variantId !== null) return String(item.variantId) === row.variantId;
  if (item.productId !== null) return String(item.productId) === row.productId;
  return row.type === 'CUSTOM';
}
function CalculationPicker({
  token,
  row,
  edit,
}: {
  token: string;
  row: Row;
  edit: (patch: Partial<Row>) => void;
}) {
  const [browse, setBrowse] = useState(false);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<{
    content: HistoryItem[];
    totalPages: number;
  }>();
  const [detail, setDetail] = useState<HistoryDetail>();
  const [error, setError] = useState('');
  useEffect(() => {
    if (!browse) return;
    const active = new AbortController();
    void costApi
      .list(token, page, undefined, active.signal)
      .then((result) => {
        if (!active.signal.aborted) setData(result);
      })
      .catch((failure: unknown) => {
        if (!active.signal.aborted) setError(message(failure));
      });
    return () => active.abort();
  }, [token, browse, page]);
  useEffect(() => {
    if (!row.calculationId) return;
    const active = new AbortController();
    void costApi
      .detail(token, Number(row.calculationId), active.signal)
      .then((result) => {
        if (!active.signal.aborted) setDetail(result);
      })
      .catch((failure: unknown) => {
        if (!active.signal.aborted) setError(message(failure));
      });
    return () => active.abort();
  }, [token, row.calculationId]);
  const selected =
    detail && String(detail.calculation.id) === row.calculationId
      ? detail
      : undefined;
  return (
    <div className="commercialLookup">
      <button type="button" onClick={() => setBrowse(!browse)}>
        {browse ? 'Cerrar búsqueda de cálculos' : 'Buscar cálculo histórico'}
      </button>
      {browse && (
        <>
          {!data && !error && <p role="status">Cargando cálculos…</p>}
          {data && (
            <>
              <ul>
                {data.content
                  .filter((item) => compatible(item, row))
                  .map((item) => (
                    <li key={item.id}>
                      <button
                        type="button"
                        onClick={() => {
                          edit({ calculationId: String(item.id) });
                          setBrowse(false);
                        }}
                      >
                        {item.jobDescription} · {techniques[item.technique]}
                      </button>
                    </li>
                  ))}
              </ul>
              {!data.content.some((item) => compatible(item, row)) && (
                <p>No hay cálculos compatibles en esta página.</p>
              )}
              <div className="commercialActions">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage(page - 1)}
                >
                  Anterior
                </button>
                <span>Página {page + 1}</span>
                <button
                  type="button"
                  disabled={page + 1 >= data.totalPages}
                  onClick={() => setPage(page + 1)}
                >
                  Siguiente
                </button>
              </div>
            </>
          )}
        </>
      )}
      {row.calculationId && (
        <p>
          Cálculo #{row.calculationId}{' '}
          {selected && (
            <>
              · {selected.calculation.jobDescription} · Unidades buenas
              calculadas: {selected.calculation.expectedGoodUnits}
            </>
          )}{' '}
          <button type="button" onClick={() => edit({ calculationId: '' })}>
            Desvincular
          </button>
        </p>
      )}
      {selected?.price && (
        <button
          type="button"
          onClick={() => edit({ unitPrice: selected.price?.chosenUnitPrice })}
        >
          Usar precio elegido del cálculo ({gtq(selected.price.chosenUnitPrice)}
          )
        </button>
      )}
      {error && <p role="alert">{error}</p>}
    </div>
  );
}
