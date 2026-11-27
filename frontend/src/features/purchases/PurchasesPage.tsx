import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../services/api';
import type { Material, Supplier } from '../../services/types';
import { PurchaseActionDialog } from './PurchaseActionDialog';
import { PurchaseDetails } from './PurchaseDetails';
import { PurchaseForm } from './PurchaseForm';
import { emptyFilters, PurchaseList } from './PurchaseList';
import {
  changePurchase,
  duplicateDocument,
  getPurchase,
  listPurchases,
  purchaseError,
  receivePurchase,
  savePurchase,
  type DraftPayload,
  type Filters,
  type PurchaseAction,
  type PurchaseDetail,
  type PurchasePage,
} from './purchaseApi';

export function PurchasesPage({
  token,
  roles,
  materials,
  suppliers,
  refreshInventory,
}: {
  token: string;
  roles: string[];
  materials: Material[];
  suppliers: Supplier[];
  refreshInventory: () => Promise<void>;
}) {
  const admin = roles.includes('ROLE_ADMIN');
  const [filters, setFilters] = useState<Filters>(emptyFilters);
  const [page, setPage] = useState(0);
  const [listRevision, setListRevision] = useState(0);
  const [results, setResults] = useState<PurchasePage | null>(null);
  const [listLoading, setListLoading] = useState(true);
  const [listError, setListError] = useState('');
  const [detail, setDetail] = useState<PurchaseDetail | null>(null);
  const [editor, setEditor] = useState<'new' | 'edit' | null>(null);
  const [editorRevision, setEditorRevision] = useState(0);
  const [action, setAction] = useState<PurchaseAction | null>(null);
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [duplicate, setDuplicate] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [receiptUncertain, setReceiptUncertain] = useState(false);
  const [inventoryRefreshFailed, setInventoryRefreshFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    void listPurchases(token, filters, page, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return;
        if (page > 0 && page >= result.totalPages) {
          setPage(Math.max(0, result.totalPages - 1));
          return;
        }
        setResults(result);
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) {
          setListError(purchaseError(reason));
          setResults(null);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setListLoading(false);
      });
    return () => controller.abort();
  }, [token, filters, page, listRevision]);

  function clearFeedback() {
    setError('');
    setNotice('');
    setDuplicate(false);
    setConflict(false);
  }

  function refreshList() {
    setListLoading(true);
    setListError('');
    setListRevision((revision) => revision + 1);
  }

  async function run(operation: () => Promise<void>) {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    clearFeedback();
    try {
      await operation();
    } catch (reason) {
      const isDuplicate = duplicateDocument(reason);
      setDuplicate(isDuplicate);
      setConflict(
        reason instanceof ApiError && reason.status === 409 && !isDuplicate,
      );
      setError(purchaseError(reason));
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }

  async function updateInventory() {
    try {
      await refreshInventory();
      setInventoryRefreshFailed(false);
    } catch {
      setInventoryRefreshFailed(true);
    }
  }

  function acceptDetail(next: PurchaseDetail) {
    setDetail(next);
    setReceiptUncertain(false);
  }

  function open(id: number) {
    void run(async () => acceptDetail(await getPurchase(token, id)));
  }

  function reload() {
    if (!detail) return;
    const id = detail.purchase.id;
    void run(async () => {
      const next = await getPurchase(token, id);
      acceptDetail(next);
      setAction(null);
      if (next.purchase.status !== 'BORRADOR') setEditor(null);
      setEditorRevision((revision) => revision + 1);
      refreshList();
      if (next.receipt) await updateInventory();
    });
  }

  function save(input: DraftPayload, authorizeDuplicate: boolean) {
    void run(async () => {
      const next = await savePurchase(
        token,
        input,
        authorizeDuplicate,
        editor === 'edit' ? detail?.purchase : undefined,
      );
      acceptDetail(next);
      setEditor(null);
      setNotice('Borrador guardado.');
      refreshList();
    });
  }

  async function receive(current: PurchaseDetail) {
    const id = current.purchase.id;
    try {
      const receipt = await receivePurchase(token, id);
      acceptDetail({
        ...current,
        purchase: { ...current.purchase, status: 'RECIBIDA' },
        receipt,
      });
      try {
        acceptDetail(await getPurchase(token, id));
      } catch {
        setError(
          'La recepción está registrada. Recarga el detalle para confirmar los datos de la compra.',
        );
      }
    } catch (reason) {
      if (reason instanceof ApiError && reason.status < 500) throw reason;
      setReceiptUncertain(true);
      setAction(null);
      try {
        const next = await getPurchase(token, id);
        acceptDetail(next);
        if (!next.receipt) {
          setError(
            'No se pudo completar la solicitud de recepción. Se consultó el estado actual de la compra.',
          );
          refreshList();
          return;
        }
      } catch {
        setError(
          'No se pudo comprobar la recepción. Comprueba el estado antes de volver a intentar.',
        );
        return;
      }
    }
    setAction(null);
    setNotice('Recepción registrada.');
    refreshList();
    await updateInventory();
  }

  function act(reason: string, authorizeDuplicate: boolean) {
    if (!detail || !action) return;
    const current = detail;
    const selected = action;
    void run(async () => {
      if (selected === 'receipt') {
        if (!receiptUncertain) await receive(current);
        return;
      }
      const payload =
        selected === 'confirm'
          ? { version: current.purchase.version, authorizeDuplicate }
          : { reason };
      acceptDetail(
        await changePurchase(token, current.purchase.id, selected, payload),
      );
      setAction(null);
      setNotice('Compra actualizada.');
      refreshList();
    });
  }

  return (
    <>
      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}
      {inventoryRefreshFailed && (
        <div className="notice">
          <span role="alert">
            La recepción está registrada. Falló la actualización de la vista de
            inventario.
          </span>
          <button
            type="button"
            disabled={busy}
            onClick={() => void run(updateInventory)}
          >
            Actualizar inventario
          </button>
        </div>
      )}
      {error && !action && (
        <div className="notice">
          <span role="alert">{error}</span>
          {editor === 'edit' && conflict && (
            <button type="button" disabled={busy} onClick={reload}>
              Recargar y descartar cambios
            </button>
          )}
        </div>
      )}
      {editor ? (
        <PurchaseForm
          key={editorRevision}
          detail={editor === 'edit' ? (detail ?? undefined) : undefined}
          materials={materials}
          suppliers={suppliers}
          busy={busy}
          canAuthorize={admin && duplicate}
          onSave={save}
          onCancel={() => {
            clearFeedback();
            setEditor(null);
          }}
        />
      ) : detail ? (
        <PurchaseDetails
          key={detail.purchase.id}
          detail={detail}
          materials={materials}
          token={token}
          admin={admin}
          busy={busy}
          receiptUncertain={receiptUncertain}
          onEdit={() => {
            clearFeedback();
            setEditor('edit');
          }}
          onAction={(selected) => {
            clearFeedback();
            setAction(selected);
          }}
          onBack={() => {
            clearFeedback();
            setDetail(null);
          }}
          onReload={reload}
        />
      ) : (
        <PurchaseList
          suppliers={suppliers}
          appliedFilters={filters}
          result={results}
          loading={listLoading}
          error={listError}
          opening={busy}
          onFilter={(next) => {
            setFilters(next);
            setPage(0);
            refreshList();
          }}
          onPage={(next) => {
            setPage(next);
            refreshList();
          }}
          onOpen={open}
          onNew={() => {
            clearFeedback();
            setEditor('new');
          }}
          onRetry={refreshList}
        />
      )}
      {action && detail && (
        <PurchaseActionDialog
          action={action}
          detail={detail}
          materials={materials}
          busy={busy}
          error={error}
          canAuthorize={admin && duplicate && action === 'confirm'}
          versionConflict={conflict && action === 'confirm'}
          onAccept={act}
          onClose={() => {
            clearFeedback();
            setAction(null);
          }}
          onReload={reload}
        />
      )}
    </>
  );
}
