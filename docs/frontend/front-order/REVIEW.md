# Review Report — front-order

**Reviewer:** agent-reviewer
**Date:** 2026-06-23
**Spec:** `.specs/features/front-order/spec.md`

---

## SOLID Check

| Principle | Status | Observation |
|-----------|--------|-------------|
| **S** Single Responsibility | ⚠️ | Components mix UI rendering, validation, API calls, error mapping, and navigation. No custom hooks separate concerns. |
| **O** Open/Closed | ✅ | Props-based injection (`apiClient`, `navigate`) makes components extensible. Error handling is hardcoded per error code. |
| **L** Liskov Substitution | ⚠️ | Tests use `mockClient as never` to bypass type checks instead of properly typed mocks. Inline mocks work but don't follow shared mock interfaces from `__mocks__/`. |
| **I** Interface Segregation | ✅ | Each component has minimal, focused props (`orderId`, `apiClient`, `navigate`). |
| **D** Dependency Inversion | ✅ | Dependencies injected via props (`apiClient`, `navigate`) with fallback to concrete implementations. |

## FIRST Test Check

| Principle | Status | Observation |
|-----------|--------|-------------|
| **F** Fast | ✅ | All tests use mocked ApiClient — no network calls. |
| **I** Isolated | ✅ | `beforeEach` with `vi.clearAllMocks()` and fresh `createMockClient()`. |
| **R** Repeatable | ⚠️ | Module-level `let itemIdCounter = 0` in `CreateOrderPage.tsx:17` never resets (increments across renders). Doesn't affect current tests but is a latent risk. |
| **S** Self-validating | ✅ | All tests have explicit `expect` assertions. |
| **T** Timely | ✅ | Tests exist for all three pages, covering success, error, loading, and edge cases. |

## Mock Isolation Check

| Mock | Location | Well isolated? | Observation |
|------|----------|---------------|-------------|
| `apiClient` (inline) | Test files: `createMockClient()` | ✅ | Injected via `apiClient` prop. Correct pattern. |
| `useAuth` (inline) | `vi.mock('@/contexts/AuthContext')` | ✅ | Mocked at module level, returns controlled user/token. |
| `__mocks__/apiClient.ts` | `src/__mocks__/apiClient.ts` | ❌ Not used | Tests use inline mocks instead of shared mock. Inline mocks match needed interface. |
| `__mocks__/authContext.ts` | `src/__mocks__/authContext.ts` | ❌ Not used | Tests use `vi.mock` instead of shared mock. |

## Clean Architecture Check

- ✅ Types defined in `src/types/order.ts` — no duplication
- ✅ API calls go through `ApiClient` abstraction (not raw `fetch`)
- ⚠️ **No custom hooks** — business logic (API calls, state, validation) lives inside components
- ⚠️ **order-api.ts** exists but **is NOT used** by any component — all three pages call `apiClient.get/post/delete` directly instead of using the domain functions in `order-api.ts`

## Spec Compliance

| ID | Requirement | Status |
|----|-------------|--------|
| ORD-01 | Form with merchant selector + items list | ✅ |
| ORD-02 | Add/remove item rows with subtotal calculation | ✅ |
| ORD-03 | Real-time total formatted as BRL | ✅ |
| ORD-04 | POST with Auth, Merchant-Id, Idempotency-Key | ❌ **BUG** (see ISSUE-001) |
| ORD-05 | Success redirects to detail page | ✅ |
| ORD-06 | EMPTY_ORDER error message | ✅ |
| ORD-07 | INVALID_ITEM_PRICE / INVALID_QUANTITY errors | ✅ |
| ORD-08 | MERCHANT_NOT_FOUND error | ✅ |
| ORD-09 | TOTAL_EXCEEDS_LIMIT error | ✅ |
| ORD-10 | DUPLICATE_ORDER idempotency (409 redirect, 200 message) | ❌ **GAP** (see ISSUE-002) |
| ORD-11 | Paginated list with status badge + BRL value | ✅ |
| ORD-12 | Status filter with page reset | ✅ |
| ORD-13 | Empty state "Nenhum pedido encontrado" | ✅ |
| ORD-14 | Loading with Spinner | ✅ |
| ORD-15 | Error with ErrorMessage + retry | ✅ |
| ORD-16 | Full order detail display | ✅ |
| ORD-17 | Items table with subtotals | ✅ |
| ORD-18 | Transaction link when PAID | ✅ |
| ORD-19 | "Cancelar pedido" button when PENDING | ✅ |
| ORD-20 | Cancellation via DELETE with confirmation | ✅ |
| ORD-21 | ORDER_CANNOT_BE_CANCELLED error | ✅ |
| ORD-22 | ORDER_NOT_FOUND error with history link | ✅ |
| ORD-23 | INSUFFICIENT_PERMISSIONS error | ✅ |

