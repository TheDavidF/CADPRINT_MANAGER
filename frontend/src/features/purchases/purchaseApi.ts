import { ApiError, request } from '../../services/api';

export const purchaseStatuses = [
  'BORRADOR',
  'PENDIENTE',
  'RECIBIDA',
  'CANCELADA',
] as const;
export type PurchaseStatus = (typeof purchaseStatuses)[number];

export type PurchaseSummary = {
  id: number;
  supplierId: number | null;
  supplierName: string | null;
  purchaseDate: string;
  documentNumber: string | null;
  status: PurchaseStatus;
  currency: string;
  subtotal: number;
  total: number;
  notes: string | null;
  createdBy: string;
  createdAt: string;
  version: number;
};

export type PurchaseLine = {
  id: number;
  materialId: number;
  quantity: number;
  purchaseUnit: string;
  unitPrice: number;
  subtotal: number;
  inventoryUnit: string | null;
  conversionFactor: number | null;
  inventoryQuantity: number | null;
  inventoryUnitCost: number | null;
};

export type Receipt = {
  id: number;
  purchaseId: number;
  receivedAt: string;
  receivedBy: string;
  movements: {
    id: number;
    purchaseLineId: number;
    materialId: number;
    type: string;
    quantity: number;
    beforeStock: number;
    afterStock: number;
    createdAt: string;
    username: string;
    reason: string;
  }[];
};

export type PurchaseDetail = {
  purchase: PurchaseSummary;
  lines: PurchaseLine[];
  receipt: Receipt | null;
};

export type PurchaseEvent = {
  id: number;
  eventType: string;
  previousStatus: PurchaseStatus | null;
  newStatus: PurchaseStatus | null;
  username: string;
  createdAt: string;
  reason: string | null;
};

export type PurchasePage = {
  content: PurchaseSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type Filters = {
  status: string;
  supplierId: string;
  from: string;
  to: string;
  documentNumber: string;
};

export type DraftPayload = {
  supplierId: number | null;
  purchaseDate: string;
  documentNumber: string | null;
  notes: string | null;
  lines: {
    materialId: number;
    quantity: string;
    purchaseUnit: string;
    unitPrice: string;
  }[];
};

export type PurchaseAction =
  'confirm' | 'receipt' | 'cancel' | 'return-to-draft';

const base = '/purchases';

export function listPurchases(
  token: string,
  filters: Filters,
  page: number,
  signal: AbortSignal,
) {
  const query = new URLSearchParams({ page: String(page) });
  for (const [name, value] of Object.entries(filters)) {
    if (value.trim()) query.set(name, value.trim());
  }
  return request<PurchasePage>(token, base + '?' + query, { signal });
}

export function getPurchase(token: string, id: number) {
  return request<PurchaseDetail>(token, base + '/' + id);
}

export function getPurchaseEvents(
  token: string,
  id: number,
  signal: AbortSignal,
) {
  return request<PurchaseEvent[]>(token, base + '/' + id + '/events', {
    signal,
  });
}

export function savePurchase(
  token: string,
  input: DraftPayload,
  authorizeDuplicate: boolean,
  purchase?: PurchaseSummary,
) {
  return request<PurchaseDetail>(
    token,
    purchase ? base + '/' + purchase.id : base,
    {
      method: purchase ? 'PUT' : 'POST',
      body: JSON.stringify({
        ...input,
        version: purchase?.version,
        authorizeDuplicate,
      }),
    },
  );
}

export function changePurchase(
  token: string,
  id: number,
  action: Exclude<PurchaseAction, 'receipt'>,
  payload:
    { version: number; authorizeDuplicate: boolean } | { reason: string },
) {
  return request<PurchaseDetail>(token, base + '/' + id + '/' + action, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function receivePurchase(token: string, id: number) {
  return request<Receipt>(token, base + '/' + id + '/receipt', {
    method: 'PUT',
  });
}

export function duplicateDocument(error: unknown) {
  return (
    error instanceof ApiError &&
    error.status === 409 &&
    error.message.startsWith('Ya existe una compra con ese documento')
  );
}

export function purchaseError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401)
      return 'La sesión no está autorizada. Vuelve a iniciar sesión.';
    if (error.status === 403)
      return 'Tu usuario no tiene permiso para realizar esta operación.';
    return error.message;
  }
  return 'No fue posible comunicarse con el servidor.';
}

export const money = new Intl.NumberFormat('es-GT', {
  style: 'currency',
  currency: 'GTQ',
});
export const dateTime = new Intl.DateTimeFormat('es-GT', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'America/Guatemala',
});
