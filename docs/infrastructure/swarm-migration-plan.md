# Plano de Migração — Docker Swarm + Traefik

**Data:** 2026-06-09
**Objetivo:** Migrar de Docker Compose para Docker Swarm com Traefik como load balancer na borda, permitindo escalar horizontalmente os microserviços sem alterar o código Spring existente.

---

## Arquitetura Alvo

```
                      ┌──────────────┐
                      │   Traefik    │  :80
                      │  (1 réplica) │
                      │ Swarm mode   │
                      └──────┬───────┘
                             │ Docker Swarm Routing Mesh
                             │
                      ┌──────▼───────┐
                      │  api-gateway  │  3-5 réplicas
                      │  (escalável)  │
                      └──────┬───────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
  ┌─────▼──────┐      ┌─────▼──────┐      ┌──────▼─────┐
  │user-service│      │payment-svc │      │order-svc  │
  │ 2-3 répl.  │      │ 2-3 répl.  │      │ 2 répl.   │
  └────────────┘      └─────┬──────┘      └────────────┘
                            │
                     ┌──────▼──────┐
                     │fraud-service│
                     │ 2 répl.     │
                     └─────────────┘

  ┌──────────────────────────────────────────────────────┐
  │               notification-service                   │
  │               2 réplicas (consumer Kafka)             │
  └──────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────┐
  │  postgres:5432 (1)  │  redis:6379 (1)  │  kafka:9092│
  │       stateful — não escalam                        │
  └──────────────────────────────────────────────────────┘
```

### Princípios

- **DNS do Swarm** resolve nomes entre serviços (`http://user-service:8081`) — exatamente como no Compose
- **Traefik** só na borda, balanceando entre réplicas do `api-gateway`
- **Zero alterações** nas URLs de serviço no código Spring
- **docker-compose.yml** continua existindo para dev local (não é alterado)

---

## O que NÃO muda

| Item | Motivo |
|---|---|
| Código Java | Chamadas continuam via DNS (`http://user-service:8081`) |
| `application.yml` de cada serviço | Nenhuma rota ou URL precisa ser alterada |
| `Dockerfile` de cada serviço | Mesma build, mesma imagem |
| `docker-compose.yml` | Continua funcionando para dev sem Swarm |
| `.env` | Swarm carrega o mesmo arquivo |
| Tópicos Kafka | Já têm partições suficientes para consumer groups |

---

## Passo 1 — Ajustar Pool de Conexões do PostgreSQL

Com múltiplas réplicas, cada uma abre seu próprio pool Hikari. É preciso reduzir o tamanho do pool por réplica e aumentar o `max_connections` do Postgres.

### Conta de conexões

| Serviço | Réplicas | Pool (novo) | Conexões totais |
|---|---|---|---|
| user-service | 3 | 10 | 30 |
| payment-service | 3 | 10 | 30 |
| order-service | 2 | 10 | 20 |
| notification-service | 2 | 10 | 20 |
| fraud-service | 2 | 10 (default) | 20 |
| **Total** | | | **120** |

### Arquivos para alterar

#### 1. `services/user-service/src/main/resources/application-docker.yml`

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10     # era 20
      minimum-idle: 3
```

#### 2. `services/payment-service/src/main/resources/application-docker.yml`

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10     # era 20
      minimum-idle: 3
```

#### 3. `services/order-service/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10     # era 20
      minimum-idle: 3
```

> Demais serviços mantêm pool 10 (notification-service) e default 10 (fraud-service) — não precisam de alteração.

---

## Passo 2 — Criar `docker-stack.yml`

Arquivo novo na raiz do projeto. Estrutura geral:

```yaml
name: acabou-o-mony

