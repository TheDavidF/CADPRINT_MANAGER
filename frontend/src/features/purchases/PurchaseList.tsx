import { useState } from 'react';
import type { Supplier } from '../../services/types';
import {
  money,
  purchaseStatuses,
  type Filters,
  type PurchasePage,
} from './purchaseApi';

export const emptyFilters: Filters = {
  status: '',
  supplierId: '',
  from: '',
  to: '',
  documentNumber: '',
};

export function PurchaseList({
  suppliers,
  appliedFilters,
  result,
  loading,
  error,
  opening,
  onFilter,
  onPage,
  onOpen,
  onNew,
  onRetry,
}: {
  suppliers: Supplier[];
  appliedFilters: Filters;
  result: PurchasePage | null;
  loading: boolean;
  error: string;
  opening: boolean;
  onFilter: (filters: Filters) => void;
  onPage: (page: number) => void;
  onOpen: (id: number) => void;
  onNew: () => void;
  onRetry: () => void;
}) {
  const [filters, setFilters] = useState(appliedFilters);
  const update = (field: keyof Filters, value: string) =>
    setFilters({ ...filters, [field]: value });

  return (
    <section>
      <div className="purchaseActions">
        <button type="button" disabled={opening} onClick={onNew}>
          Nueva compra
        </button>
      </div>
      <form
        className="formGrid"
        onSubmit={(event) => {
          event.preventDefault();
          onFilter(filters);
        }}
      >
        <label>
          Estado
          <select
            value={filters.status}
            onChange={(event) => update('status', event.target.value)}
          >
            <option value="">Todos</option>
            {purchaseStatuses.map((status) => (
              <option key={status}>{status}</option>
            ))}
          </select>
        </label>
        <label>
          Proveedor
          <select
            value={filters.supplierId}
            onChange={(event) => update('supplierId', event.target.value)}
          >
            <option value="">Todos</option>
            {suppliers.map((supplier) => (
              <option key={supplier.id} value={supplier.id}>
                {supplier.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Desde
          <input
            type="date"
            value={filters.from}
            max={filters.to || undefined}
            onChange={(event) => update('from', event.target.value)}
          />
        </label>
        <label>
          Hasta
          <input
            type="date"
            value={filters.to}
            min={filters.from || undefined}
            onChange={(event) => update('to', event.target.value)}
          />
        </label>
        <label>
          Buscar por documento
          <input
            type="search"
            value={filters.documentNumber}
            onChange={(event) => update('documentNumber', event.target.value)}
          />
        </label>
        <button type="submit">Aplicar filtros</button>
      </form>
      {loading && <p role="status">Cargando compras…</p>}
      {opening && <p role="status">Abriendo compra…</p>}
      {error && (
        <div className="notice">
          <span role="alert">{error}</span>
          <button type="button" onClick={onRetry}>
            Reintentar listado
          </button>
        </div>
      )}
      {!loading &&
        result &&
        (result.content.length === 0 ? (
          <p>No hay compras para estos filtros.</p>
        ) : (
          <div className="tableWrap purchaseTable">
            <table>
              <thead>
                <tr>
                  <th>Compra</th>
                  <th>Fecha</th>
                  <th>Proveedor</th>
                  <th>Documento</th>
                  <th>Estado</th>
                  <th>Total</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((purchase) => (
                  <tr key={purchase.id}>
                    <td>#{purchase.id}</td>
                    <td>{purchase.purchaseDate}</td>
                    <td>{purchase.supplierName ?? '—'}</td>
                    <td>{purchase.documentNumber ?? '—'}</td>
                    <td>
                      <strong>{purchase.status}</strong>
                    </td>
                    <td>{money.format(purchase.total)}</td>
                    <td>
                      <button
                        type="button"
                        disabled={opening}
                        onClick={() => onOpen(purchase.id)}
                      >
                        Ver detalle
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
      {!loading && result && result.totalPages > 1 && (
        <div className="purchaseActions" aria-label="Paginación de compras">
          <button
            type="button"
            disabled={result.page === 0}
            onClick={() => onPage(result.page - 1)}
          >
            Anterior
          </button>
          <span>
            Página {result.page + 1} de {result.totalPages}
          </span>
          <button
            type="button"
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
