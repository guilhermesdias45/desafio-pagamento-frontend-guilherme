---
name: sdd-build
description: "SDD+TDD Build Pipeline: verifica spec aprovada em specs/, enforça ciclo TDD (RED→GREEN→REFACTOR), delega implementação ao /build e valida cobertura JaCoCo ≥ 90% ao final. Use no lugar de /build para qualquer feature deste projeto."
argument-hint: "<caminho da spec ou nome da feature> (ex: specs/user-service/register.md)"
---

# SDD+TDD Build Pipeline

Você está orquestrando o build com enforcement do fluxo SDD+TDD obrigatório deste projeto. Siga os passos em ordem.

## Input

$ARGUMENTS

## Step 0: Resolver SPEC_PATH e TASKS_DIR

### Step 0a: Resolver SPEC_PATH

Derive o caminho da spec a partir de `$ARGUMENTS`:
- Se o argumento já é um path `.md` → use diretamente como `SPEC_PATH`
- Se é um nome de feature → normalize para `specs/<modulo>/spec.md`
- Ex: "user-service/register" → `specs/user-service/register.md`

### Step 0b: Verificar que a spec existe e está completa

1. Verifique se `$SPEC_PATH` existe. Se não existir:
   - Informe: "Spec não encontrada em `$SPEC_PATH`. Crie a spec antes de implementar."
   - Pergunte via `AskUserQuestion`:
     - **"Criar spec agora com /spec"** — encerre: "Execute `/spec $ARGUMENTS` primeiro, depois volte com `/sdd-build`."
     - **"Continuar sem spec (não recomendado)"** — defina `SPEC_MISSING=true` e prossiga com aviso
   - Se `SPEC_MISSING=true`: registre o desvio para o relatório final e pule Step 0c.

2. Se existir, leia `$SPEC_PATH` e verifique a presença das 11 seções obrigatórias:
   - Seções presentes: `SPEC_COMPLETE=true`
   - Seções faltando: liste as ausentes, informe o usuário, pergunte se quer completar via `/spec` primeiro ou continuar assim mesmo.

### Step 0c: Apresentar spec para confirmação

Exiba um resumo da spec para o usuário:

```
## Spec: $SPEC_PATH

**Assinatura:** [seção 1 resumida]
**Casos extremos:** [N documentados]
**Erros documentados:** [N]
**Eventos Kafka:** [lista]
**Performance target:** [P50 / P99]
```

Use `AskUserQuestion`:
- **"Spec aprovada — iniciar TDD"** → prossiga para Step 0d
- **"Voltar e ajustar spec"** → encerre: "Edite a spec e re-execute `/sdd-build`."

### Step 0d: Resolver TASKS_DIR

Execute via PowerShell para obter o diretório de tasks da branch atual:

```powershell
$branch = git rev-parse --abbrev-ref HEAD 2>$null
if (-not $branch -or $branch -eq "HEAD") {
  $sha = git rev-parse --short HEAD 2>$null
  $branch = if ($sha) { "detached-$sha" } else { "" }
}
if (-not $branch) {
  "TASKS_DIR=tasks"
} else {
  $sanitized = $branch -replace '/', '-' -replace '[^A-Za-z0-9._-]', '-' -replace '^-+|-+$', ''
  if (-not $sanitized) { $sanitized = "unknown-branch" }
  "TASKS_DIR=tasks/$sanitized"
}
```

Armazene `TASKS_DIR`. Limpe com `Remove-Item -Recurse -Force "$TASKS_DIR" -ErrorAction SilentlyContinue`.

## Step 1: Preparar PRD a partir da spec

Gere um PRD em `$TASKS_DIR/prd-from-spec.md` sintetizando as seções da spec no formato esperado pelo `prd-task-planner`:

