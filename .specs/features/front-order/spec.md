# Spec: Frontend Order — Create, History & Detail

**ID:** SPEC-FE-ORDER-001
**Área:** front-order
**Status:** Draft

---

## 1. Visão Geral

Implementa o fluxo de gerenciamento de pedidos do cliente: criação de pedidos com seleção de merchant e itens, listagem paginada do histórico com filtro por status, e visualização de detalhes completos includindo cancelamento de pedidos pendentes. Consome a API do order-service (`/api/v1/orders/*`) e depende do front-shared (API Client, AuthContext, componentes UI) e do front-auth (tipos de usuário autenticado).

---

## 2. User Stories

### P1: CreateOrder — Criar Pedido ⭐ MVP

**User Story:** As a customer, I want to select a merchant, add items to my order, and create it so that I can proceed to checkout and pay.

**Acceptance Criteria:**
1. WHEN the page loads THEN form SHALL display a merchant dropdown/selector and an empty items list with an "Adicionar item" button
2. WHEN user selects a merchant THEN system SHALL validate that the merchant exists (call `GET /api/v1/merchants/{id}` through front-shared resolver or direct validation)
3. WHEN user clicks "Adicionar item" THEN form SHALL render a new row with fields: productId, description (text), quantity (number, min 1, max 999), unitPriceInCents (number, min 1)
4. WHEN user fills item fields THEN form SHALL compute the line subtotal (`quantity × unitPriceInCents`) in real-time and display it
5. WHEN there are 2+ items THEN form SHALL display the running total (`sum of all subtotals`) in cents formatted as BRL currency
6. WHEN user clicks "Remover" on an item row THEN form SHALL remove that item and recalculate the total
7. WHEN user submits a valid form (merchant selected, at least 1 item) THEN system SHALL call `POST /api/v1/orders` with `Authorization: Bearer <token>`, `X-Merchant-Id`, and an auto-generated `Idempotency-Key` (UUID v4)
8. WHEN create succeeds (HTTP 201) THEN system SHALL redirect to `/orders/{orderId}` with success feedback
9. WHEN idempotencyKey matches an existing order (HTTP 200/409 DUPLICATE_ORDER) THEN system SHALL redirect to the existing order detail page
10. WHEN user has no items (submit with empty list) THEN form SHALL disable submit and show error "Adicione pelo menos um item ao pedido"
11. WHEN total exceeds R$ 9.999,99 (TOTAL_EXCEEDS_LIMIT) THEN system SHALL show error "Valor total do pedido excede o limite de R$ 9.999,99"
12. WHEN merchant is invalid (MERCHANT_NOT_FOUND) THEN system SHALL show error "Merchant não encontrado"
13. WHEN item price is invalid (INVALID_ITEM_PRICE) THEN system SHALL show inline error "Preço inválido para o item {description}"
14. WHEN item quantity is invalid (INVALID_QUANTITY) THEN system SHALL show inline error "Quantidade inválida para o item {description}"

**Independent Test:** Mock `apiClient.post('/api/v1/orders')` returning a valid order. Fill merchant field and 2 items, submit, assert redirect to `/orders/{orderId}`. Assert all error responses render the correct error message.

---

### P1: OrderHistory — Histórico de Pedidos ⭐ MVP

**User Story:** As a customer, I want to see a paginated list of my past orders filtered by status so that I can track my purchases.

**Acceptance Criteria:**
1. WHEN the page loads THEN system SHALL call `GET /api/v1/orders?page=0&size=20&sort=createdAt,desc` and display results in a table/card list
2. EACH order row SHALL display: orderId (truncated), status as a colored badge (PENDING=gray, PROCESSING=blue, PAID=green, CANCELLED=red, REFUNDED=orange, PARTIALLY_REFUNDED=yellow), totalInCents formatted as BRL, and createdAt formatted as dd/MM/yyyy HH:mm
3. EACH order row SHALL be clickable and navigate to `/orders/{orderId}`
4. WHEN there are more than 20 orders THEN pagination controls SHALL appear at the bottom (previous/next buttons, current page indicator)
5. WHEN user changes page THEN system SHALL call the API with the new page parameter and update the list
6. WHEN user selects a status filter (dropdown with options "Todos", "PENDING", "PROCESSING", "PAID", "CANCELLED", "REFUNDED") THEN system SHALL call `GET /api/v1/orders?status={selected}&page=0&size=20` and reset to page 0
7. WHEN the list is empty (no orders match filters) THEN system SHALL display an empty state: "Nenhum pedido encontrado"
8. WHEN data is loading THEN system SHALL render `<Spinner />` instead of the list
9. WHEN API returns an error THEN system SHALL render `<ErrorMessage />` with retry button

