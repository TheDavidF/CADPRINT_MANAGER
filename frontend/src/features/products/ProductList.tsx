import { useState } from 'react';
import { priceLabel } from './productApi';
import {
  pricingModes,
  productionModes,
  type ProductCategory,
  type ProductFilters,
  type ProductPage,
} from './productTypes';

export const emptyFilters: ProductFilters = {
  search: '',
  categoryId: '',
  productionMode: '',
  pricingMode: '',
  active: '',
};

export function ProductList({
  categories,
  applied,
  result,
  loading,
  busy,
  error,
  onApply,
  onPage,
  onSelect,
  onNew,
  onRetry,
}: {
  categories: ProductCategory[];
  applied: ProductFilters;
  result: ProductPage | null;
  loading: boolean;
  busy: boolean;
  error: string;
  onApply: (filters: ProductFilters) => void;
  onPage: (page: number) => void;
  onSelect: (id: number, edit?: boolean) => void;
  onNew: () => void;
  onRetry: () => void;
}) {
  const [filters, setFilters] = useState(applied);
  const update = (key: keyof ProductFilters, value: string) =>
    setFilters((current) => ({ ...current, [key]: value }));
  return (
    <section>
      <div className="purchaseActions">
        <button type="button" disabled={busy} onClick={onNew}>
          Nuevo producto
        </button>
      </div>
      <form
        className="formGrid"
        onSubmit={(event) => {
          event.preventDefault();
          onApply(filters);
        }}
      >
        <label>
          Buscar por nombre o SKU
          <input
            type="search"
            maxLength={255}
            value={filters.search}
            onChange={(event) => update('search', event.target.value)}
          />
        </label>
        <label>
          Categoría
          <select
            value={filters.categoryId}
            onChange={(event) => update('categoryId', event.target.value)}
          >
            <option value="">Todas</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Modalidad de producción
          <select
            value={filters.productionMode}
            onChange={(event) => update('productionMode', event.target.value)}
          >
            <option value="">Todas</option>
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
            value={filters.pricingMode}
            onChange={(event) => update('pricingMode', event.target.value)}
          >
            <option value="">Todas</option>
            {Object.entries(pricingModes).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Estado
          <select
            value={filters.active}
            onChange={(event) => update('active', event.target.value)}
          >
            <option value="">Todos</option>
            <option value="true">Activos</option>
            <option value="false">Inactivos</option>
          </select>
        </label>
        <button type="submit">Aplicar filtros</button>
      </form>
      {loading && <p role="status">Cargando productos…</p>}
      {busy && <p role="status">Abriendo producto…</p>}
      {error && (
        <div className="notice">
          <span role="alert">{error}</span>
          <button onClick={onRetry}>Reintentar listado</button>
        </div>
      )}
      {!loading &&
        result &&
        (result.content.length ? (
          <div className="tableWrap purchaseTable">
            <table>
              <thead>
                <tr>
                  <th>SKU</th>
                  <th>Nombre</th>
                  <th>Categoría</th>
                  <th>Producción</th>
                  <th>Modalidad de precio</th>
                  <th>Precio base</th>
                  <th>Estado</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((product) => (
                  <tr key={product.id}>
                    <td>{product.sku}</td>
                    <td>{product.name}</td>
                    <td>{product.category.name}</td>
                    <td>{productionModes[product.productionMode]}</td>
                    <td>
                      <span
                        className={
                          product.requiresQuotation ? 'catalogQuote' : undefined
                        }
                      >
                        {pricingModes[product.pricingMode]}
                      </span>
                    </td>
                    <td>{priceLabel(product.basePrice)}</td>
                    <td>{product.active ? 'Activo' : 'Inactivo'}</td>
                    <td>
                      <div className="materialActions">
                        <button
                          disabled={busy}
                          onClick={() => onSelect(product.id)}
                        >
                          Ver detalle
                        </button>
                        <button
                          disabled={busy}
                          onClick={() => onSelect(product.id, true)}
                        >
                          Editar
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p>No hay productos para estos filtros.</p>
        ))}
      {!loading && result && result.totalPages > 1 && (
        <div className="purchaseActions" aria-label="Paginación de productos">
          <button
            disabled={result.page === 0}
            onClick={() => onPage(result.page - 1)}
          >
            Anterior
          </button>
          <span>
            Página {result.page + 1} de {result.totalPages}
          </span>
          <button
            disabled={result.page + 1 >= result.totalPages}
            onClick={() => onPage(result.page + 1)}
          >
            Siguiente
          </button>
        </div>
      )}
    </section>
  );
}
