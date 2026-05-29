# Task 07: Enums Java — UserRole, UserStatus, MerchantStatus

## Objective
Criar os 3 enums Java que mapeiam para os enums PostgreSQL criados nas migrations V1 e V2.

## Context
**Quick Context:**
- Valores **DEVEM** bater exatamente com os enums PostgreSQL (case-sensitive).
- Hibernate precisa de `@Enumerated(EnumType.STRING)` nas entidades (task-08) para mapear corretamente; o enum em si é só uma classe Java pura.
- Pode rodar em paralelo com tasks 05, 06, 08, 09, 10.

Ler antes:
- `specs/user-service/plan.md` §"Modelo de Roles" (linhas 31-45)
- Definições dos enums em `task-03-flyway-v1-users.md` e `task-04-flyway-v2-merchants.md`

## Target Files
**Create:**
- `services/user-service/src/main/java/com/acaboumony/user/domain/enums/UserRole.java`
- `services/user-service/src/main/java/com/acaboumony/user/domain/enums/UserStatus.java`
- `services/user-service/src/main/java/com/acaboumony/user/domain/enums/MerchantStatus.java`

## Dependencies
- Depends on: None (após Wave 1; paralelo com tasks 05, 06, 08, 09, 10)
- Blocks: task-08, task-14, task-15, task-16

## TDD Mode

### RED
Criar `services/user-service/src/test/java/com/acaboumony/user/domain/enums/EnumValuesTest.java`:
- `deve_ter_3_valores_em_UserRole_quando_invocado_values()` — espera `CUSTOMER`, `MERCHANT_OWNER`, `STAFF` exatamente.
- `deve_ter_4_valores_em_UserStatus_quando_invocado_values()` — espera `PENDING_EMAIL_CONFIRMATION`, `ACTIVE`, `LOCKED`, `DISABLED`.
- `deve_ter_3_valores_em_MerchantStatus_quando_invocado_values()` — espera `ACTIVE`, `SUSPENDED`, `INACTIVE`.

Compila → falha (enums não existem).

### GREEN
Criar os 3 enums com os valores exatos.

```java
public enum UserRole { CUSTOMER, MERCHANT_OWNER, STAFF }
public enum UserStatus { PENDING_EMAIL_CONFIRMATION, ACTIVE, LOCKED, DISABLED }
public enum MerchantStatus { ACTIVE, SUSPENDED, INACTIVE }
```

### REFACTOR
- Adicionar JavaDoc curto em cada enum explicando o domínio (ex: `UserRole.STAFF` — "Operador de merchant; não pode comprar; criado via convite — Sprint 2").
- Considerar método helper `UserRole.canPurchase()` retornando `this != STAFF` — testar separadamente.

## Acceptance Criteria
- [ ] `EnumValuesTest` passa (3 testes verdes)
- [ ] `UserRole.values().length == 3`, `UserStatus.values().length == 4`, `MerchantStatus.values().length == 3`
- [ ] Nomes exatos batem com os enums PostgreSQL (verificar contra `task-03` e `task-04`)
- [ ] `UserRole.canPurchase()` retorna `false` apenas para `STAFF` (se implementado em REFACTOR — teste adicional)