**Independent Test:** Mock `apiClient.get('/api/v1/orders')` returning a page with 2 orders and totalPages=1. Assert 2 rows rendered with correct status badge colors and total formatting. Apply status filter, assert API called with filter param.

---

### P1: OrderDetail — Detalhe do Pedido ⭐ MVP

**User Story:** As a customer, I want to view the full details of a specific order so that I can see items, total, payment status, and optionally cancel if it's still pending.

**Acceptance Criteria:**
1. WHEN the page loads with an orderId in the URL THEN system SHALL call `GET /api/v1/orders/{orderId}` and display the full order detail
2. The detail view SHALL display: orderId, status badge, totalInCents formatted as BRL, createdAt, updatedAt, expiresAt (if PENDING)
3. Items SHALL be displayed in a table with columns: productId, description, quantity, unitPriceInCents, subtotalInCents — with each subtotal computed and displayed
4. IF `transactionId` is present (order is PAID) THEN system SHALL display a clickable link/button to the transaction detail (route `/transactions/{transactionId}`)
5. IF order status is PENDING THEN system SHALL render a "Cancelar pedido" button with a confirmation modal/dialog
6. WHEN user clicks "Cancelar pedido" and confirms THEN system SHALL call `DELETE /api/v1/orders/{orderId}` and update the display to show status CANCELLED
7. WHEN cancel fails because order is not PENDING (422 ORDER_CANNOT_BE_CANCELLED) THEN system SHALL show error "Este pedido não pode mais ser cancelado"
8. WHEN cancel fails because user lacks permission (403 INSUFFICIENT_PERMISSIONS) THEN system SHALL show error "Você não tem permissão para cancelar este pedido"
9. WHEN order does not exist (404 ORDER_NOT_FOUND) THEN system SHALL render `<ErrorMessage />` with title "Pedido não encontrado" and a link to the order history page
10. WHEN data is loading THEN system SHALL render `<Spinner />`

**Independent Test:** Mock `apiClient.get('/api/v1/orders/{orderId}')` returning a PAID order with transactionId. Assert transaction link is visible and items table is rendered. Mock a PENDING order, assert "Cancelar pedido" button is visible.

---

## 3. Interface Types (TypeScript)

```typescript
// ─── Domain Types ───────────────────────────────────────

export type OrderStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'PAID'
  | 'CANCELLED'
  | 'REFUNDED'
  | 'PARTIALLY_REFUNDED';

export interface OrderItem {
  productId: string;
  description: string;
  quantity: number;
  unitPriceInCents: number;
}

export interface OrderItemDetail extends OrderItem {
  subtotalInCents: number;
}

// ─── API Request DTOs ──────────────────────────────────

export interface CreateOrderRequest {
  customerId: string;
  merchantId: string;
  items: OrderItem[];
  idempotencyKey: string;
}

// ─── API Response DTOs ─────────────────────────────────

export interface CreateOrderResponse {
  orderId: string;
  status: OrderStatus;
  totalInCents: number;
  items: OrderItemDetail[];
  expiresAt: string;
  createdAt: string;
}

export interface OrderDetail {
  orderId: string;
  customerId: string;
  merchantId: string;
  status: OrderStatus;
  totalInCents: number;
  items: OrderItemDetail[];
  transactionId?: string;
  createdAt: string;
  updatedAt: string;
  expiresAt?: string;
}

export interface OrderSummary {
  orderId: string;
  status: OrderStatus;
  totalInCents: number;
  createdAt: string;
}

// ─── Frontend State Types ──────────────────────────────

export interface OrderFormItem {
  id: string;          // local uuid for row key
  productId: string;
  description: string;
  quantity: number;
  unitPriceInCents: number;
}

export interface OrderFilters {
  status?: OrderStatus;
}
```

## 4. API Endpoints Consumidos

| Endpoint | Método | Headers | Request | Response |
|----------|--------|---------|---------|----------|
| `/api/v1/orders` | POST | `Authorization: Bearer <token>`, `X-Merchant-Id`, `Idempotency-Key` | `CreateOrderRequest` | `{data: CreateOrderResponse}` |
| `/api/v1/orders/{orderId}` | GET | `Authorization: Bearer <token>` | — | `{data: OrderDetail}` |
| `/api/v1/orders` | GET | `Authorization: Bearer <token>` | `?status=&page=0&size=20&sort=createdAt,desc` | `PaginatedResponse<OrderSummary>` |
| `/api/v1/orders/{orderId}` | DELETE | `Authorization: Bearer <token>` | — | `{data: null}` |

