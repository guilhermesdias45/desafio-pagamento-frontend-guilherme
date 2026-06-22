# Acabou o Mony — Frontend

**Vision:** Web UI completa para a API de pagamentos Acabou o Mony. Clientes podem se cadastrar, criar pedidos e pagar com cartão via Mercado Pago. Lojistas gerenciam transações e estornos.

**For:** Clientes (CUSTOMER) e Lojistas (MERCHANT_OWNER) brasileiros.

**Solves:** Não existe interface gráfica para a API — apenas curl/Postman. Lojistas precisam de dashboard para operar; clientes precisam de checkout funcional.

## Goals

- Fluxo completo: cadastro → login → criar pedido → pagar → ver resultado
- Dashboard do lojista: listar transações + estornar
- Cobertura de testes unitários ≥ 80% por módulo
- Build Docker funcional integrado ao `docker-compose.yml` existente

## Tech Stack

| Camada | Tecnologia |
|--------|-----------|
| Framework | React 18 + TypeScript |
| Build | Vite 5 |
| Testes unitários | Vitest + React Testing Library |
| Testes E2E | Playwright (após todo código pronto) |
| Roteamento | React Router v6 |
| Formulários | React Hook Form + Zod |
| Estilização | Tailwind CSS 4 |
| Card Token | Mercado Pago JS SDK (pago.js) |
| HTTP | Fetch API (nativa) |
| Infra Docker | Nginx (static build) |

## Scope

**v1 includes:**

- Autenticação: registro, login, 2FA, refresh token, confirmação de email
- Pedidos: criar, listar, detalhar
- Checkout: formulário de cartão → card token MP → processar pagamento
- Dashboard lojista: listar transações, estornar (total/parcial)
- Histórico do cliente: ver pedidos e transações

**Explicitly out of scope:**

- PIX e boleto (apenas cartão de crédito no MVP)
- STAFF role (apenas CUSTOMER + MERCHANT_OWNER)
- Regeneração de recovery codes 2FA
- Tema escuro / white-label
- Responsividade mobile (desktop-first)

## Constraints

- API Gateway em `localhost:8080` (dev) — proxy Vite configurado
- JWT expira em 15 min, refresh cookie httpOnly 7 dias
- Card token MP exige keys do sandbox Mercado Pago
- Docker Compose profile `app` para subir com backend