```markdown
# PRD: [Nome da Feature]

## Objetivo
[Seção 1 da spec — assinatura e propósito]

## Comportamento esperado
[Seção 4 — pós-condições de sucesso]

## Casos de erro
[Seção 5 — tabela de erros]

## Casos extremos
[Seção 7 — tabela CE-001, CE-002...]

## Restrições técnicas
- Stack: Java 21 + Spring Boot 3.x + PostgreSQL + Redis + Kafka
- DTOs como Records Java 21
- Resultados como sealed interfaces (Success/Failure)
- Virtual Threads para operações de I/O
- Cobertura mínima: 90% (JaCoCo)
- Nunca logar: [campos da seção 11]
- Performance: P50=[valor], P99=[valor]

## Efeitos colaterais esperados
[Seção 9 — Kafka, Redis, Email]

## Segurança
[Seção 11 completa]
```

## Step 2: Configurar TDD obrigatório

Antes de passar para o `/build`, injete nas tasks geradas a instrução TDD:

Adicione ao início do PRD em `$TASKS_DIR/prd-from-spec.md`:

```markdown
## TDD Mode: REQUIRED

Cada task de implementação DEVE seguir o ciclo RED → GREEN → REFACTOR:
1. RED: Escrever teste falhando baseado na spec (cada CE-* = 1 teste mínimo)
2. GREEN: Implementar o mínimo para o teste passar
3. REFACTOR: Melhorar sem quebrar testes

Nomes de testes seguem o padrão: `deve_<comportamento>_quando_<condição>()`
Jamais usar H2 — usar Testcontainers com PostgreSQL real.
```

## Step 3: Delegar ao /build

Invoque o skill `/build` passando o conteúdo de `$TASKS_DIR/prd-from-spec.md` como PRD.

Instrução ao `/build`:
- Usar `TASKS_DIR=$TASKS_DIR` (já resolvido)
- TDD Mode está ativado em todas as tasks
- Após implementação, NÃO encerrar — retornar controle para este skill

Aguarde o `/build` completar todos os seus passos (incluindo review).

## Step 4: Verificar cobertura JaCoCo

Após o `/build` completar:

1. Execute `mvn jacoco:report -pl services/<modulo> -q` ou equivalente para o módulo implementado.

2. Leia o relatório em `services/<modulo>/target/site/jacoco/index.html` ou parse o CSV em `services/<modulo>/target/site/jacoco/jacoco.csv`.

3. Calcule a cobertura por pacote/classe:
   - **≥ 90%** → `COVERAGE_OK=true`
   - **< 90%** → `COVERAGE_OK=false`, liste os pacotes/classes abaixo do threshold

4. Se `COVERAGE_OK=false`, use `AskUserQuestion`:
   - **"Escrever testes faltantes agora"** → lance o agente `test-writer` com: "Escreva testes para os métodos sem cobertura abaixo de 90% em `services/<modulo>`. Baseie-se nos casos extremos de `$SPEC_PATH`."
   - **"Aceitar cobertura atual e prosseguir"** → registre o desvio no relatório final

## Step 5: Executar /pci-check

Execute o skill `/pci-check` apontando para os arquivos modificados nesta branch.

Aguarde o relatório. Se houver findings críticos (dados sensíveis em logs, tokens expostos), apresente-os ao usuário antes de prosseguir.

## Step 6: Relatório final SDD

```
## SDD Build Completo

### Spec
- Path: $SPEC_PATH
- Status: [aprovada / faltou seções: lista]
- Casos extremos: [N]

### TDD
- Tasks com TDD: [N/M]
- Testes RED escritos antes do código: [sim/não por task]

### Cobertura JaCoCo
- Status: [≥ 90% ✓ / abaixo: lista de pacotes]
- Relatório: services/<modulo>/target/site/jacoco/

### PCI Check
- Findings: [N críticos, N avisos / nenhum]
- Ver: $TASKS_DIR/pci-report.md

### Review
- [resultado do code-reviewer do /build]

### Próximo passo
- [ ] PR via `/craft-pr`
- [ ] Resolver findings PCI (se houver)
- [ ] Merge após aprovação do time
```

## Regras

- Nunca pular a verificação da spec — é o contrato deste projeto
- Nunca aceitar cobertura < 90% silenciosamente — sempre registrar no relatório
- O PRD gerado em Step 1 é efêmero — não substitui a spec em `specs/`
- Sempre executar `/pci-check` — segurança não é opcional em contexto financeiro
- Se o `/build` falhar, ainda executar Step 4 e Step 5 com o código parcial
