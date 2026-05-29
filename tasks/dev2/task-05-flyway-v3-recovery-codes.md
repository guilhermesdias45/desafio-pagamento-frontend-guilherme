# Task 05: Flyway V3 — recovery_codes table

## Objective
Criar migration V3: tabela `recovery_codes` com FK para `users(id)`. Cada usuário com 2FA ativo terá até 8 recovery codes (BCrypt hash de cada).

## Context
**Quick Context:**
- Pode rodar em paralelo com task-06 (V4) — Flyway aplica em ordem (V3 depois V4 não tem dependência semântica entre si após V2).
- **Não é task funcional** — SQL migration. Sem TDD formal.

Ler antes:
- `specs/user-service/plan.md` §"Tabelas do banco" → tabela `recovery_codes` (linhas 87-95)

## Target Files
**Create:**
- `services/user-service/src/main/resources/db/migration/V3__create_recovery_codes.sql`

## Dependencies
- Depends on: task-04
- Blocks: task-08

## Requirements

### Tabela `recovery_codes`
| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | UUID | PK |
| user_id | UUID | NOT NULL, FK → users(id) ON DELETE CASCADE |
| code_hash | VARCHAR(60) | NOT NULL — BCrypt hash |
| used | BOOLEAN | NOT NULL DEFAULT false |
| used_at | TIMESTAMPTZ | NULLABLE |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

### Índice
- `CREATE INDEX idx_recovery_codes_user_id ON recovery_codes(user_id);`

## Acceptance Criteria
- [ ] Arquivo `V3__create_recovery_codes.sql` existe em `services/user-service/src/main/resources/db/migration/`
- [ ] Aplicar V1→V3 contra PostgreSQL 16 cria a tabela `recovery_codes` com FK para `users`
- [ ] FK tem `ON DELETE CASCADE` (deletar usuário deleta seus recovery codes)
- [ ] Índice `idx_recovery_codes_user_id` existe
- [ ] Migration roda em < 200ms
