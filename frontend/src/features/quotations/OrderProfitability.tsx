import { OrderDirectCosts } from './OrderDirectCosts';
import type { DirectCost } from './OrderDirectCosts';
import { useEffect, useState } from 'react';
import { Button, ErrorMessage, SummaryGrid } from '../../components/WorkflowUi';
import { body, exactRequest, gtq, message } from '../../services/commercial';
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
  costsConfirmedBy: string | null;
  realProfit: string | null;
  realMarginPercent: string | null;
  profitDifferenceExcludingShipping: string | null;
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
  'realProfit',
  'realMarginPercent',
  'profitDifferenceExcludingShipping',
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
  const [confirming, setConfirming] = useState(false);

  function refresh() {
    setEstimate(undefined);
    setReload((previous) => previous + 1);
  }

  async function confirm() {
    if (!estimate || confirming) return;
    setError('');
    setConfirming(true);
    try {
      await exactRequest<Estimate>(
        token,
        path + '/confirm',
        decimals,
        body({
          consumptionCount: estimate.consumptionCount,
          directCostCount: estimate.directCosts.length,
        }),
      );
      refresh();
    } catch (failure) {
      setError(message(failure));
      refresh();
    } finally {
      setConfirming(false);
    }
  }

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
            refresh={refresh}
          />
          <h4>Utilidad real del pedido</h4>
          {estimate.realProfit === null ? (
            <>
              {estimate.unvaluedConsumptions > 0 ? (
                <p>
                  Hay consumos sin costo registrado. Completa su valoración
                  antes de confirmar.
                </p>
              ) : (
                <>
                  <p>
                    Confirma cuando hayas registrado todos los costos del
                    pedido.
                  </p>
                  <Button disabled={confirming} onClick={() => void confirm()}>
                    {confirming ? 'Confirmando…' : 'Confirmar costos completos'}
                  </Button>
                </>
              )}
            </>
          ) : (
            <>
              <SummaryGrid
                items={[
                  {
                    label: 'Utilidad real, con envío',
                    value: gtq(estimate.realProfit),
                  },
                  {
                    label: 'Margen real',
                    value:
                      estimate.realMarginPercent === null
                        ? '—'
                        : estimate.realMarginPercent + ' %',
                  },
                  {
                    label: 'Diferencia con la utilidad estimada, sin envío',
                    value:
                      estimate.profitDifferenceExcludingShipping === null
                        ? 'Sin estimación completa'
                        : gtq(estimate.profitDifferenceExcludingShipping),
                  },
                ]}
              />
              <p>Costos confirmados por {estimate.costsConfirmedBy}.</p>
            </>
          )}
        </>
      )}
    </section>
  );
}
