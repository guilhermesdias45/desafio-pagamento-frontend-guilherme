# Local Dev Setup — Acabou o Mony

Guia para subir o ambiente local completo. Cobre infraestrutura (Postgres, Redis, Kafka) e os serviços de aplicação.

---

## Pré-requisitos

| Ferramenta | Versão mínima | Verificar |
|---|---|---|
| Docker Desktop | 4.x | `docker --version` |
| Docker Compose | v2 (plugin) | `docker compose version` |
| Java | 21 | `java -version` |
| Maven | 3.9+ | `mvn -version` |

> **Windows**: use PowerShell 5.1+ para os comandos de geração de chaves. Não é necessário WSL.

---

## Serviços de infraestrutura

Estas imagens são fixas — não dependem do código do projeto.

| Container | Imagem | Porta host | Notas |
|---|---|---|---|
| `aom-postgres` | `postgres:16-alpine` | 5432 | volume persistente `postgres_data` |
| `aom-redis` | `redis:7-alpine` | 6379 | maxmemory 256 MB, allkeys-lru, persistência a cada 60s |
| `aom-kafka` | `confluentinc/cp-kafka:7.5.0` | 9094 | KRaft mode (sem Zookeeper), CLUSTER_ID fixo |
| `aom-kafka-init` | `confluentinc/cp-kafka:7.5.0` | — | job one-shot: cria os tópicos e sai |

### Por que `confluentinc/cp-kafka:7.5.0` e não `bitnami/kafka`?

- A imagem Confluent é usada pelo Testcontainers nos testes de integração (`@KafkaContainer`)
- Usar a mesma imagem localmente elimina divergências de comportamento entre dev e CI
- KRaft mode (sem Zookeeper) reduz a pilha local — um container a menos

---

## Tópicos Kafka criados automaticamente

| Tópico | Partições | Replication | Dono (produtor) |
|---|---|---|---|
| `user-events` | 3 | 1 | `user-service` |
| `transaction.completed` | 6 | 1 | `payment-service` |
| `transaction.failed` | 3 | 1 | `payment-service` |
| `fraud.detected` | 3 | 1 | `fraud-service` |
| `order.created` | 3 | 1 | `order-service` |
| `order.paid` | 3 | 1 | `order-service` |
| `order.cancelled` | 3 | 1 | `order-service` |

`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"` — nenhum tópico extra é criado em runtime.

---

## Serviços de aplicação (profile `app`)

Os serviços abaixo só sobem com `--profile app`. Durante o desenvolvimento individual, sobe apenas a infra.

| Container | Imagem | Porta host | Banco |
|---|---|---|---|
| `aom-api-gateway` | `aom/api-gateway:latest` | 8080 | — |
| `aom-user-service` | `aom/user-service:latest` | 8081 | `user_db` |
| `aom-payment-service` | `aom/payment-service:latest` | 8082 | `payment_db` |
| `aom-order-service` | `aom/order-service:latest` | 8083 | `order_db` |
| `aom-notification-service` | `aom/notification-service:latest` | 8084 | `notification_db` |
| `aom-fraud-service` | `aom/fraud-service:latest` | — (interno) | `fraud_db` |

`fraud-service` não expõe porta no host — apenas `payment-service` o acessa via rede Docker interna.

---

## Configuração do `.env`

### 1. Copie o arquivo de exemplo

```bash
cp .env.example .env
```

### 2. Gere as chaves criptográficas (Windows — PowerShell)

```powershell
.\gen-keys.ps1
```

