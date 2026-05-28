        # Divisão do Time — Acabou o Mony

**Time:** 3 pessoas
**Metodologia:** SDD + TDD
**Regra geral:** Nenhum código sem spec aprovada em `specs/`. Toda interface entre módulos segue `docs/architecture/api-contracts.md`.

---

## Dev 1 — Core de Pagamentos + Antifraude

**Foco:** O dinheiro em movimento. Módulo mais crítico do sistema.

### Serviços

- `payment-service` (porta 8082)
- `fraud-service` (porta 8085)

### Specs de responsabilidade

| Spec | Pasta |
|------|-------|
| Payment Service | `specs/payment-service/` |
| Fraud Service | `specs/fraud-service/` |

### Agents

- `agents/fraud-detection/` — Fraud Detection Agent (Claude API)
- `agents/transaction-processor/` — Transaction Processor Agent

### Entregas por sprint

| Sprint | Entrega |
|--------|---------|
| 1 | fraud-service completo com todas as regras determinísticas |
| 1 | POST /transactions funcionando com Mercado Pago sandbox |
| 2 | Estorno, consulta, idempotência, webhook do MP |
| 2 | Integração Claude API no fraud agent (casos borderline 70-89) |

### Interfaces que consome

- `POST /internal/auth/validate-token` — validar JWT (via api-gateway, headers X-User-*)
- `fraud-service` — chamada REST interna síncrona antes de cada pagamento

### Interfaces que expõe

- `POST /api/v1/transactions` — processar pagamento
- `GET /api/v1/transactions/{id}` — consultar transação
- `POST /api/v1/transactions/{id}/refund` — estornar
- `POST /internal/fraud/score` — score de fraude (apenas para payment-service)

---

## Dev 2 — Auth, API Gateway e Infraestrutura

**Foco:** Segurança, identidade e a base que todos os outros serviços precisam para funcionar.

### Serviços

- `user-service` (porta 8081)
- `api-gateway` (porta 8080)
- Infraestrutura Docker Compose
- Monitoramento New Relic
- CI/CD GitHub Actions

### Specs de responsabilidade

| Spec | Pasta |
|------|-------|
| User Service | `specs/user-service/` |
| API Gateway | `specs/api-gateway/` |

### Entregas por sprint

| Sprint | Entrega |
|--------|---------|
| 1 | user-service: login, JWT RS256, refresh token, rate limiting |
| 1 | api-gateway: roteamento, JWT validation, rate limiting por rota |
| 1 | docker-compose.yml base (PostgreSQL, Redis, Kafka, todos os serviços) |
| 2 | 2FA completo (TOTP + recovery codes) |
| 2 | Circuit breaker no api-gateway (Resilience4j) |
| 3 | New Relic configurado em todos os serviços |
| 3 | CI/CD pipeline (GitHub Actions: build, test, docker build/push) |

### Interfaces que expõe (para todos os outros serviços)

- `POST /internal/auth/validate-token` — Dev 1, Dev 3 dependem disso
- JWT filter (`SecurityFilterChain`) — todos os módulos usam
- Headers injetados pelo api-gateway: `X-User-Id`, `X-User-Email`, `X-User-Roles`, `X-Merchant-Id`, `X-Correlation-Id`

### Responsabilidades transversais

- `docker-compose.yml` na raiz do projeto — todos os serviços
- Flyway migrations do banco (coordenar schemas com Dev 1 e Dev 3)
- `.env.example` com todas as variáveis necessárias
- Configuração do New Relic em todos os Dockerfiles

---

## Dev 3 — Pedidos e Notificações

**Foco:** O ciclo de vida do pedido e a comunicação com o cliente.

### Serviços

- `order-service` (porta 8083)
- `notification-service` (porta 8084)

### Specs de responsabilidade

| Spec | Pasta |
|------|-------|
| Order Service | `specs/order-service/` |
| Notification Service | `specs/notification-service/` |

### Entregas por sprint

| Sprint | Entrega |
|--------|---------|
| 1 | order-service: CRUD de pedidos, cálculo de total, idempotência |
| 2 | order-service: consumer Kafka (transaction.completed → PAID) |
| 2 | order-service: job de expiração de pedidos (15 min) |
| 2 | notification-service: consumer Kafka + emails via SMTP |
| 3 | notification-service: todos os 8 tipos de email com templates HTML |

### Interfaces que consome

- `POST /api/v1/transactions` — payment-service precisa do `orderId` para processar
- Headers JWT do api-gateway — para autenticação de rotas

### Interfaces que expõe

- `POST /api/v1/orders`
- `GET /api/v1/orders/{id}`
- `GET /api/v1/orders`
- `DELETE /api/v1/orders/{id}`

---

## Regras de Colaboração

### Antes de começar qualquer feature

1. Spec do módulo está em `specs/[servico]/spec.md` e foi revisada pelo time
2. Interface com outros módulos está documentada em `docs/architecture/api-contracts.md`
3. Migration do banco (se houver) alinhada com Dev 2

### Fluxo de trabalho

```
1. Pegar spec.md do seu módulo
2. Ler plan.md para decisões técnicas
3. Escrever testes (TDD — RED)
4. Implementar até passar (TDD — GREEN)
5. Refatorar (REFACTOR)
6. PR com referência à spec (ex: "Implements SPEC-PAY-001")
7. Code review de pelo menos 1 outro dev
8. Merge só com todos os testes passando
```

### Pontos de sincronização obrigatórios

| Quando | O que alinhar |
|--------|--------------|
| Início do projeto | docker-compose.yml base — Dev 2 lidera, todos validam |
| Sprint 1, Dia 1 | Todos: alinhar formato do JWT (claims, como serviços consomem) |
| Sprint 1, Dia 3 | Dev 1 + Dev 2: validar contrato `/internal/auth/validate-token` |
| Sprint 1, Dia 5 | Dev 1 + Dev 3: validar contrato `POST /api/v1/orders` (Dev 1 precisa do orderId) |
| A cada sprint | Demo interna do que cada um entregou |

### Banco de dados — regras de convivência

- **Dev 2** cria o `docker-compose.yml` com o container PostgreSQL único
- Cada serviço gerencia suas próprias migrations Flyway independentemente
- Formato: `V{numero}__{descricao_snake_case}.sql`
- Nunca acessar banco de outro serviço diretamente (isolamento de dados)

### Kafka — tópicos por dono

| Tópico | Dono (produtor) | Consumidores |
|--------|----------------|--------------|
| `user.*` | Dev 2 (user-service) | Dev 3 (notification-service) |
| `transaction.*` | Dev 1 (payment-service) | Dev 3 (order-service, notification-service) |
| `fraud.*` | Dev 1 (fraud-service) | Dev 1 (payment-service), Dev 3 (notification-service) |
| `order.*` | Dev 3 (order-service) | Dev 3 (notification-service) |

---

## Dependências Críticas de Entrega

```
Dev 2 entrega JWT + api-gateway
        ↓
Dev 1 implementa fraud-service + POST /transactions
        ↓
Dev 3 implementa order-service (POST /orders que Dev 1 usa)
        ↓
Dev 3 implementa notification-service (consome eventos de todos)
```

**Dev 2 é desbloqueador do time** — JWT e docker-compose precisam estar prontos no Sprint 1.

**Nota:** Dev 1 e Dev 3 têm dependência mútua no Sprint 1: `POST /transactions` precisa de `orderId` que vem do `POST /orders`. Alinhar no Dia 5 do Sprint 1.
