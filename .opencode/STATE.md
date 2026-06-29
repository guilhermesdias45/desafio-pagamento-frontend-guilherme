# State — Frontend Pipeline

**Last updated:** 2026-06-29

## Current Cycle

| Cycle | Phase | Focus | Status |
|-------|-------|-------|--------|
| 1 | Setup | Pipeline structure (AGENTS.md + agents + templates + codebase + project) | ✅ Complete |
| 2 | Analyse | Foundation (front-shared) + Auth (front-auth) | ✅ Complete |
| 2 | Code | Foundation (front-shared) + Auth (front-auth) | ✅ Complete |
| 3 | Analyse | Order + Checkout | ✅ Complete |
| 3 | Code | Order + Checkout | ✅ Complete |
| 4 | Analyse | Merchant | ✅ Complete |
| 4 | Code | Merchant | ✅ Complete |
| 5 | Integration | Phase 5 — all items | ✅ Complete |

## Area Status

| Area | Spec | Code | Review | Docs | Mocked? |
|------|------|------|--------|------|---------|
| front-shared | ✅ | ✅ | ✅ | ✅ | — |
| front-auth | ✅ | ✅ | ✅ | ✅ | ✅ (shared) |
| front-order | ✅ | ✅ | ✅ | ✅ | ✅ (auth) |
| front-checkout | ✅ | ✅ | ✅ | ✅ | ✅ (order) |
| front-merchant | ✅ | ✅ | ✅ | ✅ | ✅ (auth) |

## Phase 5 — Integration Checklist

| Item | Status |
|------|--------|
| P1.1 — AuthProvider wraps Routes in App.tsx | ✅ |
| P1.2 — AuthDepsWrapper injects adapted deps into auth pages | ✅ |
| P1.3 — OrderDetailPage/TransactionDetailPage useParams fallback | ✅ |
| P1.4 — PaymentResultWrapper reads location.state | ✅ |
| P1.5 — Remove dead imports | ✅ |
| P1.6 — Normalize auth page redirect paths (no `/auth/` prefix) | ✅ |
| P2.1 — Remove `window.MercadoPago` from CardForm (receive via prop) | ✅ |
| P2.2 — Update CardForm tests (createMockMercadoPago helper) | ✅ |
| P2.3 — Wire CheckoutPage to create/pass mercadoPagoInstance | ✅ |
| P3.1 — Fix primary color contrast (#5B8DEE → #3366CC) | ✅ |
| P3.2 — Fix AuthContext tests (correct fetch mock counts) | ✅ |
| P3.3 — Fix ConfirmEmailPage tests (remove fake timers) | ✅ |
| P4.1 — Docker: Dockerfile, nginx.conf, docker-compose frontend service | ✅ |
| P4.2 — Playwright E2E: setup, login smoke tests, checkout smoke test | ✅ |
| STATE.md update | 🔄 In Progress |

## Verification Results

| Check | Result |
|-------|--------|
| `tsc --noEmit` | **0 errors** ✅ |
| `vite build` | **60 modules** ✅ |
| `vitest run` | **240/240 passing** ✅ |
| `npm run test:e2e` | ⬜ Not yet run |

## Active Blockers

**None.** All B01–B08 resolved.

## Decisions Log

| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-06-22 | Pipeline: 4-agent cycle per area | SDD + TDD, paralelismo com mocks |
| 2026-06-22 | Testes: Vitest + RTL (unit), Playwright (E2E final) | Stack alinhada com Vite |
| 2026-06-22 | Templates: external em `.opencode/templates/` | Modularidade |
| 2026-06-29 | Adapter over rewrite: AuthDepsWrapper | Zero changes to auth page logic |
| 2026-06-29 | mercadoPagoInstance injection via prop (DIP) | CardForm no longer reads window globals |
| 2026-06-29 | Redirect on any refresh failure | Safe: first-time visitors get 401 anyway |

## Preferences

- **Tasks leves** (STATE update, handoff): funcionam bem com modelos menores/rápidos
- **Tarefas pesadas** (brownfield mapping, specs): exigem modelo completo (big-pickle)

## Deferred Ideas

- Integração com `@opencode-ai/plugin` para comandos custom `/frontend:*`
- Dashboard com métricas de progresso do pipeline
- Cobertura de testes Playwright mais completa (fluxo completo de pagamento)