## Issues Found

### ISSUE-001: Idempotency-Key mismatch (ORD-04)
- **Severity:** HIGH
- **File:** `src/pages/orders/CreateOrderPage.tsx:103-112`
- **Detail:** The component generates **two different UUIDs** — one placed in the request body (`idempotencyKey: crypto.randomUUID()` at line 109) and another sent in the header (`idempotencyKey: crypto.randomUUID()` at line 111). The spec ORD-04 requires a **single auto-generated UUID v4** that is both included in the body and sent as the `Idempotency-Key` header. This defeats the purpose of idempotency since the two keys will never match.

### ISSUE-002: DUPLICATE_ORDER 409 not handled (ORD-10)
- **Severity:** MEDIUM
- **File:** `src/pages/orders/CreateOrderPage.tsx:131`
- **Detail:** The catch block only checks `DUPLICATE_ORDER && apiErr.status === 200`. Per spec, when the API returns 409 with `DUPLICATE_ORDER`, the code should redirect to the existing order's detail page. The 409 path falls through to the generic error handler. The existing test for this scenario uses `mockResolvedValue` (not `mockRejectedValue`), so the 409 error path is untested.

### ISSUE-003: Unused order-api.ts domain functions
- **Severity:** LOW
- **File:** `src/api/order-api.ts`
- **Detail:** The file defines clean domain functions (`createOrder`, `getOrder`, `listOrders`, `cancelOrder`) but **none of the three page components use them**. All pages call `apiClient.get/post/delete` directly, mixing API URL construction with UI logic. This violates Clean Architecture separation of concerns.

### ISSUE-004: TypeScript errors in production code (TS 2339)
- **Severity:** HIGH
- **File:** `src/pages/orders/CreateOrderPage.tsx:114-115`
- **Detail:** `Property 'orderId' does not exist on type '{}'` — the `apiClient.post` call lacks explicit type parameter inference for the response type, causing `response.data` to be typed as `{}` instead of `CreateOrderResponse`. 7 total TS errors across front-order files (`npx tsc --noEmit`).

### ISSUE-005: Module-level mutable counter
- **Severity:** LOW
- **File:** `src/pages/orders/CreateOrderPage.tsx:17-19`
- **Detail:** `let itemIdCounter = 0` at module scope persists across renders and component remounts. While not causing failures in current tests, it's a latent bug if items are managed across component lifecycles. A `useRef` would be more appropriate.

### ISSUE-006: Status filter labels in English
- **Severity:** LOW
- **File:** `src/pages/orders/OrderHistoryPage.tsx:26-32`
- **Detail:** Dropdown labels display status enum values in English (`PENDING`, `PROCESSING`, etc.) rather than Portuguese translations (e.g., "Pendente", "Processando", "Pago"). While functional, this breaks consistency with the rest of the UI which is in Portuguese.

### ISSUE-007: No INSUFFICIENT_PERMISSIONS test for OrderDetailPage
- **Severity:** LOW
- **File:** `src/pages/orders/OrderDetailPage.test.tsx`
- **Detail:** The spec requires handling `INSUFFICIENT_PERMISSIONS` (ORD-23), and the implementation has the error handling. However, there are no tests for the 403 error scenario.

## Verdict

**CONDITIONAL_PASS**

All 34 front-order tests pass. Core functionality (CreateOrder, OrderHistory, OrderDetail) is implemented and tested. However, there are **2 functional issues** (IDEMPOTENCY_KEY mismatch and DUPLICATE_ORDER 409 handling), **7 TypeScript errors**, and architectural concerns (business logic mixed with UI, unused domain functions) that should be resolved before integration.
