# Spec: Frontend Merchant — TransactionsList & RefundModal

**ID:** SPEC-FE-MERCHANT-001
**Área:** front-merchant
**Status:** Draft

---

## 1. Visão Geral

Implementa o dashboard do merchant (MERCHANT_OWNER): listagem paginada de transações com filtros de status e data, visualização de detalhes da transação, e modal de estorno (total e parcial) com confirmação em duas etapas. Consome a API do payment-service (`/api/v1/transactions/*`) e depende do `IApiClient`, `IAuthContext` e componentes UI (`Button`, `Input`, `Spinner`, `ErrorMessage`) da área front-shared.

---

## 2. User Stories

### P1: TransactionsList — Listagem de Transações ⭐ MVP

**User Story:** As a merchant owner, I want to view a paginated list of my transactions so that I can monitor sales and identify which ones need refunds.

**Acceptance Criteria:**
1. WHEN merchant navigates to `/merchant/transactions` THEN page SHALL fetch `GET /api/v1/transactions?page=0&size=20&sort=createdAt,desc` with `Authorization: Bearer <token>` and `X-Merchant-Id: <merchantId>` headers
2. WHEN data loads successfully THEN page SHALL render a table/card list with columns: transactionId (truncated to first 12 chars + "…"), amount (formatted as BRL `R$ 1.234,56`), status badge (colored), card brand + last 4 digits, customer ID (UUID truncated), and date (ISO → `dd/MM/yyyy HH:mm`)
3. Status badges SHALL use colors: APPROVED=green (`text-green-700 bg-green-100`), DECLINED=red (`text-red-700 bg-red-100`), SUSPECTED_FRAUD=orange (`text-orange-700 bg-orange-100`), FULLY_REFUNDED=purple (`text-purple-700 bg-purple-100`), PARTIALLY_REFUNDED=yellow (`text-yellow-700 bg-yellow-100`)
4. WHEN page > 0 THEN pagination SHALL show "Anterior" button; WHEN page < totalPages-1 THEN pagination SHALL show "Próximo" button; current page SHALL be displayed as "Página {page+1} de {totalPages}"
5. WHEN user clicks "Próximo" THEN page SHALL fetch page+1 and update the list (with loading state)
6. DURING loading the list area SHALL render `<Spinner size="md" />` and disable pagination buttons
7. WHEN the transaction list is empty (`totalElements === 0`) THEN page SHALL display centered message "Nenhuma transação encontrada" with an Info icon
8. WHEN an API error occurs (network failure, 4xx, 5xx) THEN page SHALL render `<ErrorMessage title="Erro ao carregar transações" message={errorMessage} onRetry={refetch} />`
9. WHEN user clicks any transaction row THEN page SHALL navigate to `/merchant/transactions/{transactionId}`

**Independent Test:** Mock `apiClient.get('/api/v1/transactions')` to return `PaginatedResponse<TransactionSummary>` with 2 items. Assert 2 rows rendered with correct BRL formatting, status badge colors, and pagination controls. Mock empty array, assert "Nenhuma transação encontrada". Mock error, assert ErrorMessage with retry.

---

### P1: RefundModal — Modal de Estorno (Total e Parcial) ⭐ MVP

**User Story:** As a merchant owner viewing a transaction detail, I want to refund a transaction (total or partial) with a clear confirmation step so that I can process customer refunds accurately.

**Acceptance Criteria:**
1. WHEN transaction has status `APPROVED` or `PARTIALLY_REFUNDED` THEN detail page SHALL show a primary "Estornar" button; otherwise the button SHALL be hidden
2. WHEN user clicks "Estornar" THEN a modal SHALL open with title "Estornar transação {transactionId}"
3. Modal SHALL show the refundable amount: original transaction amount minus sum of existing refunds, formatted as BRL
4. Modal SHALL have a radio/toggle: "Estorno total" (default selected) and "Estorno parcial"
5. WHEN user selects "Estorno parcial" THEN an amount input SHALL appear with: label "Valor do estorno (R$)", min `R$ 0,01`, max = refundable amount, and input mask for Brazilian currency (centavos)
6. Modal SHALL have a reason dropdown (required) with 4 options: "Solicitação do cliente" (`CUSTOMER_REQUEST`), "Duplicidade" (`DUPLICATE`), "Fraude" (`FRAUD`), "Produto não entregue" (`PRODUCT_NOT_DELIVERED`)
7. WHEN user fills amount (if partial) and selects reason and clicks "Continuar" THEN modal SHALL show a confirmation step summarizing: transaction ID, refund type (total/partial), amount formatted as BRL, reason in Portuguese, and warning "O valor será estornado na fatura do cliente em até 5 dias úteis"
8. WHEN user clicks "Confirmar estorno" THEN modal SHALL:
   - Call `POST /api/v1/transactions/{transactionId}/refund` with body `{ amountInCents?, reason, requestedBy: user.id }` and header `Idempotency-Key: <uuid>`
   - Show loading state on the confirm button (spinner + disabled)
