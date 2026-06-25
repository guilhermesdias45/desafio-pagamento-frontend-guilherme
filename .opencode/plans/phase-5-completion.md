# Phase 5 — Plano de Conclusão

**Última atualização:** 2026-06-25
**Stack:** React 18 + TypeScript + Vite 5 + Vitest + RTL + Tailwind CSS 3
**Pipeline:** AGENTS.md → STATE.md (Blocadores: B01–B08)

---

## Critérios de Sucesso (Definition of Done)

1. **App.tsx** — 0 erros de TypeScript, todas as páginas recebem dependências corretamente
2. **AuthProvider** — envolvendo as rotas, `useAuth()` funcional em toda árvore
3. **CardForm** — sem dependência de `window.*`, props injetadas via DIP
4. **Interfaces** — `IAuthContext` e real `AuthContext` alinhadas; `IApiClient.get()` compatível
5. **Testes** — 0 falhas (pré-existentes + novas resolvidas)
6. **Build** — `tsc --noEmit` + `vite build` limpos
7. **Docker** — Dockerfile + nginx.conf + docker-compose integrado
8. **E2E** — Playwright configurado com ao menos 1 spec de smoke test

---

## Tarefas

### 🔴 PRIORIDADE 1 — Crash em Runtime (B01–B05)

Estas tarefas impedem o app de funcionar. Qualquer rota afetada quebra na inicialização.

| # | Tarefa | Arquivos | Critério de Verificação |
|---|--------|----------|------------------------|
| P1.1 | Envolver `<Routes>` com `<AuthProvider>` no `App.tsx` | `src/App.tsx` | `useAuth()` em qualquer página não lança erro |
| P1.2 | Tornar `apiClient` opcional em páginas auth que exigem como required | `src/pages/auth/LoginPage.tsx`, `RegisterPage.tsx`, `ConfirmEmailPage.tsx`, `TwoFactorVerifyPage.tsx`, `TwoFactorSetupPage.tsx` | TypeScript sem erro, página renderiza com `apiClient` undefined |
| P1.3 | Mudar `OrderDetailPage` para ler `orderId` de `useParams()` em vez de prop required | `src/pages/orders/OrderDetailPage.tsx` | Rota `/orders/:orderId` funciona sem prop |
| P1.4 | Mudar `TransactionDetailPage` para ler `transactionId` de `useParams()` | `src/pages/merchant/TransactionDetailPage.tsx` | Rota `/merchant/transactions/:txnId` funciona sem prop |
| P1.5 | Criar wrapper stateful para `PaymentResult` que gerencia `result`/callbacks internamente | `src/pages/checkout/PaymentResult.tsx` (ou wrapper) | Rota `/checkout/result` renderiza sem prop |
| P1.6 | Remover import morto de `RefundModal` em App.tsx | `src/App.tsx` | Sem erro de lint/TS |

### 🟡 PRIORIDADE 2 — Integridade Técnica (B06–B08)

Problemas de arquitetura e contratos de interface. Não crasham o app imediatamente, mas comprometem testabilidade, segurança e manutenibilidade.

| # | Tarefa | Arquivos | Critério de Verificação |
|---|--------|----------|------------------------|
| P2.1 | Substituir `window.__AUTH_TOKEN__` por prop `authToken` no CardForm | `src/pages/checkout/CardForm.tsx` | Nenhum `window.__AUTH_TOKEN__` no código |
| P2.2 | Substituir `window.MercadoPago` por prop `mercadoPagoInstance` | `src/pages/checkout/CardForm.tsx` | `window.MercadoPago` removido do CardForm |
| P2.3 | Alinhar `IAuthContext` com `AuthContext` real (ou remover interface duplicada) | `src/types/auth.ts`, `src/contexts/AuthContext.tsx` | Props `LoginPage`/`TwoFactorVerifyPage` aceitam `AuthContext` real sem `as never` |
| P2.4 | Alinhar `IApiClient.get()` com real (mudar retorno para `Promise<T>` unwrapped) | `src/types/auth.ts` (interface) | MockClient sem `as never` nos testes |
| P2.5 | Substituir `as never` nos testes por mock factories que casam com interfaces reais | 7 arquivos de teste | Nenhum `as never` em arquivos de teste |

