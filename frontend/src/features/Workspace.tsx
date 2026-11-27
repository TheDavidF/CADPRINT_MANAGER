import { useCallback, useEffect, useState } from 'react';
import {
  ArrowLeftRight,
  Boxes,
  Calculator,
  FileText,
  LayoutDashboard,
  LogOut,
  ShoppingCart,
  Package,
  Truck,
  Users,
} from 'lucide-react';
import { CategoriesPanel } from './CategoriesPanel';
import { DashboardPage } from './DashboardPage';
import { MaterialsPage } from './MaterialsPage';
import { MovementsPage } from './MovementsPage';
import { SuppliersPage } from './SuppliersPage';
import { ProductsPage } from './products/ProductsPage';
import { PurchasesPage } from './purchases/PurchasesPage';
import { CostsPage } from './costs/CostsPage';
import { CustomersPage } from './customers/CustomersPage';
import { QuotationsPage } from './quotations/QuotationsPage';
import { request } from '../services/api';
import type {
  Category,
  Dashboard,
  Material,
  Movement,
  Supplier,
} from '../services/types';

const navigation = [
  { id: 'dashboard', label: 'Resumen', icon: LayoutDashboard },
  { id: 'materials', label: 'Materiales', icon: Boxes },
  { id: 'movements', label: 'Movimientos', icon: ArrowLeftRight },
  { id: 'suppliers', label: 'Proveedores', icon: Truck },
  { id: 'purchases', label: 'Compras', icon: ShoppingCart },
  { id: 'products', label: 'Productos', icon: Package },
  { id: 'costs', label: 'Costos de producción', icon: Calculator },
  { id: 'customers', label: 'Clientes', icon: Users },
  { id: 'quotations', label: 'Cotizaciones', icon: FileText },
] as const;

export function Workspace({
  token,
  onLogout,
}: {
  token: string;
  onLogout: () => void;
}) {
  const [page, setPage] =
    useState<(typeof navigation)[number]['id']>('dashboard');
  const [data, setData] = useState({
    materials: [] as Material[],
    categories: [] as Category[],
    movements: [] as Movement[],
    suppliers: [] as Supplier[],
    stats: null as Dashboard | null,
    username: '',
    roles: [] as string[],
  });
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(
    async (signal?: AbortSignal) => {
      const options = { signal };
      const [materials, movements, suppliers, stats, user, categories] =
        await Promise.all([
          request<Material[]>(token, '/materials', options),
          request<Movement[]>(token, '/movements', options),
          request<Supplier[]>(token, '/suppliers', options),
          request<Dashboard>(token, '/dashboard', options),
          request<{ username: string; roles: string[] }>(token, '/me', options),
          request<Category[]>(token, '/categories', options),
        ]);
      if (!signal?.aborted) {
        setData({
          materials,
          categories,
          movements,
          suppliers,
          stats,
          username: user.username,
          roles: user.roles,
        });
      }
    },
    [token],
  );

  useEffect(() => {
    const controller = new AbortController();
    void refresh(controller.signal)
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setMessage(errorMessage(error));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [refresh]);

  async function submit(
    path: string,
    payload: unknown,
    reset: () => void,
    method: 'POST' | 'PUT' = 'POST',
  ) {
    setBusy(true);
    setMessage('');
    try {
      await request(token, path, {
        method,
        body: JSON.stringify(payload),
      });
      reset();
      try {
        await refresh();
        setMessage('Operación guardada correctamente');
      } catch (error) {
        setMessage(
          'Operación guardada. Falló la actualización de los datos. ' +
            errorMessage(error),
        );
      }
    } catch (error) {
      setMessage(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  const canManageCatalog = data.roles.some(
    (role) => role === 'ROLE_ADMIN' || role === 'ROLE_OPERADOR',
  );

  return (
    <div className="app">
      <aside>
        <div className="brand">
          ⬡ CADPRINT <span>Manager</span>
        </div>
        <div className="subbrand">Tus ideas toman forma</div>
        <nav aria-label="Secciones">
          {navigation
            .filter(
              (item) =>
                canManageCatalog ||
                ![
                  'purchases',
                  'products',
                  'costs',
                  'customers',
                  'quotations',
                ].includes(item.id),
            )
            .map((item) => (
              <button
                key={item.id}
                className={page === item.id ? 'selected' : ''}
                onClick={() => setPage(item.id)}
              >
                <item.icon size={18} />
                {item.label}
              </button>
            ))}
        </nav>
        <div className="sidebarBottom">
          <span>
            Sesión: <strong>{data.username}</strong>
          </span>
          <button onClick={onLogout}>
            <LogOut size={17} /> Cerrar sesión
          </button>
        </div>
      </aside>
      <main className="content">
        <header>
          <div>
            <p className="eyebrow">CADPRINT / Administración</p>
            <h1>{navigation.find((item) => item.id === page)?.label}</h1>
          </div>
          <span className="local">● Local · MVP 0.1</span>
        </header>
        {message && (
          <div className="notice" role="status">
            {message}
            <button aria-label="Cerrar aviso" onClick={() => setMessage('')}>
              ×
            </button>
          </div>
        )}
        {loading && <p role="status">Cargando datos…</p>}
        {!loading && (
          <>
            <div hidden={page !== 'dashboard'}>
              <DashboardPage stats={data.stats} materials={data.materials} />
            </div>
            <div hidden={page !== 'materials'}>
              <MaterialsPage
                token={token}
                categories={data.categories}
                materials={data.materials}
                busy={busy}
                submit={submit}
              />
            </div>
            <div hidden={page !== 'materials'}>
              <CategoriesPanel
                categories={data.categories}
                busy={busy}
                submit={submit}
              />
            </div>
            <div hidden={page !== 'movements'}>
              <MovementsPage
                materials={data.materials}
                movements={data.movements}
                busy={busy}
                submit={submit}
              />
            </div>
            <div hidden={page !== 'suppliers'}>
              <SuppliersPage
                suppliers={data.suppliers}
                busy={busy}
                submit={submit}
              />
            </div>
            {page === 'costs' && canManageCatalog && (
              <CostsPage
                token={token}
                materials={data.materials}
                admin={data.roles.includes('ROLE_ADMIN')}
              />
            )}
            {page === 'customers' && canManageCatalog && (
              <CustomersPage token={token} />
            )}
            {page === 'quotations' && canManageCatalog && (
              <QuotationsPage
                materials={data.materials}
                refreshInventory={refresh}
                token={token}
                admin={data.roles.includes('ROLE_ADMIN')}
              />
            )}
            {page === 'products' && canManageCatalog && (
              <ProductsPage token={token} roles={data.roles} />
            )}
            {page === 'purchases' && canManageCatalog && (
              <PurchasesPage
                token={token}
                roles={data.roles}
                materials={data.materials}
                suppliers={data.suppliers}
                refreshInventory={refresh}
              />
            )}
          </>
        )}
      </main>
    </div>
  );
}

function errorMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : 'No fue posible completar la operación.';
}
