# Spec: Frontend Checkout — CardForm & PaymentResult

**ID:** SPEC-FE-CHECKOUT-001
**Área:** front-checkout
**Status:** Draft

---

## 1. Visão Geral

Implementa o fluxo de pagamento com cartão de crédito: formulário de captura de dados do cartão (CardForm), geração de card token via Mercado Pago JS SDK, envio da transação para `POST /api/v1/transactions` e exibição do resultado (PaymentResult). Consome os tipos compartilhados e o API Client da área front-shared e depende dos tipos de pedido da área front-order.

---

## 2. User Stories

### P1: CardForm — Formulário de Pagamento ⭐ MVP

**User Story:** As a customer with a pending order, I want to enter my credit card details, select installment count, and pay so that my order is completed.

**Acceptance Criteria:**
1. WHEN user accesses `/checkout?orderId=<id>` THEN page SHALL fetch order details and display order summary (total amount, items) at the top
2. WHEN user types card number THEN input SHALL apply mask (#### #### #### ####) and detect card brand from first digits (Visa: `4`, Mastercard: `5[1-5]`, Elo: `4011|4312|4389`, Amex: `34|37`, Hipercard: `6062`)
3. WHEN card brand is detected THEN form SHALL display brand icon next to card number field
4. WHEN user types expiry THEN input SHALL apply MM/AA mask and validate month is 01-12 and year is current or future
5. WHEN user types CVV THEN input SHALL limit to 3 digits (4 for Amex) and mask as bullets
6. WHEN user clicks "Pagar" THEN form SHALL validate all fields: card number (Luhn algorithm), expiry (not past), CVV (non-empty), cardholder name (non-empty, min 3 chars), installments (1-12 selected)
7. WHEN validation passes THEN form SHALL call `MercadoPago.cardToken()` with card data to generate a card token
8. WHEN `cardToken()` succeeds THEN form SHALL call `POST /api/v1/transactions` with `{ amountInCents, currency: 'BRL', customerId, orderId, cardToken: token.id, paymentMethodId: 'credit', installments }`
9. WHEN `POST /api/v1/transactions` returns `{ data: { status: 'APPROVED' } }` THEN page SHALL redirect to `/checkout/result?transactionId=<id>&status=APPROVED`
10. WHEN `POST /api/v1/transactions` returns `{ data: { status: 'FAILURE' } }` THEN page SHALL redirect to `/checkout/result?transactionId=<id>&status=FAILURE` with error details
11. DURING API call the submit button SHALL show loading spinner and be disabled
12. PCI compliance: the frontend SHALL NEVER store card number, CVV, or cardToken in variables outside the local form scope, SHALL NOT log them to console, SHALL NOT send them to any endpoint other than Mercado Pago JS SDK

**Independent Test:** Mock `window.MercadoPago.cardToken()` to resolve with `{ id: 'tok_test_123' }`. Mock `apiClient.post('/api/v1/transactions')` to resolve with `{ data: { status: 'APPROVED', transactionId: 'txn_123', orderId: 'ord_123', mpPaymentId: 12345, processingTimeMs: 350 } }`. Fill form with valid data, submit, assert redirect to `/checkout/result` with correct params.

---

### P1: PaymentResult — Tela de Resultado do Pagamento ⭐ MVP

**User Story:** As a customer who just paid, I want to see the payment result clearly so that I know whether my payment was approved or what went wrong.

**Acceptance Criteria:**
1. WHEN payment is approved THEN page SHALL display:
   - Green checkmark icon (`text-green-500`)
   - Title "Pagamento aprovado!" in green
   - Transaction amount formatted as BRL (`R$ 1.234,56`)
   - Transaction ID
   - Processing time in milliseconds
   - Button "Ver pedido" that links to `/orders/{orderId}`
2. WHEN payment fails with `CARD_DECLINED` THEN page SHALL display:
   - Red X icon (`text-red-500`)
   - Title "Cartão recusado"
   - Message "Seu cartão foi recusado. Verifique os dados ou tente outro cartão."
   - Button "Tentar novamente com outro cartão" that links back to `/checkout?orderId=<id>`
3. WHEN payment fails with `SUSPECTED_FRAUD` THEN page SHALL display:
   - Red X icon
   - Title "Transação suspeita"
   - Message "Transação suspeita. Entre em contato com o suporte."
   - NO retry button (non-retryable)
4. WHEN payment fails with `MP_GATEWAY_TIMEOUT` THEN page SHALL display:
   - Orange warning icon (`text-orange-500`)
   - Title "Tempo limite excedido"
   - Message "O gateway de pagamento não respondeu a tempo. Tente novamente."
   - Button "Tentar novamente" that links back to `/checkout?orderId=<id>`
5. WHEN payment fails with `INVALID_CARD_TOKEN` THEN page SHALL display:
   - Red X icon
   - Title "Erro no processamento"
   - Message "Ocorreu um erro ao processar seu cartão. Tente novamente."
   - Button "Tentar novamente" that links back to `/checkout?orderId=<id>`
6. WHEN payment fails with any other retryable error THEN page SHALL display:
   - Red X icon
   - Title "Pagamento não aprovado"
   - Message from the API error
   - Button "Tentar novamente" if `retryable === true`
7. Processing time (`processingTimeMs`) SHALL be displayed formatted as "Processado em X,XXX ms" for all result variants

**Independent Test:** Mock `useSearchParams` to return `status=APPROVED` with query params. Assert green checkmark and "Pagamento aprovado!" text. Repeat for CARD_DECLINED and SUSPECTED_FRAUD variants.

---

## 3. Interface Types (TypeScript)

```typescript
// ── Card Brand Detection ────────────────────────────────

export type CardBrand = 'visa' | 'mastercard' | 'elo' | 'amex' | 'hipercard' | 'unknown';

export const CARD_BRAND_PATTERNS: Record<CardBrand, RegExp> = {
  visa: /^4/,
  mastercard: /^5[1-5]/,
  elo: /^(4011|4312|4389)/,
  amex: /^3[47]/,
  hipercard: /^6062/,
  unknown: /.*/,
};

// ── Form Types ──────────────────────────────────────────

export interface CardFormData {
  cardNumber: string;       // unmasked digits only (16)
  cardNumberMasked: string; // formatted "#### #### #### ####"
  expiryMonth: string;      // "01".."12"
  expiryYear: string;       // "30".."40"
  cvv: string;              // 3-4 digits
  cardholderName: string;   // full name
  installments: number;     // 1-12
}

export type CardFormField = keyof CardFormData;

export interface CardFormErrors {
  cardNumber?: string;
  expiry?: string;
  cvv?: string;
  cardholderName?: string;
  installments?: string;
}

// ── Mercado Pago SDK ────────────────────────────────────

export interface MercadoPagoCardTokenRequest {
  cardNumber: string;
  expirationMonth: string;
  expirationYear: string;
  securityCode: string;
  cardholderName: string;
}

export interface MercadoPagoCardTokenResponse {
  id: string;               // card token to send in transaction POST
  publicKey: string;
  status: 'active' | 'used';
  cardholder: {
    name: string;
  };
}

export interface MercadoPagoInstance {
  cardToken(
    data: MercadoPagoCardTokenRequest,
  ): Promise<MercadoPagoCardTokenResponse>;
}

export interface MercadoPagoConstructor {
  new (publicKey: string, options?: { locale: string }): MercadoPagoInstance;
}

// ── Payment / Transaction Types ─────────────────────────

export interface TransactionRequest {
  amountInCents: number;
  currency: 'BRL';
  customerId: string;
  orderId: string;
  cardToken: string;
  paymentMethodId: 'credit';
  installments?: number;
}

export interface TransactionSuccess {
  transactionId: string;
  mpPaymentId: number;
  orderId: string;
  status: 'APPROVED';
  processingTimeMs: number;
}

export interface TransactionFailure {
  status: 'FAILURE';
  errorCode: string;
  message: string;
  retryable: boolean;
  processingTimeMs: number;
}

export type TransactionResponse = TransactionSuccess | TransactionFailure;

export type PaymentResultStatus = 'APPROVED' | 'FAILURE';

export interface PaymentResultData {
  transactionId: string;
  orderId: string;
  status: PaymentResultStatus;
  amountInCents: number;
  processingTimeMs: number;
  errorCode?: string;
  errorMessage?: string;
  retryable?: boolean;
}

// ── Installment Types ───────────────────────────────────

export interface InstallmentOption {
  value: number;            // installment count (1-12)
  label: string;            // "1x de R$ 1.234,56"
  total: number;            // total in cents
  installmentAmount: number; // per-installment amount in cents
}

// ── CardForm Component Props ────────────────────────────

export interface CardFormProps {
  orderId: string;
  amountInCents: number;
  customerId: string;
  onPaymentComplete: (result: PaymentResultData) => void;
  onError: (error: string) => void;
}

// ── PaymentResult Component Props ───────────────────────

export interface PaymentResultProps {
  result: PaymentResultData;
  onRetry: () => void;
  onViewOrder: (orderId: string) => void;
}
```

---

## 4. API Endpoints Consumidos

| Endpoint | Método | Headers | Request | Response |
|----------|--------|---------|---------|----------|
| `/api/v1/transactions` | POST | `Authorization: Bearer <token>`<br>`X-Merchant-Id: <merchantId>`<br>`Idempotency-Key: <uuid>`<br>`X-Forwarded-For: <clientIp>` | `TransactionRequest` (body JSON) | `{ data: TransactionSuccess, meta: { timestamp, requestId }, errors: [] }`<br>ou<br>`{ data: null, meta: { timestamp, requestId }, errors: [{ code, message, retryable }] }` |
| `/api/v1/orders/{id}` | GET | `Authorization: Bearer <token>` | — | `{ data: Order, meta: { ... }, errors: [] }` |

---

## 5. Mock Contracts

A área front-checkout depende de front-shared (IApiClient, tipos) e front-order (tipos de pedido). Abaixo os contratos de mock para cada dependência.

```typescript
// ── Mock: front-shared → IApiClient ────────────────────
// Criar em: __mocks__/apiClient.ts

export interface IMockApiClient {
  post: <TBody, TRes>(url: string, body?: TBody, options?: Record<string, unknown>) => Promise<TRes>;
  get: <TRes>(url: string, options?: Record<string, unknown>) => Promise<TRes>;
}

// ── Mock: front-shared → IAuthContext ──────────────────
// Criar em: __mocks__/authContext.ts

export interface IMockAuthContext {
  user: { id: string; email: string; role: string; merchantId?: string } | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

// ── Mock: front-order → OrderService ────────────────────
// Criar em: __mocks__/orderService.ts
// Usado para buscar detalhes do pedido na tela de checkout

export interface IMockOrderService {
  getOrderById: (orderId: string) => Promise<{
    id: string;
    status: string;
    amountInCents: number;
    currency: string;
    items: Array<{
      id: string;
      name: string;
      quantity: number;
      unitPriceInCents: number;
    }>;
    merchantId: string;
    customerId: string;
    createdAt: string;
  }>;
}

// ── Mock: Mercado Pago SDK ──────────────────────────────
// Criar em: __mocks__/mercadoPago.ts

export interface IMockMercadoPago {
  cardToken: (data: MercadoPagoCardTokenRequest) => Promise<MercadoPagoCardTokenResponse>;
}
```

---

## 6. Error Scenarios

| Erro | Código | Comportamento esperado |
|------|--------|----------------------|
| Cartão recusado pelo banco | `CARD_DECLINED` | PaymentResult com título "Cartão recusado", botão retryável "Tentar novamente com outro cartão" |
| Token de cartão inválido | `INVALID_CARD_TOKEN` | PaymentResult com título "Erro no processamento", botão retryável |
| Idempotency duplicada | `DUPLICATE_IDEMPOTENCY_KEY` | Redirecionar para PaymentResult com status da transação original (buscar por orderId) |
| Pedido não encontrado | `ORDER_NOT_FOUND` | CardForm exibe `ErrorMessage` com título "Pedido não encontrado", sem retry |
| Pedido não está pendente | `ORDER_NOT_PENDING` | CardForm exibe `ErrorMessage` com título "Este pedido já foi processado", sem retry |
| Saldo insuficiente | `INSUFFICIENT_FUNDS` | PaymentResult com título "Saldo insuficiente", sem retry, mensagem "Seu cartão não tem limite disponível" |
| Fraude suspeita | `SUSPECTED_FRAUD` | PaymentResult com título "Transação suspeita", sem retry, contatar suporte |
| Valor inválido | `INVALID_AMOUNT` | CardForm exibe erro de validação "Valor inválido para transação" |
| Gateway de pagamento time out | `MP_GATEWAY_TIMEOUT` | PaymentResult com ícone laranja, título "Tempo limite excedido", botão retryável |
| Rate limit excedido | `RATE_LIMIT_EXCEEDED` | CardForm desabilita submit por 30 segundos, exibe "Muitas tentativas. Aguarde 30 segundos." |
| Erro interno do servidor | `INTERNAL_ERROR` | PaymentResult com título "Erro interno", botão retryável |
| Rede indisponível / timeout | `NETWORK_ERROR` | CardForm exibe `ErrorMessage` com título "Erro de conexão", botão retry |
| SDK Mercado Pago falha | `MP_SDK_ERROR` | CardForm exibe erro "Não foi possível processar seu cartão. Tente novamente." botão retry |
| Card number não passa Luhn | — | Validação inline: "Número de cartão inválido" |
| CVV inválido (letras) | — | Validação inline: "CVV inválido" |
| Cartão vencido | — | Validação inline: "Cartão vencido" |
| Nome do titular incompleto | — | Validação inline: "Nome do titular deve ter ao menos 3 caracteres" |

---

## 7. Requirement Traceability

| ID | Story | Status |
|----|-------|--------|
| CHECKOUT-01 | P1: CardForm — máscara e detecção de bandeira | Pending |
| CHECKOUT-02 | P1: CardForm — validação de campos (Luhn, expiry, CVV, nome) | Pending |
| CHECKOUT-03 | P1: CardForm — geração de card token via Mercado Pago SDK | Pending |
| CHECKOUT-04 | P1: CardForm — POST /api/v1/transactions com token e dados | Pending |
| CHECKOUT-05 | P1: CardForm — loading state no botão submit | Pending |
| CHECKOUT-06 | P1: CardForm — PCI compliance (não logar/store card data) | Pending |
| CHECKOUT-07 | P1: CardForm — exibição de resumo do pedido | Pending |
| CHECKOUT-08 | P1: CardForm — seletor de parcelas 1-12 | Pending |
| CHECKOUT-09 | P1: PaymentResult — sucesso com green checkmark e dados | Pending |
| CHECKOUT-10 | P1: PaymentResult — CARD_DECLINED com retry | Pending |
| CHECKOUT-11 | P1: PaymentResult — SUSPECTED_FRAUD sem retry | Pending |
| CHECKOUT-12 | P1: PaymentResult — MP_GATEWAY_TIMEOUT com ícone laranja e retry | Pending |
| CHECKOUT-13 | P1: PaymentResult — exibição de processingTimeMs formatado | Pending |
| CHECKOUT-14 | P1: PaymentResult — erro retryable vs non-retryable | Pending |
| CHECKOUT-15 | P1: Checkout — integração com front-order (buscar pedido) | Pending |
