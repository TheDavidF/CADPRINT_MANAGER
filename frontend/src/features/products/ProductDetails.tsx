import { priceLabel } from './productApi';
import { ProductImagePanel } from './ProductImagePanel';
import { ProductVariants } from './ProductVariants';
import {
  pricingModes,
  productionModes,
  type ProductDetail,
  type ProductVariant,
  type VariantInput,
} from './productTypes';

export function ProductDetails({
  detail,
  token,
  admin,
  busy,
  variantRevision,
  onBack,
  onEdit,
  onStatus,
  onSaveVariant,
  onVariantStatus,
  onUpload,
  onRemove,
}: {
  detail: ProductDetail;
  token: string;
  admin: boolean;
  busy: boolean;
  variantRevision: number;
  onBack: () => void;
  onEdit: () => void;
  onStatus: () => void;
  onSaveVariant: (input: VariantInput, id?: number) => Promise<boolean>;
  onVariantStatus: (variant: ProductVariant) => void;
  onUpload: (file: File) => Promise<boolean>;
  onRemove: () => void;
}) {
  const { product } = detail;
  return (
    <>
      <section>
        <h2>{product.name}</h2>
        <dl className="purchaseSummary">
          <div>
            <dt>SKU</dt>
            <dd>{product.sku}</dd>
          </div>
          <div>
            <dt>Categoría comercial</dt>
            <dd>{product.category.name}</dd>
          </div>
          <div>
            <dt>Producción</dt>
            <dd>{productionModes[product.productionMode]}</dd>
          </div>
          <div>
            <dt>Modalidad de precio</dt>
            <dd
              className={product.requiresQuotation ? 'catalogQuote' : undefined}
            >
              {pricingModes[product.pricingMode]}
            </dd>
          </div>
          <div>
            <dt>
              {product.requiresQuotation ? 'Precio orientativo' : 'Precio base'}
            </dt>
            <dd>{priceLabel(product.basePrice)}</dd>
          </div>
          <div>
            <dt>Estado</dt>
            <dd>{product.active ? 'Activo' : 'Inactivo'}</dd>
          </div>
        </dl>
        {product.description && (
          <p className="catalogProductDescription">{product.description}</p>
        )}
        <div className="purchaseActions">
          <button disabled={busy} onClick={onBack}>
            Volver al listado
          </button>
          <button disabled={busy} onClick={onEdit}>
            Editar producto
          </button>
          {admin && (
            <button disabled={busy} onClick={onStatus}>
              {product.active ? 'Desactivar producto' : 'Reactivar producto'}
            </button>
          )}
        </div>
      </section>
      <ProductVariants
        variants={detail.variants}
        admin={admin}
        busy={busy}
        revision={variantRevision}
        onSave={onSaveVariant}
        onStatus={onVariantStatus}
      />
      <ProductImagePanel
        key={product.id}
        token={token}
        product={product}
        image={detail.image}
        admin={admin}
        busy={busy}
        onUpload={onUpload}
        onRemove={onRemove}
      />
    </>
  );
}
