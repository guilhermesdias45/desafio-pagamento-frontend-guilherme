# Frontend Pipeline — 4-Agent Cycle

**Modelo:** opencode/big-pickle
**Metodologia:** SDD + TDD (Spec-Driven Development + Test-Driven Development)
**Stack:** React 18 + TypeScript + Vite 5 + Vitest + RTL

---

## Pipeline

Cada área do frontend passa por 4 agentes em sequência. Todas as áreas rodam em paralelo.

```
Phase 1: ANALYSE (paralelo)
  ┌─ agent-analyst ─┐ ┌─ agent-analyst ─┐ ┌─ agent-analyst ─┐ ┌─ agent-analyst ─┐ ┌─ agent-analyst ─┐
  │   front-shared  │ │   front-auth    │ │   front-order   │ │  front-checkout │ │  front-merchant │
  └──── spec.md ────┘ └──── spec.md ────┘ └──── spec.md ────┘ └──── spec.md ────┘ └──── spec.md ────┘

Phase 2: CODE — TDD (paralelo, mocks qdo necessário)
  ┌─ agent-coder ───┐ ┌─ agent-coder ───┐ ┌─ agent-coder ───┐ ┌─ agent-coder ───┐ ┌─ agent-coder ───┐
  │   front-shared  │ │   front-auth    │ │   front-order   │ │  front-checkout │ │  front-merchant │
  │                 │ │ (moka shared)   │ │ (moka auth)     │ │ (moka order)    │ │ (moka auth)     │
  └──── código ─────┘ └──── código ─────┘ └──── código ─────┘ └──── código ─────┘ └──── código ─────┘

Phase 3: REVIEW (paralelo)
  ┌─ agent-reviewer ┐ ┌─ agent-reviewer ┐ ┌─ agent-reviewer ┐ ┌─ agent-reviewer ┐ ┌─ agent-reviewer ┐
  │   front-shared  │ │   front-auth    │ │   front-order   │ │  front-checkout │ │  front-merchant │
  └─── REVIEW.md ───┘ └─── REVIEW.md ───┘ └─── REVIEW.md ───┘ └─── REVIEW.md ───┘ └─── REVIEW.md ───┘

Phase 4: DOCUMENT (paralelo)
  ┌─ agent-documenter ┐ ┌─ agent-documenter ┐ ┌─ agent-documenter ┐ ┌─ agent-documenter ┐ ┌─ agent-documenter ┐
  │   front-shared    │ │   front-auth      │ │   front-order     │ │  front-checkout  │ │  front-merchant   │
  └─── SUMMARY.md ────┘ └─── SUMMARY.md ────┘ └─── SUMMARY.md ────┘ └─── SUMMARY.md ────┘ └─── SUMMARY.md ────┘

Phase 5: INTEGRATE (sequencial)
  → Resolver NEEDS_HUMAN → Resolver mocks → Build check → E2E tests → Atualizar STATE.md
```

## Orquestração

### Como iniciar um ciclo

O operador chama: `/start-frontend-cycle`

O orquestrador então:

1. Lê `.opencode/STATE.md` para saber o ciclo atual
2. Lê `.specs/project/ROADMAP.md` para saber quais áreas executar
3. Lança **Phase 1**: 5 Analysts via task tool (paralelo)
4. Lança **Phase 2**: 5 Coders via task tool (paralelo)
5. Lança **Phase 3**: 5 Reviewers via task tool (paralelo)
6. Lança **Phase 4**: 5 Documenters via task tool (paralelo)
7. Executa **Phase 5**: integração sequencial

### Áreas

| Slug | O que faz | Depende de |
|------|-----------|------------|
| `front-shared` | API client, AuthContext, tipos, layout, guards, scaffold | Nada |
| `front-auth` | Register, Login, 2FA, ConfirmEmail | front-shared (moka) |
| `front-order` | CreateOrder, OrderHistory, OrderDetail | front-auth + front-shared (moka) |
| `front-checkout` | CardForm + MP SDK, PaymentResult | front-order + front-auth + front-shared (moka) |
| `front-merchant` | TransactionsList, RefundModal | front-auth + front-shared (moka) |

### Mock Policy

- Se área A depende de função/serviço da área B e B não está pronta:
  - Coder de A cria mock em `__mocks__/{dependencia}.ts`
  - Mock segue INTERFACE definida no spec.md de B (§3)
  - Mock é injetado via props/context (nunca import direto)
  - **Documenter lista explicitamente** no SUMMARY.md

### Escalation

| Situação | Ação |
|----------|------|
| Dúvida pequena (campo de API, endpoint) | Agent faz grep/glob no backend |
| Dúvida arquitetural média | Orquestrador usa modelo barato para scan |
| Informação CRUCIAL ausente | `NEEDS_HUMAN:{area}:{descrição}` → pausa |
| Conflito entre áreas | `NEEDS_HUMAN` consolidado → pergunta única |

### Regras

- **SDD**: Nenhum código sem spec aprovada
- **TDD**: Teste RED → GREEN → REFACTOR (Coder)
- **FIRST**: Testes Fast, Isolated, Repeatable, Self-validating, Timely
- **SOLID + Clean Architecture**: Verificado pelo Reviewer
- **E2E**: Apenas na Phase 5, quando todo código pronto
- **Feedback**: SUMMARY.md do Documenter alimenta Analyst do próximo ciclo

## Arquivos de Referência

| O que | Onde |
|-------|------|
| Agentes | `.opencode/agents/agent-{analyst|coder|reviewer|documenter}.md` |
| Templates | `.opencode/templates/spec-area.md`, `review-report.md`, `summary-docs.md` |
| Pipeline detalhado | `.opencode/pipeline/lifecycle.md` |
| Estado | `.opencode/STATE.md` |
| Projeto | `.specs/project/PROJECT.md`, `ROADMAP.md` |
| Backend (brownfield) | `.specs/codebase/{STACK,ARCHITECTURE,CONVENTIONS,STRUCTURE,TESTING,INTEGRATIONS,CONCERNS}.md` |
| Specs das áreas | `.specs/features/{area}/spec.md` |
| Docs de saída | `docs/frontend/{area}/{SUMMARY,REVIEW}.md` |

---

**Comandos:** `/start-frontend-cycle` para iniciar um ciclo. `/frontend-status` para ver estado atual.
