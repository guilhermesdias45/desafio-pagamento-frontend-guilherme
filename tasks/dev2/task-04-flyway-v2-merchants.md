# Task 04: Flyway V2 — merchants table + ALTER users ADD FK

## Objective
Criar migration V2: enum `merchant_status`, tabela `merchants` com FK `owner_id → users(id)`, índice em `cnpj`. No **fim** da mesma migration, executar `ALTER TABLE users ADD CONSTRAINT fk_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id)` para resolver a dependência circular.

## Context
**Quick Context:**
- V1 criou `users` com `merchant_id UUID NULLABLE` **sem FK**. V2 cria `merchants` e adiciona a FK retroativamente — tudo na mesma migration atômica.
- A migration falha se V1 não estiver aplicada (FK `owner_id → users` precisa de `users` existir).
- Após V2 aplicada, tasks V3 e V4 (task-05, task-06) podem rodar em paralelo.
- **Não é task funcional** — SQL migration. Sem TDD formal.

Ler antes:
- `specs/user-service/plan.md` §"Tabelas do banco" → tabela `merchants` (linhas 76-85)
- `specs/user-service/plan.md` nota sobre dependência circular (linhas 196-199)
- `tasks/dev2/task-03-flyway-v1-users.md` (entender estado deixado por V1)

## Target Files
**Create:**
- `services/user-service/src/main/resources/db/migration/V2__create_merchants.sql`

## Dependencies
- Depends on: task-03
- Blocks: task-05 (V3 — paralelo com V4), task-06 (V4 — paralelo com V3), task-08

## Requirements

### Enum
```sql
CREATE TYPE merchant_status AS ENUM ('ACTIVE', 'SUSPENDED', 'INACTIVE');
```

### Tabela `merchants`
| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | UUID | PK |
| company_name | VARCHAR(100) | NOT NULL |
| cnpj | VARCHAR(14) | UNIQUE NOT NULL |
| owner_id | UUID | NOT NULL, FK → users(id) |
| status | merchant_status | NOT NULL DEFAULT 'ACTIVE' |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

### Índice
- `CREATE INDEX idx_merchants_cnpj ON merchants(cnpj);`

### ALTER TABLE no fim
```sql
ALTER TABLE users
    ADD CONSTRAINT fk_users_merchant
    FOREIGN KEY (merchant_id) REFERENCES merchants(id);
```

### Trigger updated_at (opcional, mesmo padrão de V1)

## Acceptance Criteria
- [ ] Arquivo `V2__create_merchants.sql` existe em `services/user-service/src/main/resources/db/migration/`
- [ ] Aplicar V1 + V2 contra PostgreSQL 16 limpa cria o enum, a tabela `merchants` e adiciona a FK em `users.merchant_id` sem erros
- [ ] Após V2, `SELECT conname FROM pg_constraint WHERE conrelid = 'users'::regclass AND contype = 'f'` retorna `fk_users_merchant`
- [ ] `SELECT conname FROM pg_constraint WHERE conrelid = 'merchants'::regclass AND contype = 'f'` retorna a FK `owner_id → users`
- [ ] Tentar `INSERT INTO users(merchant_id) VALUES ('uuid-inexistente')` falha por FK violation (validação que a FK foi efetivada)
- [ ] Migration roda em < 500ms em ambiente clean
