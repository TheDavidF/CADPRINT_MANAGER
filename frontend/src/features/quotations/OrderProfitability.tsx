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
};

const decimals = [
  'salesExcludingShipping',
  'coveredEstimatedCost',
  'estimatedProfit',
  'marginPercent',
];

export function OrderProfitability({
  token,
  current,
}: {
  token: string;
  current: Summary;
}) {
  const path =
    '/quotations/' +
    current.quotationId +
    '/revisions/' +
    current.revisionNumber +
    '/order/profitability';
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
        </>
      )}
    </section>
  );
}
