# Documentation — front-order

**Documenter:** agent-documenter
**Date:** 2026-06-23
**Cycle:** 3

---

## What Was Built

| File | Purpose |
|------|---------|
| `src/types/order.ts` | Domain types: `OrderStatus`, `OrderItem`, `OrderItemDetail`, `CreateOrderRequest`, `CreateOrderResponse`, `OrderDetail`, `OrderSummary`, `OrderFormItem`, `OrderFilters` |
| `src/api/order-api.ts` | Domain API functions: `createOrder`, `getOrder`, `listOrders`, `cancelOrder` — **unused by all 3 page components** (see Known Issues below) |
| `src/pages/orders/CreateOrderPage.tsx` | CreateOrder form: merchant ID input, dynamic items list with add/remove, real-time subtotal/total in BRL, POST submission with auth headers |
| `src/pages/orders/OrderHistoryPage.tsx` | OrderHistory: paginated table with status badges (color-coded), BRL formatting, status filter dropdown, empty/loading/error states, retry mechanism |
| `src/pages/orders/OrderDetailPage.tsx` | OrderDetail: full order info display, items table with subtotals, transaction link (PAID), cancel button with confirmation modal (PENDING), error handling |

## Tests Created

| File | Type | Test count | Coverage |
|------|------|------------|----------|
| `src/pages/orders/CreateOrderPage.test.tsx` | Component (RTL) | 11 | Form rendering, add/remove items, subtotal/total calculation, validation errors (EMPTY_ORDER), API success redirect, API errors (MERCHANT_NOT_FOUND, TOTAL_EXCEEDS_LIMIT), DUPLICATE_ORDER (409) |
| `src/pages/orders/OrderHistoryPage.test.tsx` | Component (RTL) | 11 | Loading spinner, order rows rendering, status badge colors (PAID green, PENDING gray), BRL formatting, row click navigation, empty state, error + retry, pagination controls, page change API call, status filter + page reset |
| `src/pages/orders/OrderDetailPage.test.tsx` | Component (RTL) | 10 | Loading spinner, PAID order with transaction link, items table with subtotals, cancel button for PENDING, expiry display, cancel flow (DELETE + status update), ORDER_CANNOT_BE_CANCELLED error, ORDER_NOT_FOUND + history link, no cancel for PAID, no transaction link for PENDING |
| **Total** | | **32** | All 32 tests pass (verified by reviewer) |

**Missing test coverage:** `INSUFFICIENT_PERMISSIONS` (ORD-23) — no test for 403 error scenario on cancel (ISSUE-007 in REVIEW.md).

## Mocked Functions

| Mocked function | Source interface | Reason | Mock location | Mock behavior | Status |
|-----------------|-----------------|--------|---------------|---------------|--------|
| `apiClient.get` / `.post` / `.delete` | `IApiClient` (front-shared) | Isolate HTTP calls in component tests | Inline `createMockClient()` in each `.test.tsx` file | `vi.fn()` stubs; returns controlled data per test | ✅ Used |
| `useAuth` | `IAuthContext` (front-shared) | Provide authenticated user + token | `vi.mock('@/contexts/AuthContext')` in each `.test.tsx` file | Returns `{ user: { id, email, role }, token, isAuthenticated, isLoading }` | ✅ Used |
| `__mocks__/apiClient.ts` | `IApiClient` (shared) | Shared mock for cross-area reuse | `src/__mocks__/apiClient.ts` | Returns `ApiResponse` wrapper with `{ data, meta, errors }` — **not used by tests** | ❌ Not used |
| `__mocks__/authContext.ts` | `IAuthContext` (shared) | Shared mock for cross-area reuse | `src/__mocks__/authContext.ts` | Returns `MockAuthContext` with `user`, `accessToken`, `isAuthenticated`, etc. — **not used by tests** | ❌ Not used |

## Technical Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Dependency injection pattern | Props-based (`apiClient`, `navigate`) | Allows unit testing with controlled mocks; follows DIP per SOLID review |
| Auth context usage | `vi.mock('@/contexts/AuthContext')` at module level | Simpler than passing `user/token` through props; matches how real code consumes auth |
| No custom hooks | Business logic in components | Violates SRP; reviewer flagged as warning — logic (API calls, validation, error mapping) lives inside components instead of extracted hooks |
| `idempotencyKey` generation | Two separate `crypto.randomUUID()` calls (one for body, one for header) | **BUG (ISSUE-001):** Spec requires a single UUID used in both body and header |
| `DUPLICATE_ORDER` handling | Only checks `status === 200` for DUPLICATE_ORDER | **GAP (ISSUE-002):** 409 response falls through to generic error; spec requires redirect on 409 |
| Item ID generation | Module-level `let itemIdCounter = 0` | Latent bug (ISSUE-005): persists across renders; `useRef` would be more appropriate |
| Status filter labels | English enum values (`PENDING`, `PAID`, etc.) | Inconsistent with Portuguese UI (ISSUE-006); should show "Pendente", "Pago", etc. |

## Requirements Status