9. WHEN refund succeeds (HTTP 200/201) THEN modal SHALL close, page SHALL show a success toast "Estorno realizado com sucesso", and page SHALL refetch transaction detail to show updated status and refund list
10. WHEN refund fails with `AMOUNT_EXCEEDS_ORIGINAL` THEN modal SHALL show inline error "Valor do estorno excede o valor disponível para estorno"
11. WHEN refund fails with `ALREADY_FULLY_REFUNDED` THEN modal SHALL close, page SHALL refetch and show info toast "Esta transação já foi totalmente estornada"
12. WHEN refund fails with `REFUND_WINDOW_EXPIRED` THEN modal SHALL close and page SHALL show error toast "Prazo de 90 dias para estorno expirou"
13. WHEN refund fails with `MP_GATEWAY_ERROR` THEN modal SHALL show inline error "Erro no gateway de pagamento. Tente novamente." with a retry button
14. WHEN user clicks "Cancelar" at any step THEN modal SHALL close with no side effects
15. PCI/LGPD: the modal SHALL NOT log refund amount, reason, or transactionId to console or external services

**Independent Test:** Mock `apiClient.post('/api/v1/transactions/{id}/refund')` to resolve successfully. Open modal on APPROVED transaction, select total refund, choose reason, confirm, assert toast and refetch. Repeat with partial refund, assert amount validation. Mock MP_GATEWAY_ERROR, assert inline error with retry.

---

## 3. Interface Types (TypeScript)

```typescript
// ── Transaction Types ──────────────────────────────────

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
  createdAt: string;          // ISO 8601
}

export interface RefundSummary {
  refundId: string;
  amountInCents: number;
  reason: RefundReason;
  status: 'COMPLETED';
  createdAt: string;          // ISO 8601
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

// ── Refund Types ───────────────────────────────────────

export interface RefundRequest {
  amountInCents?: number;    // optional → total refund if absent
  reason: RefundReason;
  requestedBy: string;       // UUID of the merchant user
  idempotencyKey: string;    // UUID
}

export interface RefundResponse {
  refundId: string;
  transactionId: string;
  amountRefundedInCents: number;
  fullRefund: boolean;
  status: 'COMPLETED';
  processingTimeMs: number;
}

// ── Refund Modal Types ─────────────────────────────────

export type RefundType = 'TOTAL' | 'PARTIAL';

export interface RefundFormData {
  refundType: RefundType;
  amountInCents: number | null;  // null when TOTAL
  reason: RefundReason | null;
}

export interface RefundFormErrors {
  amountInCents?: string;
  reason?: string;
}

// ── Transactions Filter Types ──────────────────────────

export interface TransactionFilters {
  status?: TransactionStatus;
  page: number;
  size: number;
  sort: string;
}

// ── Merchant Service Contract ─────────────────────────

export interface IMerchantService {
  listTransactions(filters?: Partial<TransactionFilters>): Promise<PaginatedResponse<TransactionSummary>>;
  getTransactionDetail(transactionId: string): Promise<TransactionDetail>;
  submitRefund(transactionId: string, request: RefundRequest): Promise<RefundResponse>;
}

// ── Component Props ────────────────────────────────────

export interface TransactionsListProps {
  onSelectTransaction: (transactionId: string) => void;
}

export interface RefundModalProps {
  transaction: TransactionDetail;
  isOpen: boolean;
  onClose: () => void;
  onRefundSuccess: () => void;
}
```

---

## 4. API Endpoints Consumidos

| Endpoint | Método | Headers | Request | Response |
|----------|--------|---------|---------|----------|
| `/api/v1/transactions?page={page}&size={size}&sort={sort}` | GET | `Authorization: Bearer <token>`<br>`X-Merchant-Id: <merchantId>` | — | `PaginatedResponse<TransactionSummary>` |
| `/api/v1/transactions/{transactionId}` | GET | `Authorization: Bearer <token>`<br>`X-Merchant-Id: <merchantId>` | — | `{ data: TransactionDetail, meta: { ... }, errors: [] }` |
| `/api/v1/transactions/{transactionId}/refund` | POST | `Authorization: Bearer <token>`<br>`X-Merchant-Id: <merchantId>`<br>`Idempotency-Key: <uuid>` | `RefundRequest` (body JSON) | `{ data: RefundResponse, meta: { ... }, errors: [] }` |

---

## 5. Mock Contracts

A área front-merchant depende de front-shared (IApiClient, IAuthContext, componentes UI). Abaixo os contratos de mock para cada dependência.

