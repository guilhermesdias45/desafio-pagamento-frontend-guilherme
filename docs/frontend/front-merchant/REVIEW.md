# Review Report — front-merchant

**Reviewer:** agent-reviewer
**Date:** 2026-06-24
**Spec:** `.specs/features/front-merchant/spec.md`
**Code:** `src/pages/merchant/`, `src/api/merchant-api.ts`, `src/types/merchant.ts`

---

## SOLID Check

| Princípio | Status | Observação |
|-----------|--------|------------|
| S — Single Responsibility | ✅ | TransactionsListPage e TransactionDetailPage têm responsabilidade de data-fetch + render (aceitável em page components). RefundModal tem responsabilidade única (form + confirmation flow). |
| O — Open/Closed | ✅ | Componentes aceitam `apiClient` e `navigate` como props injetáveis, permitindo extensão sem edição. |
| L — Liskov Substitution | ⚠️ | **ISSUE-001**: Mocks usam `as never` para contornar type safety, indicando gap entre interface do mock e do componente real. Sem crash em runtime, mas quebra segurança de tipos. |
| I — Interface Segregation | ✅ | Props são mínimas e específicas (`TransactionsListProps`, `RefundModalProps`). |
| D — Dependency Inversion | ⚠️ | **ISSUE-002**: `MerchantApiService` (serviço com `IApiClient` injetado) existe em `src/api/merchant-api.ts` mas **não é utilizado**. Os componentes usam `ApiClient` diretamente, pulando a camada de serviço. |

## FIRST Test Check

| Princípio | Status | Observação |
|-----------|--------|------------|
| F — Fast | ✅ | Testes usam mocks, sem network real. |
| I — Isolated | ✅ | `beforeEach` com `vi.clearAllMocks()`. |
| R — Repeatable | ✅ | Sem dependência de estado externo. |
| S — Self-validating | ⚠️ | **ISSUE-003**: Múltiplos testes têm assertions incorretas (ver detalhes abaixo). |
| T — Timely | ⚠️ | Testes existem mas contêm bugs, indicando que podem ter sido escritos "after the fact" ou não validados contra a implementação final. |

## Mock Isolation Check

| Mock | Localização | Bem isolado? | Observação |
|------|------------|-------------|------------|
| AuthContext | `vi.mock('@/contexts/AuthContext')` inline nos testes | ✅ | Corretamente mockado via module mock do Vitest |
| ApiClient | Objeto inline com `vi.fn()` | ⚠️ | Usa `as never` type assertion — não segue a interface do `ApiClient` fielmente |

## Clean Architecture Check

- ✅ Camada de UI (componentes) separada da lógica de API (`ApiClient` + `MerchantApiService`)
- ⚠️ Serviço `MerchantApiService` existe mas não é usado — componentes chamam `ApiClient` diretamente
- ✅ Tipos importados de `src/types/merchant.ts` (não duplicados)
- ✅ Efeitos colaterais em hooks (`useEffect`, `useCallback`) dentro dos componentes
- ✅ Erro de conexão `ApiError` tratado com código `NETWORK_ERROR` em `api-client.ts`

## Requirement Traceability

