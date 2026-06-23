export type TransactionStatus =
  | 'APPROVED'
  | 'DECLINED'
  | 'SUSPECTED_FRAUD'
  | 'FULLY_REFUNDED'
  | 'PARTIALLY_REFUNDED';

export type RefundReason =
  | 'CUSTOMER_REQUEST'
  | 'DUPLICATE'
  | 'FRAUD'
  | 'PRODUCT_NOT_DELIVERED';

export const REFUND_REASON_LABELS: Record<RefundReason, string> = {
  CUSTOMER_REQUEST: 'Solicitação do cliente',
  DUPLICATE: 'Duplicidade',
  FRAUD: 'Fraude',
  PRODUCT_NOT_DELIVERED: 'Produto não entregue',
};

export const STATUS_BADGE_CLASSES: Record<TransactionStatus, string> = {
  APPROVED: 'text-green-700 bg-green-100',
  DECLINED: 'text-red-700 bg-red-100',
  SUSPECTED_FRAUD: 'text-orange-700 bg-orange-100',
  FULLY_REFUNDED: 'text-purple-700 bg-purple-100',
  PARTIALLY_REFUNDED: 'text-yellow-700 bg-yellow-100',
};

export const STATUS_LABELS: Record<TransactionStatus, string> = {
  APPROVED: 'Aprovado',
  DECLINED: 'Recusado',
  SUSPECTED_FRAUD: 'Suspeita de fraude',
  FULLY_REFUNDED: 'Totalmente estornado',
  PARTIALLY_REFUNDED: 'Parcialmente estornado',
};

export interface TransactionSummary {
  transactionId: string;
  amountInCents: number;
  currency: 'BRL';
  status: TransactionStatus;
  cardBrand: string;
  cardLastFour: string;
  customerId: string;
  createdAt: string;
}

export interface RefundSummary {
  refundId: string;
  amountInCents: number;
  reason: RefundReason;
  status: 'COMPLETED';
  createdAt: string;
}

export interface TransactionDetail {
  transactionId: string;
  mpPaymentId: number;
  status: TransactionStatus;
  amountInCents: number;
  currency: 'BRL';
  cardBrand: string;
  cardLastFour: string;
  orderId: string;
  customerId: string;
  merchantId: string;
  createdAt: string;
  updatedAt: string;
  refunds: RefundSummary[];
  processingTimeMs: number;
}

export interface RefundRequest {
  amountInCents?: number;
  reason: RefundReason;
  requestedBy: string;
  idempotencyKey: string;
}

export interface RefundResponse {
  refundId: string;
  transactionId: string;
  amountRefundedInCents: number;
  fullRefund: boolean;
  status: 'COMPLETED';
  processingTimeMs: number;
}

export type RefundType = 'TOTAL' | 'PARTIAL';

export interface RefundFormData {
  refundType: RefundType;
  amountInCents: number | null;
  reason: RefundReason | null;
}

export interface RefundFormErrors {
  amountInCents?: string;
  reason?: string;
}

export interface TransactionFilters {
  status?: TransactionStatus;
  page: number;
  size: number;
  sort: string;
}

export interface PaginatedResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
}

export interface ApiErrorResponse {
  code: string;
  message: string;
  retryable: boolean;
}
