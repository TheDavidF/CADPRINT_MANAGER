import { useState } from 'react';
import { validPrice } from './productApi';
import {
  pricingModes,
  productionModes,
  type PricingMode,
  type Product,
  type ProductCategory,
  type ProductInput,
  type ProductionMode,
} from './productTypes';

export function ProductForm({
  product,
  categories,
  busy,
  onSave,
  onCancel,
}: {
  product?: Product;
  categories: ProductCategory[];
  busy: boolean;
  onSave: (input: ProductInput) => void;
  onCancel: () => void;
}) {
  const [fields, setFields] = useState({
    sku: product?.sku ?? '',
    name: product?.name ?? '',
    description: product?.description ?? '',
    categoryId: String(product?.category.id ?? ''),
    productionMode: product?.productionMode ?? 'BAJO_PEDIDO',
    pricingMode: product?.pricingMode ?? 'PRECIO_BASE',
    basePrice: product?.basePrice?.toString() ?? '',
  });
  const [validation, setValidation] = useState('');
  const update = <K extends keyof typeof fields>(
    key: K,
    value: (typeof fields)[K],
  ) => {
    setValidation('');
    setFields((current) => ({ ...current, [key]: value }));
  };
  const quotation = fields.pricingMode === 'REQUIERE_COTIZACION';
  const eligible = categories.filter(
    (category) => category.active || category.id === product?.category.id,
  );

  return (
    <section>
      <h2>{product ? 'Editar producto · ' + product.sku : 'Nuevo producto'}</h2>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          if (!fields.sku.trim() || !fields.name.trim() || !fields.categoryId) {
            setValidation('Completa el SKU, el nombre y la categoría.');
            return;
          }
          const price = fields.basePrice.trim().replace(',', '.');
          if ((!quotation || price) && !validPrice(price)) {
            setValidation(
              'Ingresa un precio positivo con hasta dos decimales.',
            );
            return;
          }
          onSave({
            ...fields,
            sku: fields.sku.trim(),
            name: fields.name.trim(),
            description: fields.description.trim() || null,
            categoryId: Number(fields.categoryId),
            basePrice: price || null,
            version: product?.version,
          });
        }}
      >
        <fieldset className="purchaseFields" disabled={busy}>
          <div className="formGrid">
            <label>
              SKU
              <input
                required
                maxLength={255}
                value={fields.sku}
                onChange={(event) => update('sku', event.target.value)}
              />
            </label>
            <label>
              Nombre
              <input
                required
                maxLength={255}
                value={fields.name}
                onChange={(event) => update('name', event.target.value)}
              />
            </label>
            <label>
              Categoría comercial
              <select
                required
                value={fields.categoryId}
                onChange={(event) => update('categoryId', event.target.value)}
              >
                <option value="">Selecciona una categoría</option>
                {eligible.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                    {category.active ? '' : ' (inactiva)'}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Modalidad de producción
              <select
                value={fields.productionMode}
                onChange={(event) =>
                  update('productionMode', event.target.value as ProductionMode)
                }
              >
                {Object.entries(productionModes).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Modalidad de precio
              <select
                value={fields.pricingMode}
                onChange={(event) =>
                  update('pricingMode', event.target.value as PricingMode)
                }
              >
                {Object.entries(pricingModes).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              {quotation ? 'Precio orientativo (Q)' : 'Precio base (Q)'}
              <input
                inputMode="decimal"
                maxLength={17}
                required={!quotation}
                value={fields.basePrice}
                onChange={(event) => update('basePrice', event.target.value)}
              />
            </label>
            <label className="catalogDescription">
              Descripción
              <textarea
                rows={3}
                maxLength={2000}
                value={fields.description}
                onChange={(event) => update('description', event.target.value)}
              />
            </label>
          </div>
          {quotation && (
            <p className="hint">
              El importe es opcional y solo sirve como referencia.
            </p>
          )}
          <p className="hint">
            Estado: {product?.active === false ? 'Inactivo' : 'Activo'}. Los
            cambios de estado se realizan en el detalle.
          </p>
          {!eligible.length && (
            <p role="status">
              Debe existir una categoría comercial activa antes de crear el
              producto.
            </p>
          )}
          {validation && (
            <p className="notice" role="alert">
              {validation}
            </p>
          )}
          <div className="purchaseActions">
            <button type="submit" disabled={!eligible.length}>
              {busy ? 'Guardando…' : 'Guardar producto'}
            </button>
            <button type="button" onClick={onCancel}>
              Volver
            </button>
          </div>
        </fieldset>
      </form>
    </section>
  );
}