| ID | Descrição | Status | Evidência / Observação |
|----|-----------|--------|----------------------|
| MERCHANT-01 | P1: TransactionsList — fetch paginado com headers de auth e merchant | ❌ **FAIL** | `TransactionsListPage.tsx:54` não envia `merchantId` nas options da chamada à API. Headers `Authorization` é adicionado automaticamente pelo `ApiClient`, mas `X-Merchant-Id` está ausente. |
| MERCHANT-02 | P1: TransactionsList — renderização de tabela com BRL, status badge, card info | ✅ | `formatBRL` usa `Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })`. TransactionId truncado em 12 chars. Card brand + last 4 exibidos. |
| MERCHANT-03 | P1: TransactionsList — cores de status badge por status | ✅ | `STATUS_BADGE_CLASSES` segue exatamente as cores do spec: verde/vermelho/laranja/roxo/amarelo. |
| MERCHANT-04 | P1: TransactionsList — paginação (anterior/próximo/indicador de página) | ✅ | Botões "Anterior" e "Próximo" com `disabled` correto. Exibe "Página {page+1} de {totalPages}". |
| MERCHANT-05 | P1: TransactionsList — loading state com Spinner | ❌ **FAIL** | Spec diz `size="md"`, mas código usa `<Spinner size="lg" />` (`TransactionsListPage.tsx:110`). |
| MERCHANT-06 | P1: TransactionsList — empty state "Nenhuma transação encontrada" | ⚠️ **CONDITIONAL** | Texto correto, mas spec pede "com um ícone Info". Código não tem ícone. |
| MERCHANT-07 | P1: TransactionsList — error state com ErrorMessage e retry | ✅ | `ErrorMessage` com `onRetry={handleRetry}`. Título "Erro ao carregar transações". |
| MERCHANT-08 | P1: TransactionsList — clique na linha navega para detail | ✅ | `onClick={() => goTo('/merchant/transactions/' + tx.transactionId)}`. |
| MERCHANT-09 | P1: RefundModal — botão "Estornar" visível apenas para APPROVED/PARTIALLY_REFUNDED | ✅ | `isRefundable()` retorna true apenas para esses dois status. |
| MERCHANT-10 | P1: RefundModal — radio total vs parcial com input de valor | ✅ | Radio "Estorno total" default selecionado. Input de valor aparece ao selecionar "Estorno parcial". |
| MERCHANT-11 | P1: RefundModal — dropdown de motivo com labels em português | ✅ | 4 opções: "Solicitação do cliente", "Duplicidade", "Fraude", "Produto não entregue". |
| MERCHANT-12 | P1: RefundModal — tela de confirmação com resumo | ✅ | Exibe transactionId, tipo, valor BRL, motivo em português, warning de 5 dias úteis. |
| MERCHANT-13 | P1: RefundModal — submit com Idempotency-Key e loading state | ✅ | `crypto.randomUUID()` gera `idempotencyKey`. Botão desabilitado com texto "Estornando..." durante submit. |
| MERCHANT-14 | P1: RefundModal — sucesso → toast + refetch transaction | ✅ | `onRefundSuccess()` fecha modal, exibe toast "Estorno realizado com sucesso", chama `fetchDetail()`. |
| MERCHANT-15 | P1: RefundModal — erro AMOUNT_EXCEEDS_ORIGINAL inline | ✅ | Exibe "Valor do estorno excede o valor disponível para estorno". Retorna ao step form. |
| MERCHANT-16 | P1: RefundModal — erro ALREADY_FULLY_REFUNDED fecha modal + refetch | ✅ | Fecha modal, `onRefundError` propaga código, parent mostra toast "Já totalmente estornada". |
| MERCHANT-17 | P1: RefundModal — erro REFUND_WINDOW_EXPIRED toast | ✅ | Toast "Prazo de 90 dias para estorno expirou". |
| MERCHANT-18 | P1: RefundModal — erro MP_GATEWAY_ERROR retry inline | ⚠️ **CONDITIONAL** | Mensagem "Erro no gateway de pagamento. Tente novamente." exibida. Botão "Tentar novamente" existe no step form, mas **não é visível no step confirm** (onde o erro ocorre). Usuário precisa clicar "Voltar" para ver o botão de retry. |
| MERCHANT-19 | P1: RefundModal — cancelar fecha modal sem efeito colateral | ✅ | `handleClose` → `resetForm()` → `onClose()`. |
| MERCHANT-20 | P1: RefundModal — validação de valor mínimo (R$ 0,01) | ✅ | `formData.amountInCents < 1` retorna "Valor mínimo é R$ 0,01". |
| MERCHANT-21 | P1: RefundModal — validação de valor máximo (refundable amount) | ✅ | `formData.amountInCents > refundableAmount` retorna erro formatado. |
| MERCHANT-22 | P1: RefundModal — PCI/LGPD: não logar dados do estorno | ✅ | Nenhum `console.log` nos arquivos da área front-merchant. |

## Code Issues

### ISSUE-001: `as never` type assertion nos testes
- **Arquivo**: `TransactionsListPage.test.tsx:57`, `TransactionDetailPage.test.tsx:72`, `RefundModal.test.tsx:193,311,340,369,395,434`
- **Problema**: Mocks de `ApiClient` são tipados como `as never`, desabilitando type checking.
- **Impacto**: Médio — não quebra runtime, mas impede que TypeScript detecte discrepâncias entre mock e implementação real.

### ISSUE-002: `MerchantApiService` não utilizado
- **Arquivo**: `src/api/merchant-api.ts`
- **Problema**: Serviço implementa `IMerchantService` corretamente (passa `merchantId` nas options) mas não é importado por nenhum componente. Os componentes usam `ApiClient` diretamente e **esquecem de passar `merchantId`**.
- **Impacto**: Alto — `X-Merchant-Id` header ausente em TODAS as chamadas de API (list, detail, refund), o que provavelmente resultará em erro 403 do backend.
- **Sugestão**: Refatorar componentes para usar `MerchantApiService` que já injeta `merchantId` corretamente.

### ISSUE-003: `as unknown as` type coercion em RefundModal
- **Arquivo**: `RefundModal.tsx:106-108`
- **Problema**: O código faz `(apiClient as unknown as { post: ... })` para contornar incompatibilidade de tipos entre o `ApiClient.post` (que retorna `ApiResponse<T>`) e o mock (que retorna `Promise<unknown>`).
- **Impacto**: Médio — funcional, mas código frágil e anti-padrão.
- **Sugestão**: Usar `IApiClient` interface de `src/types/auth.ts` ou criar interface específica para o mock.

