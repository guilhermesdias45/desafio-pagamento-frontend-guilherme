# Documentation — front-checkout

**Documenter:** agent-documenter
**Date:** 2026-06-23
**Cycle:** 2

---

## What Was Built

| Arquivo | Propósito |
|---------|-----------|
| `src/pages/checkout/CardForm.tsx` | Formulário de pagamento com cartão de crédito: máscara, detecção de bandeira, validação Luhn, integração Mercado Pago SDK, POST `/api/v1/transactions` |
| `src/pages/checkout/CardForm.test.tsx` | 25 testes para CardForm (detecção de bandeira, máscara, validação, submit, loading, PCI) |
| `src/pages/checkout/PaymentResult.tsx` | Tela de resultado do pagamento com 6 variantes de erro (APPROVED, CARD_DECLINED, SUSPECTED_FRAUD, MP_GATEWAY_TIMEOUT, INVALID_CARD_TOKEN, genérico) |
| `src/pages/checkout/PaymentResult.test.tsx` | 18 testes para PaymentResult (todas as variantes de status + formatação) |
| `src/api/payment-api.ts` | `createPaymentApi` factory com `createTransaction()` — abstrai POST `/api/v1/transactions` com tratamento de erros |
| `src/types/checkout.ts` | Tipos compartilhados: `CardBrand`, `CardFormData`, `MercadoPagoCardTokenRequest/Response`, `MercadoPagoInstance`, `TransactionRequest`, `TransactionSuccess/Failure`, `PaymentResultData`, `InstallmentOption`, `CardFormProps`, `PaymentResultProps` |

## Tests Created

| Arquivo | Tipo | Qtde testes | Cobertura |
|---------|------|-------------|-----------|
| `src/pages/checkout/CardForm.test.tsx` | Component | 25 | Detecção 6 bandeiras, máscara Visa/Amex, expiry MM/AA + validação mês/vencido, CVV bullets/limite 3-4 dígitos, parcelas 1-12, resumo BRL, Luhn + nome + expiry + CVV vazio, loading spinner, fluxo completo MP SDK → POST, erro API, PCI sem console.log |
| `src/pages/checkout/PaymentResult.test.tsx` | Component | 18 | APPROVED (check verde, título, BRL, ID, tempo, botão Ver pedido, sem retry), CARD_DECLINED (X vermelho, título, mensagem, retry), SUSPECTED_FRAUD (X vermelho, título, sem retry), MP_GATEWAY_TIMEOUT (warning laranja, título, retry), INVALID_CARD_TOKEN (título, retry), genérico retryable/non-retryable, tempo formatado 1.500 ms |

**Total de testes:** 43

**Nota:** O review original contabilizou 24+18=42. A contagem atual reflete 25 testes em CardForm (1 teste adicional de loading state).

## Mocked Functions

| Função mockada | Interface original | Motivo | Localização do mock | Comportamento do mock | Status |
|---------------|-------------------|--------|--------------------|-----------------------|--------|
| `MockMercadoPagoInstance.cardToken` | `MercadoPagoInstance.cardToken` (spec §3) | front-checkout depende do SDK externo Mercado Pago para gerar card token | `src/__mocks__/mercadoPago.ts` | Retorna `{ id: 'tok_test_123', publicKey: 'TEST-123', status: 'active', cardholder: { name: 'Test User' } }` | ⚠️ Não usado nos testes — testes mockam `window.MercadoPago` diretamente |
| `MockOrderService.getOrderById` | `IMockOrderService` (spec §5) | front-checkout depende de front-order para buscar detalhes do pedido (CHECKOUT-15) | `src/__mocks__/orderService.ts` | Retorna `{ id: 'ord_123', status: 'PENDING', amountInCents: 123456, currency: 'BRL', items: [...], merchantId: 'merchant_1', customerId: 'cust_1', createdAt: '...' }` | ❌ Nunca importado/usado — CHECKOUT-15 não implementado |
| `MockApiClient.post/get/patch` | `IApiClient` (front-shared spec §3) | front-checkout depende de front-shared para chamadas HTTP | `src/__mocks__/apiClient.ts` | Retorna `{ data: null, meta: { timestamp: '', requestId: '' }, errors: [] }` para todos os métodos | ⚠️ Não usado — CardForm usa `postTransaction` injetado, não `apiClient` |
| `MockAuthContext` | `IAuthContext` (front-shared spec §3) | front-checkout depende de front-shared para autenticação | `src/__mocks__/authContext.ts` | `user: null, accessToken: null, isAuthenticated: false, ...` | ⚠️ Não usado — token lido de `window.__AUTH_TOKEN__` em vez de contexto |

## Technical Decisions

| Decisão | Opção | Motivo |
|---------|-------|--------|
| Dependency Injection | `postTransaction` injetado via props com fallback `fetch` | Facilita testes. Permite mockar chamada HTTP. Fallback permite uso sem provider. |
| Card Brand Detection | Função pura `detectBrand` baseada em regex dos primeiros dígitos | Sem dependências. Testável isoladamente. Cobre Visa, Mastercard, Elo, Amex, Hipercard. |
| Luhn Validation | Função pura `luhnCheck` | Validação padrão da indústria para números de cartão. |
| Form State | `useState` local (não biblioteca de forms) | Simplicidade. Sem dependências externas. Escopo local = PCI compliance (não persiste). |
| Currency Formatting | `Intl.NumberFormat('pt-BR')` | Nativo do browser. Formatação BRL correta. Sem bibliotecas. |
| PaymentResult | Componente puro sem effects | Apenas renderiza props. Fácil de testar. Determinístico. |
| Error Mapper | `getErrorConfig` switch por `errorCode` | Centraliza mapeamento erro → ícone/título/mensagem/retryable. Adicionar novo erro = adicionar case. |
| MP SDK Access | `window.MercadoPago` global com `new` | Violação de DIP (ver BLOCKER-003). Deveria ser injetado via props. |
| Auth Token | `window.__AUTH_TOKEN__` global | Violação PCI/segurança (ver BLOCKER-004). Deveria vir do AuthContext. |

