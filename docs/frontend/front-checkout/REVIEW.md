# Review Report — front-checkout

**Reviewer:** agent-reviewer
**Date:** 2026-06-23
**Spec:** `.specs/features/front-checkout/spec.md`
**Code:** `src/pages/checkout/`, `src/api/payment-api.ts`, `src/types/checkout.ts`, `src/__mocks__/`

---

## SOLID Check

| Princípio | Status | Observação |
|-----------|--------|------------|
| S — Single Responsibility | ✅ | CardForm gerencia formulário de pagamento; PaymentResult exibe resultado. Cada componente tem responsabilidade clara. |
| O — Open/Closed | ⚠️ | `postTransaction` é injetado via props (extensível), mas `window.MercadoPago` é acessado diretamente, o que dificulta extensão sem modificar o componente. |
| L — Liskov Substitution | ✅ | Mocks podem substituir implementações reais sem quebrar o código (ex: `postTransaction` mock vs real). |
| I — Interface Segregation | ✅ | Props específicas e mínimas para cada componente. |
| D — Dependency Inversion | ❌ | Mercado Pago SDK é acessado via `window.MercadoPago` global em vez de ser injetado via props. Token de autenticação lido de `window.__AUTH_TOKEN__` (variável global). |

## FIRST Test Check

| Princípio | Status | Observação |
|-----------|--------|------------|
| F — Fast | ✅ | Testes usam mocks, sem network real. CardForm: ~500ms, PaymentResult: ~300ms. |
| I — Isolated | ✅ | `beforeEach` com `vi.clearAllMocks()`. Cada teste independente. |
| R — Repeatable | ✅ | Testes não dependem de ordem ou estado compartilhado. |
| S — Self-validating | ✅ | Asserts explícitos, sem `console.log`. |
| T — Timely | ✅ | Testes escritos para todas as funcionalidades implementadas (24 CardForm + 18 PaymentResult). |

## Mock Isolation Check

| Mock | Localização | Bem isolado? | Observação |
|------|------------|-------------|------------|
| Mercado Pago SDK | `src/__mocks__/mercadoPago.ts` | ⚠️ | Mock existe mas não é usado nos testes — os testes mockam `window.MercadoPago` diretamente. Nenhum teste importa `createMockMercadoPagoInstance`. |
| OrderService | `src/__mocks__/orderService.ts` | ❌ | Mock criado mas nunca importado/usado em nenhum teste de front-checkout. |
| IApiClient | `src/__mocks__/apiClient.ts` | ⚠️ | Mock existe mas não é usado por front-checkout (usa `postTransaction` injetado). |
| IAuthContext | `src/__mocks__/authContext.ts` | ⚠️ | Não usado por front-checkout. Token lido de `window.__AUTH_TOKEN__` em vez de contexto. |

## Clean Architecture Check

- ✅ CardForm separado de PaymentResult (dois componentes distintos)
- ✅ Card brand detection como função pura (`detectBrand`)
- ✅ Luhn algorithm como função pura (`luhnCheck`)
- ✅ Formatação de moeda como função pura (`formatCurrency`)
- ⚠️ API calls: `postTransaction` injetado (bom), mas fallback default usa `fetch` direto com `window.__AUTH_TOKEN__` — deveria usar `apiClient` de front-shared
- ✅ Tipos importados de `types/checkout.ts`
- ⚠️ `postTransaction` é definido no componente mas não está na interface `CardFormProps` do spec em `types/checkout.ts`

## Requirement Traceability

| ID | Requisito | Status | Evidência |
|----|-----------|--------|-----------|
| CHECKOUT-01 | CardForm — máscara e detecção de bandeira | ✅ | `detectBrand` + `maskCardNumber` implementados. Testes para Visa, Mastercard, Elo, Amex, Hipercard, desconhecida. |
| CHECKOUT-02 | CardForm — validação de campos | ✅ | `validateForm` com Luhn, expiry, CVV, nome. Erros em PT-BR. |
| CHECKOUT-03 | CardForm — geração de card token via MP SDK | ✅ | `window.MercadoPago.cardToken()` chamado com dados do cartão. |
| CHECKOUT-04 | CardForm — POST /api/v1/transactions | ⚠️ | POST implementado, mas faltam headers: `X-Merchant-Id` obrigatório conforme §4. |
| CHECKOUT-05 | CardForm — loading state | ✅ | Botão desabilitado com spinner durante submission. |
| CHECKOUT-06 | CardForm — PCI compliance | ⚠️ | Sem console.log. Mas `window.__AUTH_TOKEN__` expõe token globalmente. Card data armazenado em state local (aceitável). |
| CHECKOUT-07 | CardForm — exibição de resumo do pedido | ✅ | Total formatado em BRL exibido. |
| CHECKOUT-08 | CardForm — seletor de parcelas 1-12 | ✅ | Select com 12 opções, valores calculados. |
| CHECKOUT-09 | PaymentResult — sucesso | ✅ | Check verde, "Pagamento aprovado!", BRL, ID, "Processado em X ms", "Ver pedido". |
| CHECKOUT-10 | PaymentResult — CARD_DECLINED | ✅ | X vermelho, "Cartão recusado", botão retry. |
| CHECKOUT-11 | PaymentResult — SUSPECTED_FRAUD | ✅ | X vermelho, "Transação suspeita", sem retry. |
| CHECKOUT-12 | PaymentResult — MP_GATEWAY_TIMEOUT | ✅ | Warning laranja, "Tempo limite excedido", com retry. |
| CHECKOUT-13 | PaymentResult — processingTimeMs | ✅ | Formatado "Processado em X.XXX ms". Teste com 1.500. |
| CHECKOUT-14 | PaymentResult — retryable vs non-retryable | ✅ | Erro genérico mostra/esconde retry conforme `retryable`. |
| CHECKOUT-15 | Checkout — integração front-order | ❌ | Não há página/componente que leia `orderId` da URL e busque o pedido via API. CardForm recebe `amountInCents` como prop, sem never fetch. |

