# Pipeline Lifecycle

**Orquestrador:** AGENTS.md
**Modelo:** opencode/big-pickle

---

## 1. Iniciar Ciclo

Disparado por: `/start-frontend-cycle` (ou similar)

### Pré-condições
- [ ] `.opencode/STATE.md` existe e está atualizado
- [ ] `.specs/codebase/*.md` existem (brownfield mapeado)
- [ ] AGENTS.md existe

### Ação
O orquestrador lê:
1. AGENTS.md — pipeline definition
2. `.opencode/STATE.md` — estado atual
3. `.specs/project/ROADMAP.md` — próximo milestone
4. `.specs/codebase/*.md` — contexto do backend

Decide quais áreas executar neste ciclo baseado no ROADMAP.md.

---

## 2. Phase 1 — Analyse

### Paralelismo
TODAS as áreas do ciclo são lançadas em paralelo via task tool:

```typescript
// Pseudo-código da orquestração
const areas = ['front-shared', 'front-auth', 'front-order', 'front-checkout', 'front-merchant'];
const analysts = areas.map(area => launchAgent('agent-analyst', { area }));
await Promise.all(analysts);
```

### Cada Analyst recebe:
- `.specs/project/PROJECT.md`
- `.specs/project/ROADMAP.md`
- `.specs/codebase/*.md`
- `.opencode/STATE.md`
- `docs/frontend/{area}/SUMMARY.md` (do ciclo anterior, se existir)
- Backend specs relevantes (`specs/user-service/spec.md`, etc.)

### Cada Analyst retorna:
- Status: Complete | Blocked | Partial
- `.specs/features/{area}/spec.md` criado
- NEEDS_HUMAN flags (se houver)

### Validação
Orquestrador verifica:
- Todas as specs foram criadas?
- Alguma NEEDS_HUMAN?
- Se sim → pausa e pergunta ao usuário (pergunta consolidada única)

---

## 3. Phase 2 — Code (TDD)

### Paralelismo
TODOS os Coders são lançados em paralelo.

Cada Coder recebe a spec da sua área e as interfaces definidas nas specs das outras áreas.

**Mock Policy:**
- Se a área A depende da função X da área B, e B ainda não foi implementada:
  - Coder de A cria `__mocks__/B-service.ts` seguindo a interface definida em `spec.md §3` de B
  - Mock usa valores previsíveis (ex: token fixo, resposta sempre success)
  - Mock é injetado via props/context (nunca import direto)
  - Coder documenta o mock no output

### Cada Coder retorna:
- Status: Complete | Blocked | Partial
- Files changed: [lista]
- Gate check result: [pass/fail + test count]
- SPEC_DEVIATION markers
- Funções mockadas: [lista com localização e motivo]

### Validação
Orquestrador verifica:
- Todos os Coders completaram?
- Gate checks passaram?
- Mocks foram documentados?
- Algum SPEC_DEVIATION?
- Algum BLOCKER?

---

## 4. Phase 3 — Review

### Paralelismo
TODOS os Reviewers são lançados em paralelo.

Cada Reviewer recebe o código da sua área + spec + output do Coder.

### Cada Reviewer retorna:
- Status: PASS | CONDITIONAL_PASS | BLOCKER
- `docs/frontend/{area}/REVIEW.md` criado
- NEEDS_HUMAN flags

### Validação
Orquestrador verifica:
- Algum BLOCKER? → pausa
- Algum CONDITIONAL_PASS? → anota observações
- Todos PASS? → avança

---

## 5. Phase 4 — Document

### Paralelismo
TODOS os Documenters são lançados em paralelo.

Cada Documenter recebe spec + código + review report.

### Cada Documenter retorna:
- Status: Complete | Partial
- `docs/frontend/{area}/SUMMARY.md` criado
- Mocks documentados (tabela)
- NEEDS_HUMAN flags consolidadas

---

## 6. Phase 5 — Integrate

### Ações sequenciais:

1. **Resolver NEEDS_HUMAN**
   - Consolidar todas as flags de todas as áreas
   - Se houver → perguntar ao usuário (UMA pergunta, não várias)
   - Aguardar resposta → aplicar decisão

2. **Resolver mocks**
   - Para cada mock documentado, verificar se a área dependida já completou
   - Se sim → substituir mock por implementação real
   - Se não → manter mock para próximo ciclo

3. **Build check**
   ```bash
   cd frontend
   npx tsc --noEmit
   npm run build
   ```

4. **E2E tests** (apenas quando TODAS as áreas estiverem completas)
   - Executar Playwright
   - Reportar resultados

5. **Atualizar STATE.md**
   - Avançar ciclo
   - Atualizar status das áreas
   - Registrar decisões

---

## 7. Entre Ciclos

O output do Documenter (`docs/frontend/{area}/SUMMARY.md`) é o input do Analyst do próximo ciclo.

### Fluxo de realimentação:
```
Cycle N
  → Documenter produz SUMMARY.md com mocks listados
  ↓
Cycle N+1
  → Analyst lê SUMMARY.md do ciclo anterior
  → Sabe exatamente quais mocks precisam ser resolvidos
  → Sabe quais decisões foram tomadas
  → Foca apenas no que mudou
```

---

## 8. Escalation Rules

| Situação | Ação |
|----------|------|
| Dúvida pequena (ex: "qual campo na resposta JSON?") | Analyst faz grep no backend |
| Dúvida média (ex: "como esse endpoint lida com paginação?") | Analyst lê spec do backend |
| Dúvida arquitetural (ex: "devo usar context ou props?") | Analyst pergunta ao orquestrador → orquestrador usa modelo barato para scan |
| Informação CRUCIAL ausente (ex: "fluxo de 2FA não documentado") | NEEDS_HUMAN → pausa o ciclo |
| Conflito entre áreas (ex: "duas áreas definiram a mesma interface diferente") | NEEDS_HUMAN → consolida e pergunta |

---

## 9. Comandos do Pipeline

| Comando | Ação |
|---------|------|
| `/start-frontend-cycle` | Inicia um ciclo completo do pipeline |
| `/frontend-status` | Mostra STATE.md resumido |
| `/frontend-resolve-blocker {id}` | Marca blocker como resolvido |
| `/frontend-next` | Calcula qual o próximo ciclo com base no ROADMAP |