| ID | Story | Status |
|----|-------|--------|
| ORD-01 | P1: CreateOrder — formulário com merchant selector e lista de itens | ✅ Implemented |
| ORD-02 | P1: CreateOrder — adicionar/remover linhas de item com cálculo de subtotal | ✅ Implemented |
| ORD-03 | P1: CreateOrder — total em tempo real formatado em BRL | ✅ Implemented |
| ORD-04 | P1: CreateOrder — chamada POST com headers Auth, Merchant-Id, Idempotency-Key | ❌ **BUG** (ISSUE-001: two different UUIDs generated — body vs header) |
| ORD-05 | P1: CreateOrder — sucesso redireciona para detail | ✅ Implemented |
| ORD-06 | P1: CreateOrder — erro EMPTY_ORDER | ✅ Implemented |
| ORD-07 | P1: CreateOrder — erro INVALID_ITEM_PRICE / INVALID_QUANTITY | ✅ Implemented |
| ORD-08 | P1: CreateOrder — erro MERCHANT_NOT_FOUND | ✅ Implemented |
| ORD-09 | P1: CreateOrder — erro TOTAL_EXCEEDS_LIMIT | ✅ Implemented |
| ORD-10 | P1: CreateOrder — idempotência (DUPLICATE_ORDER) | ❌ **GAP** (ISSUE-002: 409 path falls to generic error; only 200 handled) |
| ORD-11 | P1: OrderHistory — listagem paginada com status badge e valor | ✅ Implemented |
| ORD-12 | P1: OrderHistory — filtro por status com reset de página | ✅ Implemented |
| ORD-13 | P1: OrderHistory — estado vazio "Nenhum pedido encontrado" | ✅ Implemented |
| ORD-14 | P1: OrderHistory — loading com Spinner | ✅ Implemented |
| ORD-15 | P1: OrderHistory — erro com ErrorMessage + retry | ✅ Implemented |
| ORD-16 | P1: OrderDetail — exibição de dados completos do pedido | ✅ Implemented |
| ORD-17 | P1: OrderDetail — tabela de itens com subtotais | ✅ Implemented |
| ORD-18 | P1: OrderDetail — link para transação quando PAID | ✅ Implemented |
| ORD-19 | P1: OrderDetail — botão "Cancelar pedido" quando PENDING | ✅ Implemented |
| ORD-20 | P1: OrderDetail — cancelamento via DELETE com confirmação | ✅ Implemented |
| ORD-21 | P1: OrderDetail — erro ORDER_CANNOT_BE_CANCELLED | ✅ Implemented |
| ORD-22 | P1: OrderDetail — erro ORDER_NOT_FOUND | ✅ Implemented |
| ORD-23 | P1: OrderDetail — erro INSUFFICIENT_PERMISSIONS | ⚠️ Implemented in code but **no test coverage** (ISSUE-007) |

## Test Commands

```bash
# Run all front-order tests
npx vitest run src/pages/orders/ --reporter verbose

# TypeScript check (7 errors expected)
npx tsc --noEmit --project tsconfig.json
```

## Next Cycle Input

### Unresolved dependencies
- **front-shared**: `ApiClient` class (`@/lib/api-client`), `AuthContext`/`useAuth` (`@/contexts/AuthContext`), UI components (`Button`, `Input`, `Spinner`, `ErrorMessage`)
- **front-checkout** (eventual): Transaction detail route `/transactions/{transactionId}` referenced by OrderDetailPage

### Active blockers
- **ISSUE-001 (HIGH):** `CreateOrderPage.tsx:109,111` — two different UUIDs generated for `idempotencyKey` (body) and `Idempotency-Key` (header). Fix: generate one UUID and reuse.
- **ISSUE-002 (MEDIUM):** `CreateOrderPage.tsx:131` — `DUPLICATE_ORDER` with status 409 falls through to generic error handler. Fix: handle `apiErr.status === 409` in catch block and redirect.
- **ISSUE-004 (HIGH):** 7 TypeScript errors across front-order files (`npx tsc --noEmit` reports TS 2339). Root cause: `apiClient.post` lacks explicit response type parameter inference — `response.data` typed as `{}` instead of `CreateOrderResponse`.

### Suggestions
1. **Extract custom hooks** (`useCreateOrder`, `useOrderHistory`, `useOrderDetail`) to move business logic out of components (addresses SRP violation).
2. **Use `order-api.ts` domain functions** in components instead of calling `apiClient` directly (addresses Clean Architecture separation of concerns, ISSUE-003).
3. **Migrate tests to shared mocks** (`__mocks__/apiClient.ts`, `__mocks__/authContext.ts`) instead of inline mock factories for cross-area consistency.
4. **Replace `let itemIdCounter = 0`** with `useRef` (ISSUE-005).
5. **Translate status filter labels** to Portuguese: "Pendente", "Processando", "Pago", "Cancelado", "Reembolsado" (ISSUE-006).

### Pending mocks
- None — all dependencies are mocked inline in test files. Shared `__mocks__/apiClient.ts` and `__mocks__/authContext.ts` exist but are unused.

### Known issues
| Issue | Severity | File | Description |
|-------|----------|------|-------------|
| ISSUE-001 | HIGH | `CreateOrderPage.tsx:109,111` | Two different `crypto.randomUUID()` calls — defeats idempotency |
| ISSUE-002 | MEDIUM | `CreateOrderPage.tsx:131` | `DUPLICATE_ORDER` with 409 not handled (only 200) |
| ISSUE-003 | LOW | `src/api/order-api.ts` | Domain functions exist but are unused by all 3 pages |
| ISSUE-004 | HIGH | `CreateOrderPage.tsx:114-115` | 7 TS2339 errors from missing response type inference |
| ISSUE-005 | LOW | `CreateOrderPage.tsx:17-19` | Module-level mutable counter (`itemIdCounter`) |
| ISSUE-006 | LOW | `OrderHistoryPage.tsx:26-32` | Status filter labels in English, not Portuguese |
| ISSUE-007 | LOW | `OrderDetailPage.test.tsx` | No test for `INSUFFICIENT_PERMISSIONS` (ORD-23) |