## Observações

### Pontos Positivos
1. **Cobertura de testes excelente**: 42 testes no total (24 CardForm + 18 PaymentResult), todos passando.
2. **Separação de responsabilidades**: CardForm e PaymentResult são componentes independentes com props bem definidas.
3. **Validação inline**: Luhn, expiry, CVV, nome — todas as validações com mensagens em PT-BR.
4. **Tratamento de erros**: Todos os cenários de erro definidos na spec têm tratamento adequado.

### Problemas Encontrados

**BLOCKER-001: CHECKOUT-15 não implementado**
- **Severidade**: CRÍTICO
- **Descrição**: Não existe página/componente que integre CardForm com front-order. A spec exige que o acesso a `/checkout?orderId=<id>` busque os detalhes do pedido e exiba resumo. Atualmente CardForm recebe `amountInCents` como prop, sem lógica de fetching.
- **Arquivo**: N/A — componente ausente (CheckoutPage)
- **Ação**: `NEEDS_HUMAN:front-checkout:CHECKOUT-15 ausente — criar CheckoutPage que usa useSearchParams para ler orderId, busca pedido via OrderService e renderiza CardForm`

**BLOCKER-002: Header `X-Merchant-Id` ausente**
- **Severidade**: ALTO
- **Descrição**: A spec §4 exige o header `X-Merchant-Id` na chamada `POST /api/v1/transactions`. O código atual envia apenas `Authorization`, `Idempotency-Key` e `X-Forwarded-For`.
- **Arquivo**: `src/pages/checkout/CardForm.tsx:231-236`
- **Ação**: Adicionar `X-Merchant-Id` aos headers da requisição. Pode vir de `window.__AUTH_TOKEN__` ou de prop `merchantId`.

**BLOCKER-003: Mercado Pago SDK viola DIP**
- **Severidade**: MÉDIO
- **Descrição**: `window.MercadoPago` é acessado diretamente em vez de ser injetado via props. Isso dificulta testes e viola Dependency Inversion.
- **Arquivo**: `src/pages/checkout/CardForm.tsx:213-227`
- **Ação**: Extrair MP SDK para prop `mercadoPagoInstance` ou para um hook `useMercadoPago` injetável.

**BLOCKER-004: Global `window.__AUTH_TOKEN__`**
- **Severidade**: ALTO (PCI/Security)
- **Descrição**: Token de autenticação lido de variável global `window.__AUTH_TOKEN__`. Deveria vir do AuthContext injetado via props.
- **Arquivo**: `src/pages/checkout/CardForm.tsx:230`
- **Ação**: Receber token via prop `authToken` ou usar `postTransaction` que já gerencia headers.

### Sugestões (não bloqueantes)
1. Brand "icon" é texto (`<span>Visa</span>`), não ícone visual — considerar adicionar SVG de bandeira
2. `X-Forwarded-For` é string vazia — considerar passar IP real do cliente
3. `formatProcessingTime` formata `1.500` (ponto) enquanto spec usa `1,500` (vírgula) — mas pt-BR usa ponto como separador de milhar, então está correto para o locale
4. Mocks em `__mocks__/` não são utilizados pelos testes atuais — considerar refatorar testes para usar `createMockMercadoPagoInstance`

### Resultados dos Testes

```
✓ src/pages/checkout/CardForm.test.tsx (24 testes, todos passando)
✓ src/pages/checkout/PaymentResult.test.tsx (18 testes, todos passando)
✓ TypeScript: sem erros nos arquivos de front-checkout
```

Testes de outras áreas (AuthContext, ConfirmEmailPage, contrast) têm falhas conhecidas não relacionadas a front-checkout.

## Verdict

**BLOCKER**

3 bloqueios críticos/altos impedem o PASS:
1. **BLOCKER-001**: CHECKOUT-15 não implementado (falta CheckoutPage)
2. **BLOCKER-002**: Header `X-Merchant-Id` ausente na chamada POST
3. **BLOCKER-004**: Token de autenticação exposto em variável global (`window.__AUTH_TOKEN__`)

Além disso, `BLOCKER-003` (DIP violado para MP SDK) deve ser corrigido para alinhamento com Clean Architecture.

**Recomendação**: Pausar pipeline e resolver NEEDS_HUMAN antes de prosseguir.
