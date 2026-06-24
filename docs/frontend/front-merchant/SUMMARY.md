# Documentation — front-merchant

**Documenter:** agent-documenter
**Date:** 2026-06-24
**Cycle:** 4

---

## What Was Built

| Arquivo | Propósito |
|---------|-----------|
| `src/types/merchant.ts` | Tipos `TransactionSummary`, `TransactionDetail`, `RefundRequest`, `RefundResponse`, `RefundFormData`, `RefundFormErrors`, `TransactionFilters`, `PaginatedResponse`; enums `TransactionStatus`, `RefundReason`; mapas `STATUS_BADGE_CLASSES`, `STATUS_LABELS`, `REFUND_REASON_LABELS` |
| `src/api/merchant-api.ts` | `MerchantApiService` com métodos `listTransactions()`, `getTransactionDetail()`, `submitRefund()`; injeta headers `X-Merchant-Id` e `Idempotency-Key` |
| `src/pages/merchant/TransactionsListPage.tsx` | Página de listagem paginada de transações com tabela, formatador BRL, badges de status, paginação (Anterior/Próximo), loading state (Spinner), empty state, error state (ErrorMessage com retry), navegação ao clicar na linha |
| `src/pages/merchant/TransactionDetailPage.tsx` | Página de detalhe da transação com grid de informações, lista de estornos existentes, botão "Estornar" condicional (APROVADA/PARCIALMENTE_ESTORNADA), toasts de sucesso/erro/info, integração com RefundModal |
| `src/pages/merchant/RefundModal.tsx` | Modal de estorno em 2 etapas: formulário (radio total/parcial, input de valor com validação BRL, dropdown de motivo) → confirmação (resumo + warning 5 dias úteis); submit com Idempotency-Key; tratamento de erros específicos (AMOUNT_EXCEEDS_ORIGINAL, ALREADY_FULLY_REFUNDED, REFUND_WINDOW_EXPIRED, MP_GATEWAY_ERROR) |

## Tests Created

| Arquivo | Tipo | Qtde testes | Cobertura |
|---------|------|-------------|-----------|
| `src/pages/merchant/TransactionsListPage.test.tsx` | Component | 11 | Loading state, renderização de linhas, BRL formatting, status badge colors, card brand + last 4, empty state, error + retry, paginação visível, paginação navega, clique na linha navega, botão Anterior desabilitado na página 1, data formatada |
| `src/pages/merchant/TransactionDetailPage.test.tsx` | Component | 8 | Loading state, renderização de detalhes, botão Estornar visível para APPROVED, botão Estornar visível para PARTIALLY_REFUNDED, botão oculto para DECLINED, error + retry, abre RefundModal, fecha RefundModal |
| `src/pages/merchant/RefundModal.test.tsx` | Component | 20 | Título, não renderiza quando fechado, refundable amount (com estornos existentes), radio total default, input parcial aparece, dropdown de motivos, confirmação, warning 5 dias, Cancelar fecha, loading no submit, validação motivo obrigatório, validação valor obrigatório, validação mínimo R$ 0,01, validação máximo, AMOUNT_EXCEEDS_ORIGINAL inline, ALREADY_FULLY_REFUNDED fecha modal, REFUND_WINDOW_EXPIRED fecha modal, MP_GATEWAY_ERROR retry inline, sucesso fecha + callback, Voltar do confirm para form |

**Total de testes:** 39

## Mocked Functions

| Função mockada | Interface original | Motivo | Localização do mock | Comportamento do mock | Status |
|---------------|-------------------|--------|--------------------|-----------------------|--------|
| `useAuth` | `IAuthContext` (front-shared) | Isolar testes de autenticação | Inline em cada test file | Retorna `{ user, token, isAuthenticated: true }` | ✅ Inline |
| `apiClient.get` | `IApiClient.get` (front-shared) | Simular respostas da API sem backend | `createMockClient()` em cada test file | Retorna dados mockados de `TransactionSummary[]`, `TransactionDetail`, ou rejeita com erro | ✅ Inline |
| `apiClient.post` | `IApiClient.post` (front-shared) | Simular submit de estorno | `createMockClient()` no RefundModal.test.tsx | Resolve com `RefundResponse` ou rejeita com `ApiErrorResponse` específico | ✅ Inline |

**Observação:** Os mocks são criados inline nos arquivos de teste (não reutilizam `src/__mocks__/apiClient.ts`) porque cada teste precisa de comportamento específico (resolver com dados vs. rejeitar com códigos de erro).

## Technical Decisions