## 5. Mock Contracts

```typescript
// Dependência: front-shared → IApiClient
// Mock para isolar chamadas HTTP nos testes de front-order:

export interface IMockApiClient {
  get: <T>(url: string, options?: RequestOptions) => Promise<T>;
  post: <T>(url: string, body?: unknown, options?: RequestOptions) => Promise<T>;
  delete: <T>(url: string, options?: RequestOptions) => Promise<T>;
}

// Dependência: front-shared → IAuthContext
// Mock para isolar estado de autenticação nos testes de front-order:

export interface IMockAuthContext {
  user: { id: string; email: string; role: 'CUSTOMER' | 'MERCHANT_OWNER'; merchantId?: string } | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

// Ambos os mocks seguem as interfaces reduzidas do §3 da spec front-shared.
// Injeção via props ou React Context — nunca import direto.
```

## 6. Error Scenarios

| Erro | Código HTTP | Comportamento esperado |
|------|------------|----------------------|
| DUPLICATE_ORDER | 409 | Redirecionar para pedido existente; se 200, mostrar "Pedido já processado" |
| EMPTY_ORDER | 400 | Mostrar erro no formulário: "Adicione pelo menos um item ao pedido" |
| INVALID_ITEM_PRICE | 400 | Mostrar inline: "Preço inválido para o item {description}" |
| INVALID_QUANTITY | 400 | Mostrar inline: "Quantidade inválida para o item {description}" |
| MERCHANT_NOT_FOUND | 404 | Mostrar: "Merchant não encontrado. Verifique o CNPJ ou selecione outro." |
| TOTAL_EXCEEDS_LIMIT | 400 | Mostrar: "Valor total do pedido excede o limite de R$ 9.999,99" |
| ORDER_NOT_FOUND | 404 | Mostrar ErrorMessage: "Pedido não encontrado" com link para histórico |
| ORDER_CANNOT_BE_CANCELLED | 422 | Mostrar: "Este pedido não pode mais ser cancelado" |
| INSUFFICIENT_PERMISSIONS | 403 | Mostrar: "Você não tem permissão para realizar esta ação" |
| NETWORK_ERROR | — | Mostrar ErrorMessage com retry: "Erro de conexão. Verifique sua internet." |

## 7. Requirement Traceability

| ID | Story | Status |
|----|-------|--------|
| ORD-01 | P1: CreateOrder — formulário com merchant selector e lista de itens | Pending |
| ORD-02 | P1: CreateOrder — adicionar/remover linhas de item com cálculo de subtotal | Pending |
| ORD-03 | P1: CreateOrder — total em tempo real formatado em BRL | Pending |
| ORD-04 | P1: CreateOrder — chamada POST com headers Auth, Merchant-Id, Idempotency-Key | Pending |
| ORD-05 | P1: CreateOrder — sucesso redireciona para detail | Pending |
| ORD-06 | P1: CreateOrder — erro EMPTY_ORDER | Pending |
| ORD-07 | P1: CreateOrder — erro INVALID_ITEM_PRICE / INVALID_QUANTITY | Pending |
| ORD-08 | P1: CreateOrder — erro MERCHANT_NOT_FOUND | Pending |
| ORD-09 | P1: CreateOrder — erro TOTAL_EXCEEDS_LIMIT | Pending |
| ORD-10 | P1: CreateOrder — idempotência (DUPLICATE_ORDER) | Pending |
| ORD-11 | P1: OrderHistory — listagem paginada com status badge e valor | Pending |
| ORD-12 | P1: OrderHistory — filtro por status com reset de página | Pending |
| ORD-13 | P1: OrderHistory — estado vazio "Nenhum pedido encontrado" | Pending |
| ORD-14 | P1: OrderHistory — loading com Spinner | Pending |
| ORD-15 | P1: OrderHistory — erro com ErrorMessage + retry | Pending |
| ORD-16 | P1: OrderDetail — exibição de dados completos do pedido | Pending |
| ORD-17 | P1: OrderDetail — tabela de itens com subtotais | Pending |
| ORD-18 | P1: OrderDetail — link para transação quando PAID | Pending |
| ORD-19 | P1: OrderDetail — botão "Cancelar pedido" quando PENDING | Pending |
| ORD-20 | P1: OrderDetail — cancelamento via DELETE com confirmação | Pending |
| ORD-21 | P1: OrderDetail — erro ORDER_CANNOT_BE_CANCELLED | Pending |
| ORD-22 | P1: OrderDetail — erro ORDER_NOT_FOUND | Pending |
| ORD-23 | P1: OrderDetail — erro INSUFFICIENT_PERMISSIONS | Pending |
