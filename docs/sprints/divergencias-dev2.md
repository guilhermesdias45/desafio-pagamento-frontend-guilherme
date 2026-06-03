# Divergências Reportadas — Dev 2

**De:** Dev 1
**Data:** 2026-06-03
**Sprint:** 4

---

## D-001: Endpoint `GET /internal/users/{customerId}` inexistente no user-service

**Problema:** O `UserServiceClient.java:50` do payment-service chama `GET /internal/users/{customerId}` para validar clientes, mas o user-service **não possui** este endpoint. Ele expõe apenas:
- `POST /internal/auth/validate-token` — validação de JWT (usado pelo api-gateway)
- `GET /api/v1/users/me` — perfil do usuário autenticado (requer JWT)

**Impacto:** Todas as transações caem no `catch (Exception)` e usam fallback `valid=true`, aprovando clientes inválidos.

**Ação necessária:** Criar endpoint `GET /internal/users/{customerId}` no user-service **OU** alinhar com Dev 1 sobre mecanismo alternativo de validação.

---

## D-003: Internal secret com valores divergentes

**Problema:** Três valores distintos para o mesmo secret:
| Quem envia | Valor | Configurado em |
|-----------|-------|---------------|
| api-gateway | `${INTERNAL_SERVICE_SECRET:dev-secret}` | `application.yml:81` |
| payment-service | `${INTERNAL_SECRET:dev-secret}` (agora externalizado) | `application.yml:74` |
| user-service (valida) | `${INTERNAL_SECRET:dev-internal-secret}` | `application.yml:40` |

**Correção realizada (Dev 1):** Payment-service agora lê de `payment.internal-secret` via env var `INTERNAL_SECRET`.

**Pendente (Dev 2):**
- Alinhar o nome da env var no api-gateway de `INTERNAL_SERVICE_SECRET` para `INTERNAL_SECRET`
- Ajustar o valor default no user-service de `dev-internal-secret` para `dev-secret` (ou vice-versa, mas precisa ser consistente entre todos)

---

## D-004: `X-User-Role` (singular) vs `X-User-Roles` (plural)

**Problema:**
- api-gateway injeta `X-User-Role` (singular) via `AuthenticationFilter.java:69`
- order-service espera `X-User-Roles` (plural) em `OrderController.java`

**Correção realizada (Dev 1):** Payment-service `OrderServiceClient.java:47` alterado de `X-User-Roles` para `X-User-Role` (singular).

**Pendente:**
- **Dev 2:** Verificar se api-gateway deve manter `X-User-Role` ou alterar para `X-User-Roles`
- **Dev 3:** Verificar se order-service deve aceitar `X-User-Role` (singular) ou `X-User-Roles` (plural)
- Decidir qual padrão seguir e alinhar entre os 3 serviços