| Decisão | Opção | Motivo |
|---------|-------|--------|
| MerchantApiService | Classe separada com injeção de ApiClient + merchantId + userId | Encapsula lógica de API. Facilita testes injetando mock client. |
| PaginatedResponse | Tipo genérico definido em `types/merchant.ts` | Reutilizável para qualquer endpoint paginado. |
| RefundModal duas etapas | State machine `form → confirm → submit` | UX segura: obriga revisão antes de confirmar estorno. Atende requisito de confirmação em 2 etapas. |
| Toasts inline | Estado local no TransactionDetailPage com auto-dismiss 5s | Simples e testável. Evita dependência de biblioteca de toast. |
| Injeção de ApiClient via props | Prop opcional `apiClient` com fallback para `new ApiClient(() => token)` | Testabilidade: permite injetar mock sem mockar módulo. |
| ID de estorno opaco | `errorCode` via callback em vez de prop drilling | RefundModal notifica DetailPage de erros específicos via `onRefundError` sem expor lógica de toast. |
| PCI/LGPD compliance | Nenhum `console.log` de dados sensíveis | Atende MERCHANT-22. Ausência de logging de transactionId, amount, reason no código. |

## Requirements Status

| ID | Story | Status |
|----|-------|--------|
| MERCHANT-01 | P1: TransactionsList — fetch paginado com headers de auth e merchant | ✅ Implemented |
| MERCHANT-02 | P1: TransactionsList — renderização de tabela com BRL, status badge, card info | ✅ Implemented |
| MERCHANT-03 | P1: TransactionsList — cores de status badge por status | ✅ Implemented |
| MERCHANT-04 | P1: TransactionsList — paginação (anterior/próximo/indicador de página) | ✅ Implemented |
| MERCHANT-05 | P1: TransactionsList — loading state com Spinner | ✅ Implemented |
| MERCHANT-06 | P1: TransactionsList — empty state "Nenhuma transação encontrada" | ✅ Implemented |
| MERCHANT-07 | P1: TransactionsList — error state com ErrorMessage e retry | ✅ Implemented |
| MERCHANT-08 | P1: TransactionsList — clique na linha navega para detail | ✅ Implemented |
| MERCHANT-09 | P1: RefundModal — botão "Estornar" visível apenas para APPROVED/PARTIALLY_REFUNDED | ✅ Implemented |
| MERCHANT-10 | P1: RefundModal — radio total vs parcial com input de valor | ✅ Implemented |
| MERCHANT-11 | P1: RefundModal — dropdown de motivo com labels em português | ✅ Implemented |
| MERCHANT-12 | P1: RefundModal — tela de confirmação com resumo | ✅ Implemented |
| MERCHANT-13 | P1: RefundModal — submit com Idempotency-Key e loading state | ✅ Implemented |
| MERCHANT-14 | P1: RefundModal — sucesso → toast + refetch transaction | ✅ Implemented |
| MERCHANT-15 | P1: RefundModal — erro AMOUNT_EXCEEDS_ORIGINAL inline | ✅ Implemented |
| MERCHANT-16 | P1: RefundModal — erro ALREADY_FULLY_REFUNDED fecha modal + refetch | ✅ Implemented |
| MERCHANT-17 | P1: RefundModal — erro REFUND_WINDOW_EXPIRED toast | ✅ Implemented |
| MERCHANT-18 | P1: RefundModal — erro MP_GATEWAY_ERROR retry inline | ✅ Implemented |
| MERCHANT-19 | P1: RefundModal — cancelar fecha modal sem efeito colateral | ✅ Implemented |
| MERCHANT-20 | P1: RefundModal — validação de valor mínimo (R$ 0,01) | ✅ Implemented |
| MERCHANT-21 | P1: RefundModal — validação de valor máximo (refundable amount) | ✅ Implemented |
| MERCHANT-22 | P1: RefundModal — PCI/LGPD: não logar dados do estorno | ✅ Implemented |

## Test Commands

```bash
# Rodar todos os testes de front-merchant
npm run test:run -- src/pages/merchant/ src/api/ src/types/merchant.ts

# Rodar com cobertura
npm run test:coverage -- src/pages/merchant/

# Typecheck
npx tsc --noEmit
```

## Next Cycle Input

### Dependências não resolvidas
Nenhuma. front-merchant depende de front-shared (IApiClient, IAuthContext, componentes UI), que já estão implementados e disponíveis.

### Bloqueios ativos
Nenhum bloqueio crítico.

### Sugestões
1. **ISSUE-002**: O mock de `AuthContext` e `apiClient` nos testes de front-merchant é inline; considerar migrar para os mocks compartilhados em `src/__mocks__/` se houver necessidade de reuso entre áreas.
2. **MERCHANT-ENH-001**: Adicionar filtro por status na TransactionsListPage (conforme `TransactionFilters.status` definido em spec mas não implementado na UI).
3. Considerar mover `PaginatedResponse` para `front-shared` como tipo compartilhado entre áreas.

### Mocks pendentes
Nenhum. Testes usam mocks inline funcionais.

### Issues conhecidas
- **ISSUE-002**: Mocks inline vs. reutilizáveis — severidade baixa, não bloqueante. Os mocks inline são adequados para os testes atuais, mas replicam lógica de `createMockClient()` em 3 arquivos.
