import { OrderDirectCosts } from './OrderDirectCosts';
import type { DirectCost } from './OrderDirectCosts';
import { useEffect, useState } from 'react';
import { Button, ErrorMessage, SummaryGrid } from '../../components/WorkflowUi';
import { exactRequest, gtq, message } from '../../services/commercial';
import type { Summary } from './quotationApi';

type Estimate = {
  salesExcludingShipping: string;
  coveredEstimatedCost: string;
  lineCount: number;
  missingCostLines: number;
  estimatedProfit: string | null;
  marginPercent: string | null;
  recordedMaterialCost: string;
  consumptionCount: number;
  unvaluedConsumptions: number;
  directCostTotal: string;
  recordedCostTotal: string;
  directCosts: DirectCost[];
};

const decimals = [
  'salesExcludingShipping',
  'coveredEstimatedCost',
  'estimatedProfit',
  'marginPercent',
  'recordedMaterialCost',
  'directCostTotal',
  'recordedCostTotal',
  'amount',
];

export function OrderProfitability({
  token,
  current,
}: {
  token: string;
  current: Summary;
}) {
  const orderPath =
    '/quotations/' +
    current.quotationId +
    '/revisions/' +
    current.revisionNumber +
    '/order';
  const path = orderPath + '/profitability';
  const [estimate, setEstimate] = useState<Estimate>();
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);

  useEffect(() => {
    const active = new AbortController();
    void exactRequest<Estimate>(token, path, decimals, {
      signal: active.signal,
    }).then(
      (value) => {
        if (!active.signal.aborted) setEstimate(value);
      },
      (failure: unknown) => {
        if (!active.signal.aborted) setError(message(failure));
      },
    );
    return () => active.abort();
  }, [token, path, reload]);

  return (
    <section aria-label="Rentabilidad estimada">
      <h4>Rentabilidad estimada</h4>
      {error && (
        <>
          <ErrorMessage>{error}</ErrorMessage>
          <Button
            onClick={() => {
              setError('');
              setReload((previous) => previous + 1);
            }}
          >
            Reintentar consulta
          </Button>
        </>
      )}
      {!estimate && !error && <p role="status">Consultando rentabilidad…</p>}
      {estimate && (
        <>
          <SummaryGrid
            items={[
              {
                label: 'Venta acordada sin envío',
                value: gtq(estimate.salesExcludingShipping),
              },
              {
                label: 'Costo estimado de líneas vinculadas',
                value: gtq(estimate.coveredEstimatedCost),
              },
              {
                label: 'Utilidad estimada',
                value:
                  estimate.estimatedProfit === null
                    ? 'Sin datos completos'
                    : gtq(estimate.estimatedProfit),
              },
              {
                label: 'Margen estimado',
                value:
                  estimate.marginPercent === null
                    ? '—'
                    : estimate.marginPercent + ' %',
              },
            ]}
          />
          {estimate.missingCostLines > 0 ? (
            <p>
              Líneas sin cálculo vinculado: {estimate.missingCostLines} de{' '}
              {estimate.lineCount}.
            </p>
          ) : (
            <p>
              Calculado con los costos de producción vinculados a la revisión
              aceptada.
            </p>
          )}
          <h4>Materiales consumidos</h4>
          {estimate.consumptionCount === 0 ? (
            <p>Sin consumos registrados.</p>
          ) : (
            <>
              <SummaryGrid
                items={[
                  {
                    label: 'Costo de materiales con valor registrado',
                    value: gtq(estimate.recordedMaterialCost),
                  },
                ]}
              />
              {estimate.unvaluedConsumptions > 0 && (
                <p>
                  Consumos sin costo registrado: {estimate.unvaluedConsumptions}{' '}
                  de {estimate.consumptionCount}.
                </p>
              )}
              <p>
                Valorado al costo unitario de inventario al registrar cada
                salida. Incluye únicamente materiales.
              </p>
            </>
          )}
          <OrderDirectCosts
            token={token}
            path={orderPath + '/direct-costs'}
            costs={estimate.directCosts}
            directTotal={estimate.directCostTotal}
            recordedTotal={estimate.recordedCostTotal}
            refresh={() => setReload((previous) => previous + 1)}
          />
        </>
      )}
    </section>
  );
}