Cole a saída no `.env`. O script gera:
- `JWT_PRIVATE_KEY` — chave RSA 2048-bit (PKCS#1, formato PEM inline com `\n`)
- `JWT_PUBLIC_KEY` — chave pública correspondente
- `TOTP_AES_KEY` — 32 bytes hex (AES-256 para TOTP em repouso)
- `INTERNAL_SECRET` — token aleatório base64 para comunicação interna `user-service ↔ api-gateway`

### 2. Gere as chaves criptográficas (Linux/macOS — OpenSSL)

```bash
# Chave privada RSA 2048
openssl genrsa -out private.pem 2048

# Chave pública
openssl rsa -in private.pem -pubout -out public.pem

# Converter para linha única (formato .env)
JWT_PRIVATE_KEY=$(awk 'NF {sub(/\r/, ""); printf "%s\\n",$0;}' private.pem)
JWT_PUBLIC_KEY=$(awk 'NF {sub(/\r/, ""); printf "%s\\n",$0;}' public.pem)

# AES-256 key
TOTP_AES_KEY=$(openssl rand -hex 32)

# Internal secret
INTERNAL_SECRET=$(openssl rand -base64 32 | tr -d '+/=')
```

### 3. Preencha as variáveis restantes

| Variável | Onde obter |
|---|---|
| `POSTGRES_PASSWORD` | invente uma senha forte (dev local) |
| `REDIS_PASSWORD` | invente uma senha forte (dev local) |
| `MERCADOPAGO_ACCESS_TOKEN` | [MP Developers](https://www.mercadopago.com.br/developers) → credenciais de teste → `TEST-...` |
| `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD` | [Mailtrap](https://mailtrap.io) (free) ou Gmail App Password |
| `NEW_RELIC_LICENSE_KEY` | New Relic → Account Settings → API Keys (opcional em dev) |
| `ANTHROPIC_API_KEY` | [console.anthropic.com](https://console.anthropic.com/settings/keys) (necessário só para `fraud-service`) |

---

## Subindo o ambiente

### Só infraestrutura (o mais comum durante desenvolvimento)

```bash
docker compose up -d
```

Sobe: `postgres`, `redis`, `kafka`, `kafka-init`.

### Infraestrutura + todos os serviços

```bash
docker compose --profile app up --build -d
```

### Verificar saúde dos containers

```bash
docker compose ps
```

Todos devem aparecer como `healthy` antes de usar.

### Ver logs

```bash
# Todos os containers
docker compose logs -f

# Container específico
docker compose logs -f kafka
docker compose logs -f aom-postgres
```

### Parar tudo

```bash
docker compose down
```

### Parar e remover volumes (reset completo do banco)

```bash
docker compose down -v
```

---

## Rede Docker interna

Todos os containers estão na rede bridge `aom-network`. Use os nomes dos containers como hostnames:

| De → Para | Endereço |
|---|---|
| qualquer serviço → Postgres | `postgres:5432` |
| qualquer serviço → Redis | `redis:6379` |
| qualquer serviço → Kafka (interno) | `kafka:9092` |
| host → Kafka | `localhost:9094` |
| `payment-service` → `fraud-service` | `fraud-service:8085` |

---

## Inicialização do banco de dados

O script `scripts/init-databases.sql` é executado automaticamente pelo Postgres no primeiro boot (`docker-entrypoint-initdb.d`). Ele cria os schemas/databases separados para cada serviço.

Cada serviço gerencia suas próprias migrations via **Flyway** — nenhum serviço acessa o banco de outro.

---

## Healthchecks

| Serviço | Check | Interval | Retries |
|---|---|---|---|
| Postgres | `pg_isready` | 10s | 5 |
| Redis | `redis-cli ping` | 10s | 5 |
| Kafka | `kafka-topics --list` | 15s | 10 (start_period 30s) |
| Serviços Spring | `GET /actuator/health` | 30s | 5 (start_period 60s) |

Os serviços de aplicação (`depends_on: condition: service_healthy`) só iniciam após a infra estar saudável.

---

## Variáveis de ambiente — referência rápida

```
# PostgreSQL
POSTGRES_USER=aom
POSTGRES_PASSWORD=<senha forte>
POSTGRES_PORT=5432

# Redis
REDIS_PASSWORD=<senha forte>
REDIS_PORT=6379

# Kafka (porta do host — containers usam kafka:9092)
KAFKA_PORT=9094

# JWT RS256
JWT_PRIVATE_KEY=-----BEGIN RSA PRIVATE KEY-----\n...\n-----END RSA PRIVATE KEY-----
JWT_PUBLIC_KEY=-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----

# Cripto AES-256 para TOTP
TOTP_AES_KEY=<64 chars hex>

# Auth interna user-service ↔ api-gateway
INTERNAL_SECRET=<random string>

# 2FA
TOTP_ISSUER=AcabouoMony

# Mercado Pago
MERCADOPAGO_ACCESS_TOKEN=TEST-<token>

# Email
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=<email>
MAIL_PASSWORD=<app-password>

# New Relic (opcional em dev)
NEW_RELIC_LICENSE_KEY=<key>

# Anthropic (fraud-service)
ANTHROPIC_API_KEY=<key>
```

---

## Problemas comuns

**Kafka não sobe (`start_period` expira)**
- O Kafka KRaft pode demorar 30–60s no primeiro boot. Aguarde e verifique com `docker compose logs kafka`.

**Postgres: `password authentication failed`**
- O volume foi criado com uma senha diferente. Rode `docker compose down -v` para reiniciar com senha limpa.

**Redis: `WRONGPASS`**
- Mesma causa — volume antigo com senha diferente. `docker compose down -v`.

**`kafka-init` saiu com erro**
- Geralmente o Kafka ainda não estava pronto. `docker compose restart kafka-init` ou `docker compose up kafka-init`.

**JWT_PRIVATE_KEY inválido**
- Verifique se a chave está em uma única linha com `\n` literal entre as quebras de linha — não quebras reais. O `gen-keys.ps1` faz isso automaticamente.
