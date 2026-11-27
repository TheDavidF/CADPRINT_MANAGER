import type { HistoryDetail, Preview } from './costApi';
import { gtq } from '../../services/commercial';

export function CostResult({ result }: { result: Preview | HistoryDetail }) {
  const totals = 'calculation' in result ? result.calculation : result;
  const price = result.price;
  return (
    <section className="panel">
      <h2>
        {'calculation' in result
          ? result.calculation.jobDescription
          : 'Vista previa'}
      </h2>
      <p>
        Unidades buenas: {totals.expectedGoodUnits} · Tiempo productivo:{' '}
        {totals.productiveSeconds} s · Indirectos: {gtq(totals.indirectCost)}
      </p>
      {'calculation' in result && result.fdm && (
        <p>
          Impresión: {result.fdm.printSeconds} s · Trabajo humano:{' '}
          {result.fdm.laborSeconds} s
        </p>
      )}
      {'calculation' in result && result.sublimation && (
        <p>
          Prensado: {result.sublimation.cycleCount} ciclos de{' '}
          {result.sublimation.pressSecondsPerCycle} s · Preparación:{' '}
          {result.sublimation.preparationSeconds} s · Trabajo humano:{' '}
          {result.sublimation.laborSeconds} s
        </p>
      )}
      <div className="tableWrap">
        <table>
          <thead>
            <tr>
              <th>Concepto</th>
              <th>Cantidad</th>
              <th>Tarifa</th>
              <th>Importe</th>
            </tr>
          </thead>
          <tbody>
            {result.lines.map((line) => (
              <tr key={line.position}>
                <td>{line.description}</td>
                <td>
                  {line.quantity} {line.unit}
                </td>
                <td>{gtq(line.appliedRate)}</td>
                <td>{gtq(line.amount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <dl className="commercialTotals">
        <dt>Costo total</dt>
        <dd>{gtq(totals.totalCost)}</dd>
        <dt>Costo por unidad buena</dt>
        <dd>{gtq(totals.unitCost)}</dd>
        {price && (
          <>
            <dt>Margen objetivo</dt>
            <dd>{price.targetMarginPct}%</dd>
            <dt>Precio sugerido</dt>
            <dd>{gtq(price.suggestedUnitPrice)}</dd>
            <dt>Precio elegido</dt>
            <dd>{gtq(price.chosenUnitPrice)}</dd>
            <dt>Ganancia bruta por unidad</dt>
            <dd>{gtq(price.grossProfitPerUnit)}</dd>
            <dt>Margen real</dt>
            <dd>
              {price.actualMarginPct === null
                ? 'No aplica'
                : price.actualMarginPct + '%'}
            </dd>
          </>
        )}
      </dl>
      {price?.belowCost && (
        <p className="warning">
          El precio elegido es inferior al costo unitario estimado.
        </p>
      )}
    </section>
  );
}
