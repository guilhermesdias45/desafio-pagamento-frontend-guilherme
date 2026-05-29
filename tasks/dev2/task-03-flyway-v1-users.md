# Task 03: Flyway V1 — users table (sem FK para merchants)

## Objective
Criar a primeira migration Flyway: enums `user_role` e `user_status`, tabela `users` com `merchant_id` como UUID NULLABLE **sem FK constraint** (a FK é adicionada em task-04 via ALTER TABLE para resolver dependência circular). Adicionar índices em `email` e `merchant_id`.

## Context
**Quick Context:**
- `application.yml` já tem `spring.flyway.locations: classpath:db/migration` mas a pasta não existe — precisa ser criada.
- `ddl-auto: validate` significa que **qualquer entidade JPA criada em task-08 quebra o boot se a migration correspondente não existir**.
- A dependência circular é resolvida na ordem: V1 cria `users` (merchant_id sem FK) → V2 cria `merchants` (com FK owner_id → users) → V2 termina com `ALTER TABLE users ADD CONSTRAINT fk_merchant`.
- **Não é task funcional** — SQL migration. Sem TDD formal. Acceptance criteria: migration aplica contra Testcontainers PostgreSQL 16 sem erros.

Ler antes:
- `specs/user-service/plan.md` §"Tabelas do banco" → tabela `users` (linhas 61-74)
- `specs/user-service/plan.md` §"Flyway Migrations" + nota sobre dependência circular (linhas 187-199)
- `services/user-service/src/main/resources/application.yml` (configuração Flyway)

## Target Files
**Create:**
- `services/user-service/src/main/resources/db/migration/V1__create_users.sql`

## Dependencies
- Depends on: None (paralelo a task-01, task-02 conceitualmente, mas pelo design wave estamos colocando após task-01/02 para serializar a fundação)
- Blocks: task-04, task-08

## Requirements

### Enums PostgreSQL
```sql
CREATE TYPE user_role AS ENUM ('CUSTOMER', 'MERCHANT_OWNER', 'STAFF');
CREATE TYPE user_status AS ENUM ('PENDING_EMAIL_CONFIRMATION', 'ACTIVE', 'LOCKED', 'DISABLED');
```

### Tabela `users`
| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | UUID | PK |
| email | VARCHAR(255) | UNIQUE NOT NULL |
| password_hash | VARCHAR(60) | NOT NULL — BCrypt fixo em 60 chars |
| full_name | VARCHAR(100) | NOT NULL |
| role | user_role | NOT NULL |
| merchant_id | UUID | **NULLABLE, SEM FK ainda** (FK adicionada no V2) |
| status | user_status | NOT NULL DEFAULT 'PENDING_EMAIL_CONFIRMATION' |
| totp_enabled | BOOLEAN | NOT NULL DEFAULT false |
| totp_secret_encrypted | TEXT | NULLABLE |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

### Índices
- `CREATE INDEX idx_users_email ON users(email);` (já é UNIQUE mas índice explícito ajuda EXPLAIN)
- `CREATE INDEX idx_users_merchant_id ON users(merchant_id) WHERE merchant_id IS NOT NULL;`

### Trigger updated_at (opcional mas recomendado)
Função plpgsql que atualiza `updated_at = NOW()` em UPDATE; trigger BEFORE UPDATE em `users`.

## Acceptance Criteria
- [ ] Arquivo `V1__create_users.sql` existe em `services/user-service/src/main/resources/db/migration/`
- [ ] Aplicar a migration contra PostgreSQL 16 (via `psql` ou Testcontainers) cria os 2 enums e a tabela `users` sem erros
- [ ] Tabela `users` tem **11 colunas** com os tipos exatos da spec
- [ ] `merchant_id` é nullable e NÃO tem FK constraint (`SELECT conname FROM pg_constraint WHERE conrelid = 'users'::regclass AND contype = 'f'` retorna 0 rows)
- [ ] Índices `idx_users_email` e `idx_users_merchant_id` existem (`\di` no psql)
- [ ] Migration roda em < 500ms em ambiente clean