```typescript
// ── Mock: front-shared → IApiClient ────────────────────
// Criar em: src/__mocks__/apiClient.ts

export interface IMockApiClient {
  get: <T>(url: string, options?: Record<string, unknown>) => Promise<T>;
  post: <T>(url: string, body?: unknown, options?: Record<string, unknown>) => Promise<T>;
}

// ── Mock: front-shared → IAuthContext ──────────────────
// Criar em: src/__mocks__/authContext.ts

export interface IMockAuthContext {
  user: { id: string; email: string; role: string; merchantId: string } | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

// ── Mock: front-shared → PaginatedResponse ─────────────
// Para tests, criar dados mockados que sigam o envelope:

export function createMockTransactionSummary(overrides?: Partial<TransactionSummary>): TransactionSummary {
  return {
    transactionId: 'txn_test_001',
    amountInCents: 123456,
    currency: 'BRL',
    status: 'APPROVED',
    cardBrand: 'visa',
    cardLastFour: '4242',
    customerId: '550e8400-e29b-41d4-a716-446655440000',
    createdAt: '2026-05-27T14:00:00Z',
    ...overrides,
  };
}
```

---

## 6. Error Scenarios

| Erro | Código HTTP | Comportamento esperado |
|------|-------------|----------------------|
| Transação não encontrada | `TRANSACTION_NOT_FOUND` (404) | TransactionsList detail: `ErrorMessage` com título "Transação não encontrada", sem retry |
| Merchant sem permissão | `INSUFFICIENT_PERMISSIONS` (403) | TransactionsList: `ErrorMessage` com título "Acesso negado" e link para voltar |
| Transação não estornável | `TRANSACTION_NOT_REFUNDABLE` (422) | RefundModal fecha, toast: "Esta transação não pode ser estornada" |
| Valor excede disponível | `AMOUNT_EXCEEDS_ORIGINAL` (422) | RefundModal exibe erro inline no campo valor |
| Já totalmente estornado | `ALREADY_FULLY_REFUNDED` (422) | RefundModal fecha, página refaz fetch, info toast: "Já totalmente estornada" |
| Janela de estorno expirada | `REFUND_WINDOW_EXPIRED` (422) | RefundModal fecha, error toast: "Prazo de 90 dias para estorno expirou" |
| Gateway MP offline | `MP_GATEWAY_ERROR` (503) | RefundModal exibe erro inline retryável |
| Pedido não encontrado | `ORDER_NOT_FOUND` (404) | Na detail, `ErrorMessage` com título "Pedido não encontrado" |
| Rede indisponível | `NETWORK_ERROR` | TransactionsList/Detail exibe `ErrorMessage` com retry |
| Parâmetros de página inválidos | 400 | TransactionsList usa valores padrão (page=0, size=20) e exibe erro se persistir |
| Rate limit excedido | `RATE_LIMIT_EXCEEDED` (429) | `ErrorMessage` com retry e aviso "Muitas requisições. Aguarde alguns segundos." |

---

## 7. Requirement Traceability

| ID | Story | Status |
|----|-------|--------|
| MERCHANT-01 | P1: TransactionsList — fetch paginado com headers de auth e merchant | Pending |
| MERCHANT-02 | P1: TransactionsList — renderização de tabela com BRL, status badge, card info | Pending |
| MERCHANT-03 | P1: TransactionsList — cores de status badge por status | Pending |
| MERCHANT-04 | P1: TransactionsList — paginação (anterior/próximo/indicador de página) | Pending |
| MERCHANT-05 | P1: TransactionsList — loading state com Spinner | Pending |
| MERCHANT-06 | P1: TransactionsList — empty state "Nenhuma transação encontrada" | Pending |
| MERCHANT-07 | P1: TransactionsList — error state com ErrorMessage e retry | Pending |
| MERCHANT-08 | P1: TransactionsList — clique na linha navega para detail | Pending |
| MERCHANT-09 | P1: RefundModal — botão "Estornar" visível apenas para APPROVED/PARTIALLY_REFUNDED | Pending |
| MERCHANT-10 | P1: RefundModal — radio total vs parcial com input de valor | Pending |
| MERCHANT-11 | P1: RefundModal — dropdown de motivo com labels em português | Pending |
| MERCHANT-12 | P1: RefundModal — tela de confirmação com resumo | Pending |
| MERCHANT-13 | P1: RefundModal — submit com Idempotency-Key e loading state | Pending |
| MERCHANT-14 | P1: RefundModal — sucesso → toast + refetch transaction | Pending |
| MERCHANT-15 | P1: RefundModal — erro AMOUNT_EXCEEDS_ORIGINAL inline | Pending |
| MERCHANT-16 | P1: RefundModal — erro ALREADY_FULLY_REFUNDED fecha modal + refetch | Pending |
| MERCHANT-17 | P1: RefundModal — erro REFUND_WINDOW_EXPIRED toast | Pending |
| MERCHANT-18 | P1: RefundModal — erro MP_GATEWAY_ERROR retry inline | Pending |
| MERCHANT-19 | P1: RefundModal — cancelar fecha modal sem efeito colateral | Pending |
| MERCHANT-20 | P1: RefundModal — validação de valor mínimo (R$ 0,01) | Pending |
| MERCHANT-21 | P1: RefundModal — validação de valor máximo (refundable amount) | Pending |
| MERCHANT-22 | P1: RefundModal — PCI/LGPD: não logar dados do estorno | Pending |
