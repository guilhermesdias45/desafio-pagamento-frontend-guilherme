# State — Frontend Pipeline

**Last updated:** 2026-06-22

## Current Cycle

| Cycle | Phase | Focus | Status |
|-------|-------|-------|--------|
| 1 | Setup | Pipeline structure (AGENTS.md + agents + templates + codebase + project) | ✅ Complete |
| 2 | Analyse | Foundation (front-shared) + Auth (front-auth) | ✅ Complete |
| 2 | Code | Foundation (front-shared) + Auth (front-auth) | 🔄 In Progress |
| 3 | — | Order + Checkout | Pending |
| 4 | — | Merchant | Pending |
| 5 | — | Integration + E2E | Pending |

## Area Status

| Area | Spec | Code | Review | Docs | Mocked? |
|------|------|------|--------|------|---------|
| front-shared | 🔲 | 🔲 | 🔲 | 🔲 | — |
| front-auth | 🔲 | 🔲 | 🔲 | 🔲 | 🔲 (shared) |
| front-order | 🔲 | 🔲 | 🔲 | 🔲 | 🔲 (auth) |
| front-checkout | 🔲 | 🔲 | 🔲 | 🔲 | 🔲 (order) |
| front-merchant | 🔲 | 🔲 | 🔲 | 🔲 | 🔲 (auth) |

## Active Blockers

| ID | Area | Description | Status |
|----|------|-------------|--------|
| — | — | — | — |

## Decisions Log

| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-06-22 | Pipeline: 4-agent cycle per area | SDD + TDD, paralelismo com mocks |
| 2026-06-22 | Testes: Vitest + RTL (unit), Playwright (E2E final) | Stack alinhada com Vite |
| 2026-06-22 | Templates: external em `.opencode/templates/` | Modularidade |

## Preferences

- **Tasks leves** (STATE update, handoff): funcionam bem com modelos menores/rápidos
- **Tarefas pesadas** (brownfield mapping, specs): exigem modelo completo (big-pickle)

## Deferred Ideas

- Integração com `@opencode-ai/plugin` para comandos custom `/frontend:*`
- Dashboard com métricas de progresso do pipeline
