# Stack — Acabou o Mony (Backend)

## Tecnologias

| Camada | Tecnologia | Versão | Onde encontrar referência |
|--------|-----------|--------|--------------------------|
| Linguagem | Java | 21 (LTS) | `docs/tech/stack.md` |
| Framework | Spring Boot | 3.4.x | `docs/tech/stack.md` |
| API Gateway | Spring Cloud Gateway | 4.x | `services/api-gateway/` |
| Banco | PostgreSQL | 16 | `docker-compose.yml` |
| Cache | Redis | 7 | `docker-compose.yml` |
| Mensageria | Apache Kafka | 3.7 (KRaft) | `docker-compose.yml` |
| Gateway Pagamento | Mercado Pago SDK Java | 2.1.x | `specs/payment-service/spec.md` |
| AI | Claude API (Anthropic) | 0.8.x | `services/fraud-service/` |
| Monitoramento | New Relic | — | `docs/tech/stack.md` |
| Container | Docker Compose + Swarm | — | `docker-compose.yml`, `docker-stack.yml` |

## Portas dos Serviços

| Serviço | Porta | Onde encontrar |
|---------|-------|----------------|
| api-gateway | 8080 | `docker-compose.yml`, `services/api-gateway/` |
| user-service | 8081 | `services/user-service/` |
| payment-service | 8082 | `services/payment-service/` |
| order-service | 8083 | `services/order-service/` |
| notification-service | 8084 | `services/notification-service/` |
| fraud-service | 8085 (interno) | `services/fraud-service/` |

## Frontend Stack (a ser criado)

| Camada | Tecnologia | Versão |
|--------|-----------|--------|
| Framework | React | 18 |
| Linguagem | TypeScript | 5.x |
| Build | Vite | 5 |
| Testes | Vitest + React Testing Library | — |
| Roteamento | React Router | v6 |
| Forms | React Hook Form + Zod | — |
| Estilização | Tailwind CSS | 4 |
| Card Token | Mercado Pago JS SDK | — |
