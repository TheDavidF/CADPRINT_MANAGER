import { useEffect, useState } from 'react';
import { getProduct, listProducts } from './productApi';
import type { Product, ProductVariant } from './productTypes';
import { message } from '../../services/commercial';

export type CatalogSelection = { productId: string; variantId: string };
export function CatalogPicker({
  token,
  value,
  onChange,
}: {
  token: string;
  value: CatalogSelection;
  onChange: (value: CatalogSelection) => void;
}) {
  const [search, setSearch] = useState('');
  const [products, setProducts] = useState<Product[]>([]);
  const [selected, setSelected] = useState<Product | null>(null);
  const [variants, setVariants] = useState<ProductVariant[]>([]);
  const [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      void listProducts(
        token,
        {
          search,
          active: 'true',
          categoryId: '',
          productionMode: '',
          pricingMode: '',
        },
        0,
        controller.signal,
      )
        .then((result) => {
          if (!controller.signal.aborted) setProducts(result.content);
        })
        .catch((failure: unknown) => {
          if (!controller.signal.aborted) setError(message(failure));
        });
    }, 250);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [token, search]);
  useEffect(() => {
    if (!value.productId) return;
    let alive = true;
    void getProduct(token, Number(value.productId))
      .then((result) => {
        if (alive) {
          setSelected(result.product);
          setVariants(result.variants);
        }
      })
      .catch((failure: unknown) => {
        if (alive) setError(message(failure));
      });
    return () => {
      alive = false;
    };
  }, [token, value.productId]);
  const current = selected?.id === Number(value.productId) ? selected : null;
  return (
    <>
      <label>
        Buscar en catálogo
        <input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Nombre o SKU"
        />
      </label>
      <label>
        Producto
        <select
          value={value.productId}
          onChange={(event) =>
            onChange({ productId: event.target.value, variantId: '' })
          }
        >
          <option value="">Selecciona producto</option>
          {current &&
            !products.some((product) => product.id === current.id) && (
              <option value={current.id}>
                {current.sku} · {current.name}
              </option>
            )}
          {value.productId &&
            !current &&
            !products.some(
              (product) => String(product.id) === value.productId,
            ) && (
              <option value={value.productId}>
                Producto #{value.productId}
              </option>
            )}
          {products.map((product) => (
            <option key={product.id} value={product.id}>
              {product.sku} · {product.name}
            </option>
          ))}
        </select>
      </label>
      {value.productId && (
        <label>
          Variante
          <select
            value={value.variantId}
            onChange={(event) =>
              onChange({ ...value, variantId: event.target.value })
            }
          >
            <option value="">Sin variante</option>
            {value.variantId &&
              (!current ||
                !variants.some(
                  (variant) => String(variant.id) === value.variantId,
                )) && (
                <option value={value.variantId}>
                  Variante #{value.variantId}
                </option>
              )}
            {current &&
              variants
                .filter(
                  (variant) =>
                    variant.commerciallyActive ||
                    String(variant.id) === value.variantId,
                )
                .map((variant) => (
                  <option value={variant.id} key={variant.id}>
                    {variant.sku} · {variant.name}
                  </option>
                ))}
          </select>
        </label>
      )}
      {error && <p role="alert">{error}</p>}
    </>
  );
}