services:

  # ─── Load Balancer ──────────────────────────────────────

  traefik:
    image: traefik:v3.1
    command:
      - "--providers.docker.swarmMode=true"
      - "--providers.docker.exposedByDefault=false"
      - "--entrypoints.web.address=:80"
      - "--log.level=INFO"
    ports:
      - target: 80
        published: 80
        protocol: tcp
        mode: ingress
      - target: 8080
        published: 8080
        protocol: tcp
        mode: ingress
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
    deploy:
      replicas: 1
      placement:
        constraints:
          - node.role == manager
    networks:
      - aom-net
      - traefik-net

  # ─── Infraestrutura ─────────────────────────────────────

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-aom}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_DB: postgres
    command:
      - "postgres"
      - "-c"
      - "max_connections=200"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./scripts/init-databases.sql:/docker-entrypoint-initdb.d/01-init-databases.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-aom}"]
      interval: 10s
      timeout: 5s
      retries: 5
    deploy:
      replicas: 1
    networks:
      - aom-net

  redis:
    image: redis:7-alpine
    environment:
      REDIS_PASSWORD: ${REDIS_PASSWORD}
    command: >
      redis-server
      --requirepass ${REDIS_PASSWORD}
      --maxmemory 256mb
      --maxmemory-policy allkeys-lru
      --save 60 1
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD-SHELL", "redis-cli --no-auth-warning -a \"$REDIS_PASSWORD\" ping | grep PONG"]
      interval: 10s
      timeout: 5s
      retries: 5
    deploy:
      replicas: 1
    networks:
      - aom-net

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093,EXTERNAL://:9094
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,EXTERNAL://localhost:9094
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT,EXTERNAL:PLAINTEXT
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_NUM_PARTITIONS: 3
      KAFKA_LOG_RETENTION_HOURS: 168
      CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk
    volumes:
      - kafka_data:/var/lib/kafka/data
    healthcheck:
      test: ["CMD-SHELL", "kafka-topics --bootstrap-server localhost:9092 --list"]
      interval: 15s
      timeout: 10s
      retries: 10
      start_period: 30s
    deploy:
      replicas: 1
    networks:
      - aom-net

  kafka-init:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      kafka:
        condition: service_healthy
    command: >
      bash -c "
        echo 'Criando topicos Kafka...' &&
        kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic user-events          --partitions 3 --replication-factor 1 &&
        kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic user.registered     --partitions 3 --replication-factor 1 &&
        kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic user.login.success  --partitions 3 --replication-factor 1 &&
        kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic user.login.blocked  --partitions 3 --replication-factor 1 &&
        kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic user.2fa.enabled    --partitions 3 --replication-factor 1 &&
        kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic transaction.completed --partitions 6 --replication-factor 1 &&
        kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic transaction.failed    --partitions 3 --replication-factor 1 &&
        kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic fraud.detected        --partitions 3 --replication-factor 1 &&
        kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic order.created         --partitions 3 --replication-factor 1 &&
        kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic order.paid            --partitions 3 --replication-factor 1 &&
        kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic order.cancelled       --partitions 3 --replication-factor 1 &&
        kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic transaction.refunded  --partitions 3 --replication-factor 1 &&
        echo 'Topicos criados com sucesso.'
      "
    deploy:
      replicas: 1
      restart_policy:
        condition: none
    networks:
      - aom-net

  # ─── Serviços de Aplicação ──────────────────────────────

  api-gateway:
    image: aom/api-gateway:latest
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVER_PORT: 8080
      USER_SERVICE_URL: http://user-service:8081
      PAYMENT_SERVICE_URL: http://payment-service:8082
      ORDER_SERVICE_URL: http://order-service:8083
      NOTIFICATION_SERVICE_URL: http://notification-service:8084
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}
      INTERNAL_SECRET: ${INTERNAL_SECRET}
      NEW_RELIC_LICENSE_KEY: ${NEW_RELIC_LICENSE_KEY}
      NEW_RELIC_APP_NAME: aom-api-gateway
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 90s
    deploy:
      replicas: 3
      resources:
        limits:
          cpus: '1'
          memory: 1024M
        reservations:
          cpus: '0.25'
          memory: 512M
      restart_policy:
        condition: any
        delay: 5s
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.gateway.rule=PathPrefix(`/api`)"
      - "traefik.http.services.gateway.loadbalancer.server.port=8080"
      - "traefik.http.routers.gateway.entrypoints=web"
    networks:
      - aom-net
      - traefik-net

  user-service:
    image: aom/user-service:latest
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVER_PORT: 8081
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/user_db
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-aom}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      JWT_PRIVATE_KEY: ${JWT_PRIVATE_KEY}
      JWT_PUBLIC_KEY: ${JWT_PUBLIC_KEY}
      TOTP_AES_KEY: ${TOTP_AES_KEY}
      INTERNAL_SECRET: ${INTERNAL_SECRET}
      TOTP_ISSUER: ${TOTP_ISSUER:-AcabouoMony}
      MAIL_HOST: ${MAIL_HOST}
      MAIL_PORT: ${MAIL_PORT:-587}
      MAIL_USERNAME: ${MAIL_USERNAME}
      MAIL_PASSWORD: ${MAIL_PASSWORD}
      NEW_RELIC_LICENSE_KEY: ${NEW_RELIC_LICENSE_KEY}
      NEW_RELIC_APP_NAME: aom-user-service
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O /dev/null http://localhost:8081/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 90s
    deploy:
      replicas: 2
      resources:
        limits:
          cpus: '1'
          memory: 1024M
        reservations:
          cpus: '0.25'
          memory: 512M
      restart_policy:
        condition: any
    networks:
      - aom-net

  payment-service:
    image: aom/payment-service:latest
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVER_PORT: 8082
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/payment_db
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-aom}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      MERCADOPAGO_ACCESS_TOKEN: ${MERCADOPAGO_ACCESS_TOKEN}
      FRAUD_SERVICE_URL: http://fraud-service:8085
      ORDER_SERVICE_URL: http://order-service:8083
      USER_SERVICE_URL: http://user-service:8081
      INTERNAL_SECRET: ${INTERNAL_SECRET}
      NEW_RELIC_LICENSE_KEY: ${NEW_RELIC_LICENSE_KEY}
      NEW_RELIC_APP_NAME: aom-payment-service
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O /dev/null http://localhost:8082/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 90s
    deploy:
      replicas: 2
      resources:
        limits:
          cpus: '1'
          memory: 1024M
        reservations:
          cpus: '0.25'
          memory: 512M
      restart_policy:
        condition: any
    networks:
      - aom-net

  order-service:
    image: aom/order-service:latest
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVER_PORT: 8083
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/order_db
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-aom}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      INTERNAL_SECRET: ${INTERNAL_SECRET}
      NEW_RELIC_LICENSE_KEY: ${NEW_RELIC_LICENSE_KEY}
      NEW_RELIC_APP_NAME: aom-order-service
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O /dev/null http://localhost:8083/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 90s
    deploy:
      replicas: 2
      resources:
        limits:
          cpus: '0.5'
          memory: 512M
        reservations:
          cpus: '0.25'
          memory: 256M
      restart_policy:
        condition: any
    networks:
      - aom-net

  notification-service:
    image: aom/notification-service:latest
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVER_PORT: 8084
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/notification_db
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-aom}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      MAIL_HOST: ${MAIL_HOST}
      MAIL_PORT: ${MAIL_PORT:-587}
      MAIL_USERNAME: ${MAIL_USERNAME}
      MAIL_PASSWORD: ${MAIL_PASSWORD}
      MAIL_FROM: ${MAIL_FROM:-noreply@acaboumony.com}
      NEW_RELIC_LICENSE_KEY: ${NEW_RELIC_LICENSE_KEY}
      NEW_RELIC_APP_NAME: aom-notification-service
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O /dev/null http://localhost:8084/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 90s
    deploy:
      replicas: 2
      resources:
        limits:
          cpus: '0.5'
          memory: 512M
        reservations:
          cpus: '0.25'
          memory: 256M
      restart_policy:
        condition: any
    networks:
      - aom-net

  fraud-service:
    image: aom/fraud-service:latest
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVER_PORT: 8085
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/fraud_db
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-aom}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY}
      INTERNAL_SECRET: ${INTERNAL_SECRET}
      NEW_RELIC_LICENSE_KEY: ${NEW_RELIC_LICENSE_KEY}
      NEW_RELIC_APP_NAME: aom-fraud-service
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O /dev/null http://localhost:8085/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 90s
    deploy:
      replicas: 2
      resources:
        limits:
          cpus: '1'
          memory: 1024M
        reservations:
          cpus: '0.25'
          memory: 512M
      restart_policy:
        condition: any
    networks:
      - aom-net

