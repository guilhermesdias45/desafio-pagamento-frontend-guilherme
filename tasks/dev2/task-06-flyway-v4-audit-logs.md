# Task 06: Flyway V4 — user_audit_logs table

## Objective
Criar migration V4: tabela `user_audit_logs` para registrar eventos de segurança (LOGIN_SUCCESS, LOGIN_FAILED, ACCOUNT_LOCKED, 2FA_ENABLED). FK para `users(id)` nullable (tentativas de login com email inexistente podem precisar de audit sem user_id).

## Context
**Quick Context:**
- Pode rodar em paralelo com task-05 (V3) — Flyway aplica em ordem mas as duas migrations criam tabelas independentes.
- **Não é task funcional** — SQL migration. Sem TDD formal.
- Tabela populada nas tasks 15 (login attempts) e 16 (2FA events). Veja `updated-prd.md` §11 Open Questions sobre escopo do `UserAuditLogger`.

Ler antes:
- `specs/user-service/plan.md` §"Tabelas do banco" → tabela `user_audit_logs` (linhas 97-105)

## Target Files
**Create:**
- `services/user-service/src/main/resources/db/migration/V4__create_user_audit_logs.sql`

## Dependencies
- Depends on: task-04
- Blocks: task-08

## Requirements

### Tabela `user_audit_logs`
| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | UUID | PK |
| user_id | UUID | NULLABLE, FK → users(id) ON DELETE SET NULL |
| event_type | VARCHAR(50) | NOT NULL |
| ip_address | VARCHAR(45) | NULLABLE — suporta IPv6 |
| device_fingerprint | VARCHAR(255) | NULLABLE |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

### Índices
- `CREATE INDEX idx_user_audit_logs_user_id ON user_audit_logs(user_id);`
- `CREATE INDEX idx_user_audit_logs_created_at ON user_audit_logs(created_at);`

### CHECK constraint para event_type
```sql
ALTER TABLE user_audit_logs ADD CONSTRAINT chk_event_type
    CHECK (event_type IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILED', 'ACCOUNT_LOCKED', 'ACCOUNT_UNLOCKED',
        'REGISTER_SUCCESS', '2FA_ENABLED', '2FA_DISABLED', '2FA_RECOVERY_USED',
        'REFRESH_SUCCESS', 'REFRESH_FAILED', 'LOGOUT'
    ));
```

## Acceptance Criteria
- [ ] Arquivo `V4__create_user_audit_logs.sql` existe em `services/user-service/src/main/resources/db/migration/`
- [ ] Aplicar V1→V4 contra PostgreSQL 16 cria a tabela `user_audit_logs` com FK nullable para `users`
- [ ] FK tem `ON DELETE SET NULL`
- [ ] Os 2 índices existem
- [ ] CHECK constraint rejeita event_type não previsto (testar com INSERT manual)
- [ ] Migration roda em < 200ms