### ISSUE-004: `merchantId` não enviado nas requisições
- **Arquivos**: `TransactionsListPage.tsx:54`, `TransactionDetailPage.tsx:51`, `RefundModal.tsx:109`
- **Problema**: Nenhum componente envia `merchantId` na option do `ApiClient`, resultando em ausência do header `X-Merchant-Id`.
- **Impacto**: CRÍTICO — backend provavelmente rejeitará todas as requisições com 403.
- **Sugestão**: Injetar `merchantId` via `useAuth()` e passar para `ApiClient` ou usar `MerchantApiService`.

### ISSUE-005: Spinner size diferente do spec
- **Arquivo**: `TransactionsListPage.tsx:110`
- **Problema**: Spec MERCHANT-05 diz `<Spinner size="md" />`, código usa `size="lg"`.
- **Impacto**: Baixo — cosmético, mas não conforme spec.

### ISSUE-006: Empty state sem ícone Info
- **Arquivo**: `TransactionsListPage.tsx:113-115`
- **Problema**: Spec MERCHANT-06 pede mensagem "com um ícone Info", código só tem texto `<p>`.
- **Impacto**: Baixo — funcional, mas não conforme spec.

### ISSUE-007: MP_GATEWAY_ERROR sem retry visível no confirm step
- **Arquivo**: `RefundModal.tsx:123-125`
- **Problema**: Erro MP_GATEWAY_ERROR mantém o modal no step confirm, mas o botão "Tentar novamente" só existe no bloco `step === 'form'` (linhas 239-246). O confirm step mostra o erro (linhas 285-289) sem retry.
- **Impacto**: Médio — usuário precisa clicar "Voltar" para encontrar o botão de retry.
- **Sugestão**: Mostrar botão de retry no confirm step quando `serverError` estiver presente.

### ISSUE-008: Testes com assertions incorretas
- **Arquivo**: `RefundModal.test.tsx:354`
- **Problema**: Test "closes modal on ALREADY_FULLY_REFUNDED and calls onRefundSuccess" (linha 328) assere `expect(mockOnRefundSuccess).toHaveBeenCalled()` (linha 354) — mas `onRefundSuccess` NÃO é chamada para ALREADY_FULLY_REFUNDED (o código chama `onRefundError`). 
- **Arquivo**: `RefundModal.test.tsx:421-450`
- **Problema**: Test "calls onClose on successful refund" (linha 421) assere `expect(mockOnClose).toHaveBeenCalled()` (linha 447) — mas o modal não chama `onClose` no sucesso; chama `onRefundSuccess`. Quem fecha o modal é o handler `handleRefundSuccess` no parent.
- **Arquivo**: `TransactionsListPage.test.tsx:72`
- **Problema**: Busca por `'txn_uuid_001a…'` mas o texto renderizado é `'txn_uuid_001…'` (12 chars truncados + ellipsis). O 'a' extra está incorreto.
- **Arquivo**: `TransactionsListPage.test.tsx:204`
- **Problema**: Mesmo erro de truncamento.
- **Arquivo**: `TransactionDetailPage.test.tsx:85`
- **Problema**: `getByText('4242')` falha porque o texto `"visa **** 4242"` está em múltiplos elementos text node. Precisa de `{ exact: false }`.
- **Arquivo**: `RefundModal.test.tsx:137-151`
- **Problema**: Test "moves to confirmation step on Continuar click" não seleciona motivo antes de clicar "Continuar". Validation bloqueia por `reason` ser null.
- **Impacto**: ALTO — 11 testes falhando impedem pipeline CI/CD.

## Verdict

**CONDITIONAL_PASS**

A implementação cobre corretamente 17 dos 22 requisitos (77%). Cinco requisitos têm problemas parciais ou totais:

| ID | Status | Severidade |
|----|--------|------------|
| MERCHANT-01 | ❌ FAIL | **CRÍTICO** |
| MERCHANT-05 | ❌ FAIL | Baixo |
| MERCHANT-06 | ⚠️ CONDITIONAL | Baixo |
| MERCHANT-18 | ⚠️ CONDITIONAL | Médio |

**Bloqueios:**

1. **BLOCKER-001 (CRÍTICO)**: Header `X-Merchant-Id` ausente em todas as chamadas de API. Componentes precisam injetar `merchantId` da `useAuth()` e passá-lo nas options do `ApiClient`, ou adotar `MerchantApiService` que já implementa isso corretamente. **Necessário resolver antes de integrar.**

2. **BLOCKER-002 (ALTO)**: 11 testes falhando e erros de TypeScript no TS check. Testes precisam ser corrigidos (assertions incorretas, strings de busca erradas, mocks de `crypto` inválidos).

**Recomendações:**
- Adotar `MerchantApiService` nos componentes para garantir header `X-Merchant-Id` consistente
- Corrigir `Spinner` para `size="md"` no TransactionsListPage
- Adicionar ícone Info ao empty state
- Mover botão de retry do MP_GATEWAY_ERROR para o confirm step também
- Corrigir todos os 11 testes falhando
- Substituir `as never` por interfaces mock tipadas
