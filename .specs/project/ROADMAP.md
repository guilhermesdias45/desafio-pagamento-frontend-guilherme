# Roadmap — Frontend Acabou o Mony

## Milestones

### M1: Foundation (Setup + Shared)
- Scaffold Vite + React + TypeScript + Tailwind
- API client (`api/`)
- AuthContext + hooks
- Route guards + layouts
- Types compartilhados
- **Dockerfile + nginx.conf + docker-compose**

### M2: Auth Flow
- Register (CUSTOMER + MERCHANT_OWNER)
- Login + JWT management + refresh automático
- Email Confirmation
- 2FA Setup / Verify / Confirm / Disable
- Tratamento de erros (locked account, invalid credentials)

### M3: Order Flow
- Criar pedido (selecionar merchant, adicionar itens)
- Listar pedidos do cliente (paginado, filtro por status)
- Detalhe do pedido

### M4: Checkout Flow
- Gerar card token via Mercado Pago JS SDK
- Formulário de pagamento (card number, expiry, CVV, parcelas)
- Processar pagamento via `POST /api/v1/transactions`
- Tela de resultado (success/failure)
- Tratamento de erros (card declined, fraud, timeout)

### M5: Merchant Dashboard
- Listar transações do merchant (paginado, ordenado)
- Detalhe da transação
- Estorno total e parcial (modal de confirmação)

### M6: Integration
- Resolver todos os mocks entre áreas
- Build check (TypeScript + Vite)
- Testes E2E com Playwright
- Integração no docker-compose.yml

## Dependencies Between Areas

```
M1 (Shared) ──→ M2 (Auth) ──→ M3 (Order) ──→ M4 (Checkout)
                                                       
M1 (Shared) ──→ M2 (Auth) ──→ M5 (Merchant Dashboard)
                                       
M6 (Integration) depende de M1..M5 completos
```

## Cycle Plan

| Cycle | Foco | Areas |
|-------|------|-------|
| 1 | Foundation + Auth | shared, auth |
| 2 | Order + Checkout | order, checkout |
| 3 | Merchant | merchant |
| 4 | Integration | shared (resolver mocks), E2E |
