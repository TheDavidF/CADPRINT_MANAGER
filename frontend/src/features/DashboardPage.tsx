import type { Dashboard, Material } from '../services/types';
export function DashboardPage({
  stats,
  materials,
}: {
  stats: Dashboard | null;
  materials: Material[];
}) {
  return (
    <>
      <div className="cards">
        <article>
          <span>Materiales</span>
          <strong>{stats?.materials ?? '—'}</strong>
        </article>
        <article>
          <span>Stock bajo</span>
          <strong>{stats?.lowStock ?? '—'}</strong>
        </article>
        <article>
          <span>Valor estimado del inventario</span>
          <strong>Q {Number(stats?.inventoryValue || 0).toFixed(2)}</strong>
        </article>
        <article>
          <span>Movimientos</span>
          <strong>{stats?.movements ?? '—'}</strong>
        </article>
      </div>
      <section>
        <h2>Existencias que requieren atención</h2>
        <div className="tableWrap">
          <table>
            <thead>
              <tr>
                <th>Material</th>
                <th>SKU</th>
                <th>Existencia</th>
                <th>Mínimo</th>
              </tr>
            </thead>
            <tbody>
              {materials
                .filter((m) => m.active && m.stock <= m.minStock)
                .map((m) => (
                  <tr key={m.id}>
                    <td>{m.name}</td>
                    <td>{m.sku}</td>
                    <td>
                      {m.stock} {m.unit}
                    </td>
                    <td>{m.minStock}</td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
