# Task 01: docker-compose + kafka-init + .env.example

## Objective
Atualizar `docker-compose.yml` e `.env.example` para usar tópico Kafka único `user-events`, renomear `AES_SECRET_KEY` → `TOTP_AES_KEY`, e adicionar `INTERNAL_SECRET` e `TOTP_ISSUER`.

## Context
**Quick Context:**
- `docker-compose.yml` atualmente cria 4 tópicos separados em `kafka-init` (`user.registered`, `user.login.success`, `user.login.blocked`, `user.2fa.enabled`) — `plan.md` linha 53 diz que deve ser tópico único `user-events` com tipo no payload.
- `user-service` em `docker-compose.yml` recebe `AES_SECRET_KEY` (linha 179); precisa virar `TOTP_AES_KEY`.
- `.env.example` não tem `INTERNAL_SECRET` nem `TOTP_ISSUER`.
- **NÃO é task funcional** — sem TDD formal. Acceptance Criteria são verificações manuais + verificação que o app sobe.

Ler antes:
- `specs/user-service/plan.md` §"Dependências"→"Tópicos Kafka produzidos" (linhas 52-58)
- `specs/user-service/plan.md` §"Variáveis de Ambiente" (linhas 225-233)
- `docker-compose.yml` completo
- `.env.example` completo

## Target Files
**Modify:**
- `docker-compose.yml` (raiz do repo)
- `.env.example` (raiz do repo)

## Dependencies
- Depends on: None
- Blocks: task-02, task-12 (UserEventProducer precisa do tópico criado)

## Requirements

### `docker-compose.yml` — bloco `kafka-init`
Substituir as 4 linhas que criam tópicos user.* por uma única linha que cria `user-events`:

```bash
kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic user-events --partitions 3 --replication-factor 1
```

Manter os tópicos `transaction.*`, `fraud.detected`, `order.*` intactos (outros serviços).

### `docker-compose.yml` — bloco `user-service.environment`
- Remover: `AES_SECRET_KEY: ${AES_SECRET_KEY}`
- Adicionar:
  - `TOTP_AES_KEY: ${TOTP_AES_KEY}`
  - `INTERNAL_SECRET: ${INTERNAL_SECRET}`
  - `TOTP_ISSUER: ${TOTP_ISSUER:-AcabouoMony}`

### `.env.example`
- Renomear seção `AES_SECRET_KEY=...` para `TOTP_AES_KEY=CHANGE_ME_64_CHAR_HEX_STRING` (manter o comentário sobre `openssl rand -hex 32`).
- Adicionar nova seção:
  ```
  # ─── Endpoint interno (user-service ↔ api-gateway) ──────────────────────────
  INTERNAL_SECRET=CHANGE_ME_STRONG_RANDOM_STRING

  # ─── TOTP / 2FA ─────────────────────────────────────────────────────────────
  TOTP_ISSUER=AcabouoMony
  ```

## Acceptance Criteria
- [ ] `docker-compose.yml` cria exatamente UM tópico `user-events` no `kafka-init` (verificar via `grep -c "topic user" docker-compose.yml` retorna 1)
- [ ] `docker-compose.yml` bloco `user-service.environment` contém `TOTP_AES_KEY`, `INTERNAL_SECRET`, `TOTP_ISSUER` e NÃO contém `AES_SECRET_KEY`
- [ ] `.env.example` contém `TOTP_AES_KEY=` e NÃO contém `AES_SECRET_KEY=`
- [ ] `.env.example` contém `INTERNAL_SECRET=` e `TOTP_ISSUER=AcabouoMony`
- [ ] Tópicos não-user (`transaction.completed`, `transaction.failed`, `fraud.detected`, `order.created`, `order.paid`, `order.cancelled`) permanecem intactos
- [ ] `docker compose config` (sem `--profile app`) valida sem erros