networks:
  aom-net:
    driver: overlay
    attachable: true
  traefik-net:
    driver: overlay
    attachable: true

volumes:
  postgres_data:
    driver: local
  redis_data:
    driver: local
  kafka_data:
    driver: local
```

---

## Passo 3 — Diferenças entre `docker-compose.yml` e `docker-stack.yml`

| Item | `docker-compose.yml` (dev) | `docker-stack.yml` (swarm) | Motivo |
|---|---|---|---|
| `container_name` | ✅ Sim | ❌ Removido | Swarm gera nomes únicos |
| `profiles: ["app"]` | ✅ Sim | ❌ Removido | Swarm não suporta profiles |
| `restart: unless-stopped` | ✅ Sim | `deploy.restart_policy.condition: any` | Gerenciado pelo Swarm |
| `depends_on: condition: service_healthy` | ✅ Sim | ❌ Removido (só mantido no kafka-init) | Swarm não garante ordem |
| Rede | `bridge` | `overlay` | Necessário para multi-node |
| `ports:` | `"8080:8080"` | `target/published/mode: ingress` | Routing mesh |
| Traefik | ❌ | ✅ Adicionado | Load balancer externo |
| Labels Traefik | ❌ | ✅ No api-gateway | Descoberta de serviço |

---

## Passo 4 — Lista Completa de Alterações em Arquivos

### Arquivos para ALTERAR (4 linhas no total)

| Arquivo | Linha | Configuração | De | Para |
|---|---|---|---|---|
| `services/user-service/src/main/resources/application-docker.yml` | 7 | `maximum-pool-size` | 20 | 10 |
| `services/payment-service/src/main/resources/application-docker.yml` | 7 | `maximum-pool-size` | 20 | 10 |
| `services/order-service/src/main/resources/application.yml` | 12 | `maximum-pool-size` | 20 | 10 |

### Arquivos para CRIAR

| Arquivo | Conteúdo |
|---|---|
| `docker-stack.yml` | Stack completa com Swarm + Traefik (Passo 2) |

### Arquivos que NÃO mudam

- `docker-compose.yml` — continua para dev local
- `application.yml` de user, payment, notification, fraud services
- `application-docker.yml` de notification-service (não existe, não precisa)
- `application-docker.yml` de order, fraud services (não existem, não precisam)
- `Dockerfile` de todos os serviços
- Todo código Java

---

## Passo 5 — Comandos para Deploy

### Build das imagens (uma vez)

```bash
docker compose --profile app build
```

### Inicializar Swarm (uma vez)

```bash
docker swarm init
```

### Deploy da stack

```bash
export $(grep -v '^\s*#' .env | xargs) && docker stack deploy -c docker-stack.yml aom

