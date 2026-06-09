# Sprint 5 — Contas de Teste MercadoPago

**Spec:** [spec.md](spec.md) | **Plano:** [plan.md](plan.md) | **Tasks:** [tasks.md](tasks.md)
**Responsável:** Dev 1 | **Status:** Em andamento

---

## 1. Objetivo

Registrar duas contas de teste do MercadoPago (seller + buyer) na base de dados do payment-service,
permitindo que no futuro os pagamentos transacionem saldo real entre essas contas dentro do ambiente
de testes do MP.

## 2. Escopo (Fase 1)

- Criar tabela `mp_test_accounts` para armazenar credenciais das contas MP
- Inserir manualmente as duas contas via script SQL
- Adicionar overload em `MercadoPagoGateway` que aceita `sellerAccessToken` via `RequestOptions`
- Preparar `TransactionService` para usar `test@testuser.com` como `payer.email`
- Arquitetura pronta para Fase 2 (OAuth + token seller)

## 3. Contas de Teste

| Conta | User ID | Usuário | Senha | Código Verificação |
|-------|---------|---------|-------|--------------------|
| **Seller** | 3459882808 | TESTUSER1504687285327688180 | d5Poral76w | 882808 |
| **Buyer** | 3459473280 | TESTUSER2899368672786037940 | 9mGCDnDaY3 | 473280 |

## 4. Tasks

| # | Tarefa | Tipo | Status |
|---|--------|------|--------|
| T1 | Atualizar spec §9 — Contas de Teste MP | Spec | ⬜ |
| T2 | Atualizar plan — tabela, configs, riscos | Plan | ⬜ |
| T3 | V6 Migration — `mp_test_accounts` | Infra | ⬜ |
| T4 | Entidade `MpTestAccount` + enum `MpAccountType` | Code | ⬜ |
| T5 | Repository `MpTestAccountRepository` | Code | ⬜ |
| T6 | DTO `MpTestAccountResponse` | Code | ⬜ |
| T7 | **[TEST]** `MpTestAccountRepositoryTest` | Test | ⬜ |
| T8 | **[TEST]** `MpTestAccountEntityTest` | Test | ⬜ |
| T9 | `MercadoPagoGateway` overload com seller token | Code | ⬜ |
| T10 | **[TEST]** `MercadoPagoGatewayTest` — overload | Test | ⬜ |
| T11 | `TransactionService` — usar `test@testuser.com` | Code | ⬜ |
| T12 | **[TEST]** `TransactionServiceTest` — atualizar | Test | ⬜ |
| T13 | Script SQL manual `insert-mp-test-accounts.sql` | Infra | ⬜ |
| T14 | Validar cobertura JaCoCo ≥ 90% | Validate | ⬜ |

## 5. Decisões Técnicas

| Decisão | Escolha | Motivo |
|---------|---------|--------|
| Armazenamento | Tabela `mp_test_accounts` no schema payment_service | Isolamento por serviço |
| Inserção | Script SQL manual executado pelo usuário | Única forma permitida |
| Token seller | Null na Fase 1; OAuth na Fase 2 | Sem Client Secret disponível |
| payer.email | `test@testuser.com` (hardcoded) | Único email aceito pelo MP em modo de teste |
| Overload gateway | `createPayment()` com `@Nullable sellerAccessToken` | Compatibilidade retroativa |
