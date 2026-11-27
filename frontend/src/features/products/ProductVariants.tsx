import { useState } from 'react';
import { priceLabel, validPrice } from './productApi';
import type { ProductVariant, VariantInput } from './productTypes';

export function ProductVariants({
  variants,
  admin,
  busy,
  revision,
  onSave,
  onStatus,
}: {
  variants: ProductVariant[];
  admin: boolean;
  busy: boolean;
  revision: number;
  onSave: (input: VariantInput, id?: number) => Promise<boolean>;
  onStatus: (variant: ProductVariant) => void;
}) {
  const [editor, setEditor] = useState<ProductVariant | 'new' | null>(null);
  return (
    <section>
      <h2>Variantes</h2>
      <button disabled={busy} onClick={() => setEditor('new')}>
        Nueva variante
      </button>
      {editor && (
        <VariantForm
          key={String(editor === 'new' ? 'new' : editor.id) + '-' + revision}
          variant={
            editor === 'new'
              ? undefined
              : (variants.find((item) => item.id === editor.id) ?? editor)
          }
          busy={busy}
          onSave={onSave}
          onClose={() => setEditor(null)}
        />
      )}
      {!variants.length ? (
        <p>Este producto todavía no tiene variantes.</p>
      ) : (
        <div className="tableWrap purchaseTable">
          <table>
            <thead>
              <tr>
                <th>SKU</th>
                <th>Nombre</th>
                <th>Atributos</th>
                <th>Precio propio</th>
                <th>Precio efectivo</th>
                <th>Cotización</th>
                <th>Estado</th>
                <th>Disponibilidad comercial</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {variants.map((variant) => (
                <tr key={variant.id}>
                  <td>{variant.sku}</td>
                  <td>{variant.name}</td>
                  <td>
                    {[variant.size, variant.color, variant.presentation]
                      .filter(Boolean)
                      .join(' · ') || '—'}
                  </td>
                  <td>{priceLabel(variant.ownPrice)}</td>
                  <td>{priceLabel(variant.effectivePrice)}</td>
                  <td>
                    {variant.requiresQuotation ? (
                      <span className="catalogQuote">Requiere cotización</span>
                    ) : (
                      '—'
                    )}
                  </td>
                  <td>{variant.active ? 'Activa' : 'Inactiva'}</td>
                  <td>
                    {variant.commerciallyActive
                      ? 'Disponible'
                      : 'No disponible'}
                  </td>
                  <td>
                    <div className="materialActions">
                      <button
                        disabled={busy}
                        onClick={() => setEditor(variant)}
                      >
                        Editar variante
                      </button>
                      {admin && (
                        <button
                          disabled={busy}
                          onClick={() => onStatus(variant)}
                        >
                          {variant.active ? 'Desactivar' : 'Reactivar'}
                        </button>
                      )}
                    </div>
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

function VariantForm({
  variant,
  busy,
  onSave,
  onClose,
}: {
  variant?: ProductVariant;
  busy: boolean;
  onSave: (input: VariantInput, id?: number) => Promise<boolean>;
  onClose: () => void;
}) {
  const [fields, setFields] = useState({
    sku: variant?.sku ?? '',
    name: variant?.name ?? '',
    size: variant?.size ?? '',
    color: variant?.color ?? '',
    presentation: variant?.presentation ?? '',
    price: variant?.ownPrice?.toString() ?? '',
  });
  const [validation, setValidation] = useState('');
  const labels = {
    sku: 'SKU de variante',
    name: 'Nombre de variante',
    size: 'Tamaño',
    color: 'Color',
    presentation: 'Presentación',
    price: 'Precio propio (Q)',
  } as const;
  return (
    <form
      className="catalogVariantForm"
      onSubmit={(event) => {
        event.preventDefault();
        const price = fields.price.trim().replace(',', '.');
        if (
          !fields.sku.trim() ||
          !fields.name.trim() ||
          (price && !validPrice(price))
        ) {
          setValidation(
            'Indica el SKU y el nombre. El precio propio, si se indica, debe ser positivo y tener hasta dos decimales.',
          );
          return;
        }
        void onSave(
          {
            sku: fields.sku.trim(),
            name: fields.name.trim(),
            size: fields.size.trim() || null,
            color: fields.color.trim() || null,
            presentation: fields.presentation.trim() || null,
            ownPrice: price || null,
            version: variant?.version,
          },
          variant?.id,
        ).then((saved) => {
          if (saved) onClose();
        });
      }}
    >
      <fieldset className="purchaseFields" disabled={busy}>
        <div className="formGrid">
          {(Object.keys(labels) as (keyof typeof labels)[]).map((key) => (
            <label key={key}>
              {labels[key]}
              <input
                required={key === 'sku' || key === 'name'}
                inputMode={key === 'price' ? 'decimal' : undefined}
                maxLength={key === 'price' ? 17 : 255}
                value={fields[key]}
                onChange={(event) => {
                  setValidation('');
                  setFields((previous) => ({
                    ...previous,
                    [key]: event.target.value,
                  }));
                }}
              />
            </label>
          ))}
        </div>
        {validation && (
          <p className="notice" role="alert">
            {validation}
          </p>
        )}
        <div className="purchaseActions">
          <button type="submit">
            {busy ? 'Guardando…' : 'Guardar variante'}
          </button>
          <button type="button" onClick={onClose}>
            Cancelar edición
          </button>
        </div>
      </fieldset>
    </form>
  );
}
