# Review Report — [Area Name]

**Reviewer:** agent-reviewer
**Date:** [date]
**Spec:** `.specs/features/[area]/spec.md`
**Code:** `frontend/src/[area-path]/`

---

## SOLID Check

| Princípio | Status | Observação |
|-----------|--------|------------|
| S — Single Responsibility | ✅/❌ | ... |
| O — Open/Closed | ✅/❌ | ... |
| L — Liskov Substitution | ✅/❌ | ... |
| I — Interface Segregation | ✅/❌ | ... |
| D — Dependency Inversion | ✅/❌ | ... |

## FIRST Test Check

| Princípio | Status | Observação |
|-----------|--------|------------|
| F — Fast | ✅/❌ | ... |
| I — Isolated | ✅/❌ | ... |
| R — Repeatable | ✅/❌ | ... |
| S — Self-validating | ✅/❌ | ... |
| T — Timely | ✅/❌ | ... |

## Mock Isolation Check

| Mock | Localização | Bem isolado? | Observação |
|------|------------|-------------|------------|
| ... | `__mocks__/...` | ✅/❌ | ... |

## Clean Architecture Check

- [ ] Camada de UI separada da lógica de negócio
- [ ] API client abstraído (não chamado diretamente dos componentes)
- [ ] Tipos importados de `types/` (não duplicados)
- [ ] Efeitos colaterais em hooks, não em componentes

## Bloqueios (NEEDS_HUMAN)

| ID | Descrição | Justificativa |
|----|-----------|---------------|
| ... | ... | ... |

## Verdict

**PASS** | **CONDITIONAL_PASS** (ver observações) | **BLOCKER** (ver NEEDS_HUMAN)