### Verificar serviços

```bash
docker service ls

# Ver réplicas de um serviço específico
docker service ps aom_api-gateway
docker service ps aom_user-service
docker service ps aom_payment-service
```

### Logs

```bash
# Logs de um serviço (todas as réplicas)
docker service logs aom_api-gateway -f

# Logs de uma réplica específica
docker service ps aom_api-gateway --no-trunc  # pegar o container ID
docker logs <container-id> -f
```

---

## Passo 6 — Escalonamento

### Comandos manuais

```bash
# Escalar api-gateway para 5 réplicas
docker service scale aom_api-gateway=5

# Escalar payment-service para 4 réplicas
docker service scale aom_payment-service=4

# Reduzir user-service para 2 réplicas
docker service scale aom_user-service=2

# Ver estado atual de todos os serviços
docker service ls
```

### Réplicas sugeridas por ambiente

| Serviço | Dev/QA | Staging | Produção (inicial) |
|---|---|---|---|
| api-gateway | 3 | 3 | 5 |
| user-service | 2 | 2 | 3 |
| payment-service | 2 | 3 | 5 |
| order-service | 2 | 2 | 3 |
| notification-service | 2 | 2 | 3 |
| fraud-service | 2 | 2 | 2 |

---

## Passo 7 — Health Checks e Startup

