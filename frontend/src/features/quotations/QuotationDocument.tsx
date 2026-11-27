import { Button, ErrorMessage } from '../../components/WorkflowUi';
import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../services/api';
import { message } from '../../services/commercial';
import { quotationApi } from './quotationApi';
import type { DocumentStatus, Summary } from './quotationApi';

export function QuotationDocument({
  token,
  current,
}: {
  token: string;
  current: Summary;
}) {
  const [status, setStatus] = useState<DocumentStatus | null>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [unavailable, setUnavailable] = useState(false);
  const controller = useRef<AbortController | null>(null);
  const { quotationId, revisionNumber } = current;
  useEffect(() => {
    const active = new AbortController();
    controller.current = active;
    return () => active.abort();
  }, [quotationId, revisionNumber, token]);

  const pending =
    status?.status === 'PENDING' || status?.status === 'PROCESSING';
  useEffect(() => {
    if (status !== undefined && !pending) return;
    const active = new AbortController();
    let timer: number;
    async function check() {
      try {
        const result = await quotationApi.document(
          token,
          current,
          active.signal,
        );
        if (active.signal.aborted) return;
        setStatus(result);
        setError('');
        if (result.status === 'PENDING' || result.status === 'PROCESSING')
          timer = window.setTimeout(() => void check(), 3500);
      } catch (failure) {
        if (active.signal.aborted) return;
        if (failure instanceof ApiError && failure.status === 404)
          setStatus(null);
        else {
          setError(message(failure));
          timer = window.setTimeout(() => void check(), 6000);
        }
      }
    }
    timer = window.setTimeout(() => void check(), pending ? 3500 : 0);
    return () => {
      active.abort();
      clearTimeout(timer);
    };
  }, [token, current, pending, status]);

  async function generate() {
    const signal = controller.current?.signal;
    setBusy(true);
    setError('');
    try {
      const result = await quotationApi.document(token, current, signal, true);
      if (!signal?.aborted) setStatus(result);
    } catch (failure) {
      if (!signal?.aborted) setError(message(failure));
    } finally {
      if (!signal?.aborted) setBusy(false);
    }
  }
  async function download() {
    const signal = controller.current?.signal;
    setBusy(true);
    setError('');
    try {
      const { blob, disposition } = await quotationApi.download(
        token,
        current,
        signal,
      );
      if (signal?.aborted) return;
      const filename = disposition?.match(
        /(?:^|;)\s*filename="?([^";]+)"?/i,
      )?.[1];
      const url = URL.createObjectURL(blob);
      const link = window.document.createElement('a');
      link.href = url;
      link.download =
        filename ||
        'CADPRINT-' +
          current.quotationNumber +
          '-R' +
          String(revisionNumber).padStart(2, '0') +
          '.pdf';
      window.document.body.append(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      setUnavailable(false);
    } catch (failure) {
      if (signal?.aborted) return;
      if (failure instanceof ApiError && failure.status === 503) {
        setUnavailable(true);
        setError(
          'El PDF original no está disponible. Debe recuperarse desde el respaldo.',
        );
      } else setError(message(failure));
    } finally {
      if (!signal?.aborted) setBusy(false);
    }
  }
  return (
    <div className="quotePdf" aria-label="PDF de la revisión seleccionada">
      {status === undefined && !error && (
        <p role="status">Consultando documento…</p>
      )}
      {pending && <p role="status">Preparando PDF</p>}
      {status?.status === 'FAILED' && (
        <p role="status">Falló la preparación del PDF.</p>
      )}
      {error && <ErrorMessage>{error}</ErrorMessage>}
      {status === null && (
        <Button disabled={busy} onClick={() => void generate()}>
          Solicitar PDF
        </Button>
      )}
      {status?.status === 'FAILED' && (
        <Button disabled={busy} onClick={() => void generate()}>
          Reintentar PDF
        </Button>
      )}
      {status?.status === 'READY' && (
        <Button tone="primary" disabled={busy} onClick={() => void download()}>
          {unavailable
            ? 'Volver a intentar descarga'
            : 'Descargar PDF · revisión ' + revisionNumber}
        </Button>
      )}
    </div>
  );
}