## Requirements Status

| ID | Story | Status |
|----|-------|--------|
| CHECKOUT-01 | P1: CardForm — máscara e detecção de bandeira | ✅ Implemented |
| CHECKOUT-02 | P1: CardForm — validação de campos (Luhn, expiry, CVV, nome) | ✅ Implemented |
| CHECKOUT-03 | P1: CardForm — geração de card token via Mercado Pago SDK | ✅ Implemented |
| CHECKOUT-04 | P1: CardForm — POST /api/v1/transactions com token e dados | ⚠️ Missing `X-Merchant-Id` header |
| CHECKOUT-05 | P1: CardForm — loading state no botão submit | ✅ Implemented |
| CHECKOUT-06 | P1: CardForm — PCI compliance (não logar/store card data) | ⚠️ `window.__AUTH_TOKEN__` exposto globalmente |
| CHECKOUT-07 | P1: CardForm — exibição de resumo do pedido | ✅ Implemented |
| CHECKOUT-08 | P1: CardForm — seletor de parcelas 1-12 | ✅ Implemented |
| CHECKOUT-09 | P1: PaymentResult — sucesso com green checkmark e dados | ✅ Implemented |
| CHECKOUT-10 | P1: PaymentResult — CARD_DECLINED com retry | ✅ Implemented |
| CHECKOUT-11 | P1: PaymentResult — SUSPECTED_FRAUD sem retry | ✅ Implemented |
| CHECKOUT-12 | P1: PaymentResult — MP_GATEWAY_TIMEOUT com ícone laranja e retry | ✅ Implemented |
| CHECKOUT-13 | P1: PaymentResult — exibição de processingTimeMs formatado | ✅ Implemented |
| CHECKOUT-14 | P1: PaymentResult — erro retryable vs non-retryable | ✅ Implemented |
| CHECKOUT-15 | P1: Checkout — integração com front-order (buscar pedido) | ❌ Não implementado |

## Test Commands

```bash
# Rodar todos os testes de front-checkout
npm run test:run -- src/pages/checkout src/api/payment-api

# Rodar com cobertura
npm run test:coverage -- src/pages/checkout src/api/payment-api

# Typecheck
npx tsc --noEmit
```

## Next Cycle Input

### Dependências não resolvidas
- Depende de front-order (tipos de pedido e `OrderService`) — CHECKOUT-15 não implementado
- Depende de front-shared (`IApiClient`, `IAuthContext`) — parcialmente resolvido (mocks existem mas não são usados)

### Bloqueios ativos

**BLOCKER-001 (CRÍTICO): CHECKOUT-15 não implementado**
- Descrição: Não existe página/componente que leia `orderId` da URL, busque o pedido via API e renderize CardForm com os dados. CardForm recebe `amountInCents` como prop, sem fetching.
- Ação: `NEEDS_HUMAN:front-checkout:CHECKOUT-15 ausente — criar CheckoutPage que usa useSearchParams para ler orderId, busca pedido via OrderService e renderiza CardForm`

**BLOCKER-002 (ALTO): Header `X-Merchant-Id` ausente**
- Descrição: Spec §4 exige header `X-Merchant-Id` no POST `/api/v1/transactions`. Código atual envia apenas `Authorization`, `Idempotency-Key` e `X-Forwarded-For`.
- Arquivo: `src/pages/checkout/CardForm.tsx:231-236`

**BLOCKER-003 (MÉDIO): MP SDK viola DIP**
- Descrição: `window.MercadoPago` acessado diretamente em vez de injetado via props. Dificulta testes.
- Arquivo: `src/pages/checkout/CardForm.tsx:213-227`

**BLOCKER-004 (ALTO/PCI): Global `window.__AUTH_TOKEN__`**
- Descrição: Token de autenticação exposto em variável global. Deveria vir do AuthContext.
- Arquivo: `src/pages/checkout/CardForm.tsx:230`

### Sugestões
1. Substituir `window.MercadoPago` por prop `mercadoPagoInstance` ou hook `useMercadoPago`
2. Substituir `window.__AUTH_TOKEN__` por prop `authToken` vinda do AuthContext
3. Migrar mocks existentes (`__mocks__/mercadoPago.ts`, `orderService.ts`, `apiClient.ts`, `authContext.ts`) para serem usados pelos testes atuais
4. Substituir fetch direto no fallback de `postTransaction` pelo `apiClient` de front-shared
5. Adicionar SVG de bandeira do cartão (atualmente é texto `<span>Visa</span>`)

### Mocks pendentes
- `MockOrderService`: criado mas não utilizado — depende de CHECKOUT-15 ser implementado
- `MockApiClient`: criado mas não utilizado por front-checkout (CardForm usa `postTransaction` injetado)
- `MockAuthContext`: criado mas não utilizado (token lido de `window.__AUTH_TOKEN__`)
- `MockMercadoPagoInstance`: criado mas testes mockam `window.MercadoPago` diretamente

### Issues conhecidas
- **CHECKOUT-15**: Bloqueio crítico — falta página integradora CheckoutPage
- **BLOCKER-002**: Header `X-Merchant-Id` ausente viola spec §4
- **BLOCKER-004**: `window.__AUTH_TOKEN__` viola PCI compliance (CHECKOUT-06)
- **BLOCKER-003**: MP SDK acoplado ao `window` global viola DIP
- **BRAND ICON**: Spec pede ícone visual da bandeira, implementação atual usa texto