Como Swarm não respeita `depends_on`, a ordem de startup é garantida por:

1. **Postgres, Redis, Kafka** — health checks impedem que o Swarm os considere "prontos" antes do tempo
2. **kafka-init** — `depends_on: kafka: condition: service_healthy` ainda funciona (exceção por ser um job one-shot)
3. **Serviços Spring** — HikariCP faz retry automático de conexão com o banco. Se o Postgres não estiver pronto, ele aguarda e tenta de novo
4. **`start_period: 90s`** — o Swarm não considera o serviço como "falhou" durante os primeiros 90s, dando tempo para o Spring inicializar e o Postgres responder

---

## Passo 8 — Parar e Remover

```bash
# Remover a stack (containers param, volumes permanecem)
docker stack rm aom

# Remover volumes também
docker volume rm aom_postgres_data aom_redis_data aom_kafka_data

# Sair do Swarm (se não for mais usar)
docker swarm leave --force
```

---

## Riscos e Mitigações

| Risco | Probabilidade | Impacto | Mitigação |
|---|---|---|---|
| **Flyway race condition** — duas réplicas migram simultaneamente | Média | Baixo | Flyway usa lock na tabela `flyway_schema_history` — uma réplica espera a outra |
| **max_connections estourado** — pool por réplica excede limite do Postgres | Baixa | Alto | `max_connections=200` + pools reduzidos para 10 = 120 conexões máximas, folga de 80 |
| **Kafka rebalance lento** — ao escalar o notification-service, consumer group rebalanceia | Média | Médio | Partições já ≥ número de réplicas. Usar `cooperative-sticky` se necessário |
| **DNS cache** — o Spring Cloud Gateway pode cachear resolução DNS | Baixa | Baixo | Spring Boot 3.4 usa DNS cache com TTL default de 30s. Swarm DNS responde rápido |
| **Sticky session perdida** — requisições vão para réplicas diferentes | Nula | Nenhum | O projeto é stateless (JWT, sem sessão HTTP) — sem impacto |
| **Traefik sem réplica do api-gateway** — se todas as réplicas morrerem | Baixa | Alto | Swarm reinicia automaticamente via `restart_policy`. Alertas no New Relic |

---

## Checklist de Validação

- [ ] Pool Hikari alterado nos 3 arquivos (user, payment, order)
- [ ] `docker-stack.yml` criado na raiz
- [ ] `docker compose --profile app build` executa sem erros
- [ ] `docker swarm init` executado
- [ ] `export $(grep -v '^\s*#' .env | xargs) && docker stack deploy -c docker-stack.yml aom` executa sem erros
- [ ] `docker service ls` mostra todos os serviços como `replicated`
- [ ] `docker service ps aom_traefik` mostra traefik rodando
- [ ] `docker service ps aom_postgres` mostra postgres como `healthy`
- [ ] `docker service ps aom_kafka-init` mostra status `complete` (job one-shot)
- [ ] Acessar `http://localhost/api/v1/auth/**` via Traefik funciona
- [ ] `docker service scale aom_user-service=3` adiciona réplica sem quebrar
- [ ] Verificar logs para confirmar que não há erros de conexão com Postgres

---

## Resumo das Alterações

```
ACABOU O MONY — Swarm Migration
├── Arquivos CRIADOS
│   └── docker-stack.yml                          (novo)
│
├── Arquivos ALTERADOS (pool de conexões)
│   ├── services/user-service/application-docker.yml   (20 → 10)
│   ├── services/payment-service/application-docker.yml (20 → 10)
│   └── services/order-service/application.yml          (20 → 10)
│
├── Arquivos NÃO ALTERADOS
│   ├── docker-compose.yml
│   ├── NENHUM Dockerfile
│   ├── NENHUM arquivo .java
│   └── application.yml dos demais serviços
│
└── Total de linhas alteradas no código: 3
```