### 🟡 PRIORIDADE 3 — Qualidade e Confiabilidade

Falhas de teste e accessibility que reduzem a confiança no pipeline.

| # | Tarefa | Arquivos | Critério de Verificação |
|---|--------|----------|------------------------|
| P3.1 | Ajustar contraste da paleta (primary #5B8DEE) para WCAG AA ≥ 4.5:1 | `src/lib/contrast.test.ts`, `src/lib/palette.ts` | Teste `contrast.test.ts` passa |
| P3.2 | Corrigir `AuthContext.test.tsx` — asserções de `isLoading` e redirect não correspondem ao código real | `src/contexts/AuthContext.test.tsx` | 3 testes passam |
| P3.3 | Corrigir `ConfirmEmailPage.test.tsx` — interação timer/Promise causa timeouts | `src/pages/auth/ConfirmEmailPage.test.tsx` | 5 testes passam sem timeout |
| P3.4 | Rodar `tsc --noEmit` e zerar erros | `src/App.tsx` | `npx tsc --noEmit` → 0 erros |
| P3.5 | Rodar `vitest run` e verificar 0 falhas | — | `vitest run` → 240/240 pass |

### 🟢 PRIORIDADE 4 — Deploy e E2E

Infraestrutura de container e testes de integração.

| # | Tarefa | Arquivos | Critério de Verificação |
|---|--------|----------|------------------------|
| P4.1 | Criar `Dockerfile` para build estático (multi-stage: node → nginx) | `Dockerfile` (raiz) | `docker build -t frontend .` sucede |
| P4.2 | Criar `nginx.conf` para SPA (fallback index.html) | `nginx.conf` | Container serve app e rotas funcionam |
| P4.3 | Adicionar serviço frontend no `docker-compose.yml` | `docker-compose.yml` | `docker compose up` inclui frontend |
| P4.4 | Instalar Playwright e criar config | `package.json`, `playwright.config.ts` | `npx playwright test` roda sem erro |
| P4.5 | Criar smoke test E2E (login → criar pedido → checkout) | `e2e/smoke.spec.ts` | Teste cobre fluxo crítico |

---

## Dependências Entre Tarefas

```
P1.1 ──→ P1.2 ──→ P3.4 (tsc errors)
                    ↓
P1.3 ──→ P3.4 ──→ P3.5 (testes integrados)
P1.4 ──→ P3.4
P1.5 ──→ P3.4
P1.6 ──→ P3.4

P2.1 ──→ P3.5 (testes CardForm)
P2.2 ──→ P3.5
P2.3 ──→ P3.5 ──→ P2.5 (remover as never)
P2.4 ──→ P3.5 ──→ P2.5

P3.1 ──→ P3.5
P3.2 ──→ P3.5
P3.3 ──→ P3.5

P3.5 ──→ P4.1, P4.2, P4.3 (Docker após testes verdes)
P4.1, P4.2 ──→ P4.3
P4.4, P4.5 ──→ P3.5 (E2E após unitários verdes)
```

---

## Execução Sugerida

1. **Primeiro bloco:** P1.1 → P1.2 → P1.3 → P1.4 → P1.5 → P1.6 (resolve 5 crashes + TS errors)
2. **Segundo bloco:** P2.1 → P2.2 → P2.3 → P2.4 (alinha interfaces)
3. **Terceiro bloco:** P3.1 → P3.2 → P3.3 (corrige testes) + P2.5 (remove `as never`)
4. **Validação:** P3.4 (tsc) + P3.5 (vitest)
5. **Infra:** P4.1 → P4.2 → P4.3 (Docker) + P4.4 → P4.5 (E2E)

---

## Bloqueadores Atuais

Ver STATE.md §Active Blockers (B01–B08).

| Bloqueador | Resolvido Por |
|------------|---------------|
| B01 | P1.1 |
| B02 | P1.2 |
| B03 | P1.3 |
| B04 | P1.4 |
| B05 | P1.5 |
| B06 | P2.1 + P2.2 |
| B07 | P2.3 |
| B08 | P2.4 |
