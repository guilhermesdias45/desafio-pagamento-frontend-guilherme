# Structure — Acabou o Mony

## Diretório Raiz

```
├── .opencode/            # Config opencode (skills, agents, templates, commands)
├── .specs/               # SDD: specs do projeto
├── .specify/             # Spec-Kit templates legados
├── agents/               # Specs dos AI agents (fraud, transaction processor)
├── docs/                 # Documentação técnica, sprints, testes
├── monitoring/           # Prometheus + Grafana config
├── qa-output/            # Resultados de QA e PCI reports
├── scripts/              # Scripts auxiliares PowerShell
├── services/             # Código dos 6 microserviços
│   ├── api-gateway/
│   ├── user-service/
│   ├── payment-service/
│   ├── order-service/
│   ├── notification-service/
│   └── fraud-service/
├── specs/                # SDD specs originais (referência)
├── tasks/                # Tasks de desenvolvimento
├── docker-compose.yml
├── docker-stack.yml
└── AGENTS.md             # Orquestrador (este arquivo)
```

## Frontend (a ser criado)

```
frontend/
├── src/
│   ├── api/              # API client + funções por recurso
│   ├── components/       # UI components reutilizáveis
│   ├── contexts/         # AuthContext (JWT, refresh)
│   ├── hooks/            # Hooks customizados
│   ├── layouts/          # Layouts (AuthLayout, AppLayout)
│   ├── lib/              # Utilitários (format, validators)
│   ├── pages/            # Páginas agrupadas por área
│   │   ├── auth/         # Register, Login, 2FA, ConfirmEmail
│   │   ├── order/        # CreateOrder, OrderHistory, OrderDetail
│   │   ├── checkout/     # Checkout, PaymentResult
│   │   └── merchant/     # Dashboard, TransactionDetail, Refund
│   ├── routes/           # Route definitions + guards
│   └── types/            # Tipos compartilhados
├── public/
├── __mocks__/            # Mocks entre áreas (documentados)
├── Dockerfile
├── nginx.conf
├── vite.config.ts
├── tailwind.config.ts
├── tsconfig.json
└── package.json
```

## Onde encontrar cada spec original

| Área frontend | Spec backend de referência | Plano técnico |
|---------------|---------------------------|---------------|
| Auth | `specs/user-service/spec.md` | `specs/user-service/plan.md` |
| Pagamento | `specs/payment-service/spec.md` | `specs/payment-service/plan.md` |
| Pedido | `specs/order-service/spec.md` | — |
| Webhook | `specs/payment-service/spec.md §7` | — |
