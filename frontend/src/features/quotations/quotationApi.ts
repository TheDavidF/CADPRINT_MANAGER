import { request, requestFile } from '../../services/api';
import { body, exactRequest } from '../../services/commercial';

export const statuses = [
  'BORRADOR',
  'EMITIDA',
  'ACEPTADA',
  'RECHAZADA',
  'VENCIDA',
  'CANCELADA',
] as const;
export type Status = (typeof statuses)[number];
export type Discount = {
  type: 'NONE' | 'FIXED' | 'PERCENTAGE';
  value: string | null;
};
export type Recipient = {
  name: string | null;
  contact: string | null;
  email: string | null;
  phone: string | null;
  address: string | null;
};
export type Totals = {
  grossAmount: string;
  lineDiscountTotal: string;
  subtotal: string;
  generalDiscount: Discount;
  generalDiscountAmount: string;
  shippingAmount: string;
  totalAmount: string;
};
export type CommercialLine = {
  position: number;
  sku: string | null;
  name: string;
  description: string | null;
  quantity: number;
  unitPrice: string;
  discount: Discount;
  grossAmount: string;
  discountAmount: string;
  netAmount: string;
};
type InternalLine = {
  commercial: CommercialLine;
  type: 'CATALOG' | 'CUSTOM';
  productId: number | null;
  variantId: number | null;
  productionCalculationId: number | null;
  belowHistoricalCost: boolean;
};
type Issuer = Omit<Recipient, 'contact'>;
export type CommercialView = {
  quotationNumber: string;
  revisionNumber: number;
  currency: string;
  recipient: Recipient;
  issuer: Issuer;
  issuedAt: string;
  validUntil: string;
  commercialTerms: string | null;
  totals: Totals;
  lines: CommercialLine[];
};
export type CustomerOrder = {
  id: number;
  quotationId: number;
  createdAt: string;
  createdBy: string;
  quotation: CommercialView;
  productionStartedAt: string | null;
  productionStartedBy: string | null;
};
export type Summary = {
  quotationId: number;
  quotationNumber: string | null;
  version: number;
  revisionNumber: number;
  status: Status;
  recipientName: string | null;
  issuedAt: string | null;
  validUntil: string | null;
  replacedAt: string | null;
  totalAmount: string;
};
export type Detail = {
  customerId?: number | null;
  revision: Summary;
  sourceRevisionNumber: number | null;
  replacementRevisionNumber: number | null;
  currency: string;
  recipient: Recipient;
  issuer: Issuer;
  commercialTerms: string | null;
  notes: string | null;
  totals: Totals;
  lines: InternalLine[];
};
export type Event = {
  revisionNumber: number;
  type: string;
  previousStatus: Status | null;
  newStatus: Status | null;
  actor: string;
  occurredAt: string;
  reason: string | null;
  details: string | null;
};
export type LineInput = {
  type: InternalLine['type'];
  productId: number | null;
  variantId: number | null;
  productionCalculationId: number | null;
  name: string | null;
  description: string | null;
  quantity: string;
  unitPrice: string | null;
  discount: Discount;
};
export type Draft = {
  customerId?: number | null;
  recipient: Recipient;
  commercialTerms: string;
  notes: string;
  validUntil: string | null;
  generalDiscount: Discount;
  shippingAmount: string | null;
  lines: LineInput[];
};
export type QuotationPage = {
  items: Summary[];
  page: number;
  totalPages: number;
  totalElements: number;
};
export type DocumentStatus = {
  status: 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED';
  templateVersion: string;
  sha256: string | null;
  sizeBytes: number | null;
  generatedAt: string | null;
  errorCode: string | null;
};
export type Action =
  'emit' | 'revise' | 'accept' | 'reject' | 'expire' | 'cancel';
const fields = [
  'grossAmount',
  'lineDiscountTotal',
  'subtotal',
  'generalDiscountAmount',
  'shippingAmount',
  'totalAmount',
  'unitPrice',
  'discountAmount',
  'netAmount',
  'value',
];
const path = (id: number) => '/quotations/' + id;
const revision = (id: number, number: number) =>
  path(id) + '/revisions/' + number;
const document = (id: number, number: number) =>
  revision(id, number) + '/document';
const read = <T>(token: string, url: string, options?: RequestInit) =>
  exactRequest<T>(token, url, fields, options);
export const quotationApi = {
  list: (
    token: string,
    page: number,
    filters: { status: string; recipient: string; number: string },
    signal?: AbortSignal,
  ) =>
    read<QuotationPage>(
      token,
      '/quotations?' + new URLSearchParams({ ...filters, page: String(page) }),
      { signal },
    ),
  detail: (token: string, id: number, number?: number, signal?: AbortSignal) =>
    read<Detail>(token, number ? revision(id, number) : path(id), { signal }),
  revisions: (token: string, id: number, signal?: AbortSignal) =>
    read<Summary[]>(token, path(id) + '/revisions', { signal }),
  events: (token: string, id: number, signal?: AbortSignal) =>
    read<Event[]>(token, path(id) + '/events', { signal }),
  create: (token: string, draft: Draft) =>
    read<Detail>(token, '/quotations', body(draft)),
  edit: (token: string, current: Summary, draft: Draft) =>
    read<Detail>(
      token,
      revision(current.quotationId, current.revisionNumber) + '/draft',
      body({ version: current.version, draft }, 'PUT'),
    ),
  action: (token: string, current: Summary, action: Action, reason: string) =>
    read<Detail>(
      token,
      revision(current.quotationId, current.revisionNumber) + '/' + action,
      body(
        action === 'emit' || action === 'revise'
          ? { version: current.version }
          : { reason: reason || null },
      ),
    ),
  commercial: (token: string, current: Summary, signal?: AbortSignal) =>
    read<CommercialView>(
      token,
      revision(current.quotationId, current.revisionNumber) + '/commercial',
      { signal },
    ),
  order: (
    token: string,
    current: Summary,
    signal?: AbortSignal,
    create = false,
  ) =>
    read<CustomerOrder>(
      token,
      revision(current.quotationId, current.revisionNumber) + '/order',
      {
        signal,
        method: create ? 'POST' : 'GET',
      },
    ),
  startProduction: (token: string, current: Summary) =>
    read<CustomerOrder>(
      token,
      revision(current.quotationId, current.revisionNumber) +
        '/order/production/start',
      { method: 'POST' },
    ),
  document: (
    token: string,
    current: Summary,
    signal?: AbortSignal,
    create = false,
  ) =>
    request<DocumentStatus>(
      token,
      document(current.quotationId, current.revisionNumber),
      { signal, method: create ? 'POST' : 'GET' },
    ),
  download: (token: string, current: Summary, signal?: AbortSignal) =>
    requestFile(
      token,
      document(current.quotationId, current.revisionNumber) + '/download',
      { signal },
    ),
};
