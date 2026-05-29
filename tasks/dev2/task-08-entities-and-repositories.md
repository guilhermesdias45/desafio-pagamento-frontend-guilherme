# Task 08: Entidades JPA + Repositories (User, Merchant, RecoveryCode)

## Objective
Criar as 3 entidades JPA mapeando para `users`, `merchants`, `recovery_codes` e os respectivos Spring Data JPA Repositories. **Sem User.userAuditLog** — `UserAuditLog` é uma entidade separada cuja gestão é delegada a um repository genérico em task-15/task-16.

## Context
**Quick Context:**
- `ddl-auto: validate` exige que toda coluna `@Column` exista na migration correspondente — qualquer divergência quebra o startup.
- Usar `@Enumerated(EnumType.STRING)` + tipo customizado `@JdbcType` ou `@Column(columnDefinition = "user_role")` para mapear os enums PostgreSQL (truque: passar `String` para Hibernate e ele faz cast implícito no INSERT/UPDATE com PostgreSQL JDBC).
- `User.merchant` é `@ManyToOne` com `merchant_id` FK NULLABLE (`@JoinColumn(name = "merchant_id", nullable = true)`).
- `Merchant.owner` é `@ManyToOne` com `owner_id` FK NOT NULL.
- **Cuidado com ciclo:** evitar `toString()` recursivo. Usar Lombok `@ToString(exclude = ...)` ou anotar manualmente.

Ler antes:
- `task-03-flyway-v1-users.md` e `task-04-flyway-v2-merchants.md` (schema exato das colunas)
- `task-05-flyway-v3-recovery-codes.md`
- `task-07-enums.md` (enums Java)

## Target Files
**Create:**
- `services/user-service/src/main/java/com/acaboumony/user/domain/entity/User.java`
- `services/user-service/src/main/java/com/acaboumony/user/domain/entity/Merchant.java`
- `services/user-service/src/main/java/com/acaboumony/user/domain/entity/RecoveryCode.java`
- `services/user-service/src/main/java/com/acaboumony/user/domain/entity/UserAuditLog.java`
- `services/user-service/src/main/java/com/acaboumony/user/repository/UserRepository.java`
- `services/user-service/src/main/java/com/acaboumony/user/repository/MerchantRepository.java`
- `services/user-service/src/main/java/com/acaboumony/user/repository/RecoveryCodeRepository.java`
- `services/user-service/src/main/java/com/acaboumony/user/repository/UserAuditLogRepository.java`
- `services/user-service/src/test/java/com/acaboumony/user/repository/UserRepositoryIT.java`
- `services/user-service/src/test/java/com/acaboumony/user/repository/MerchantRepositoryIT.java`
- `services/user-service/src/test/java/com/acaboumony/user/repository/RecoveryCodeRepositoryIT.java`

## Dependencies
- Depends on: task-04, task-05, task-06, task-07, task-10 (BaseIntegrationTest)
- Blocks: task-14, task-15, task-16

## TDD Mode

### RED
Para cada repository, criar um `*IT.java` que estende `BaseIntegrationTest` (task-10). Testes:

`UserRepositoryIT`:
- `deve_persistir_e_recuperar_user_quando_save_e_findById()`
- `deve_encontrar_user_por_email_quando_findByEmail()`
- `deve_retornar_optional_empty_quando_email_nao_existe()`
- `deve_falhar_quando_email_duplicado_quando_save()`

`MerchantRepositoryIT`:
- `deve_persistir_merchant_com_owner_quando_save()`
- `deve_encontrar_merchant_por_cnpj_quando_findByCnpj()`
- `deve_falhar_quando_cnpj_duplicado_quando_save()`
- `deve_persistir_merchant_e_atualizar_user_merchant_id_quando_save_em_transacao()` — verifica round-trip do FK circular

`RecoveryCodeRepositoryIT`:
- `deve_persistir_recovery_code_associado_a_user()`
- `deve_listar_recovery_codes_nao_usados_por_user_quando_findByUserIdAndUsedFalse()`
- `deve_deletar_em_cascade_quando_user_deletado()`

Compila → falha (entidades/repos não existem).

### GREEN
1. **User.java** — `@Entity @Table(name = "users")` com campos: `id UUID @Id`, `email String`, `passwordHash String`, `fullName String`, `role UserRole` (`@Enumerated(STRING) @Column(columnDefinition = "user_role")`), `merchant Merchant @ManyToOne(fetch = LAZY) @JoinColumn(name = "merchant_id")`, `status UserStatus` (mesmo padrão de enum), `totpEnabled boolean`, `totpSecretEncrypted String` (nullable), `createdAt Instant`, `updatedAt Instant`. Usar `@PrePersist` para gerar UUID e setar timestamps.
2. **Merchant.java** — `@Entity @Table(name = "merchants")` com campos: `id UUID`, `companyName String`, `cnpj String`, `owner User @ManyToOne @JoinColumn(name = "owner_id", nullable = false)`, `status MerchantStatus`, `createdAt`, `updatedAt`.
3. **RecoveryCode.java** — `@Entity @Table(name = "recovery_codes")` com `id UUID`, `user User @ManyToOne @JoinColumn(name = "user_id")`, `codeHash String`, `used boolean`, `usedAt Instant nullable`, `createdAt`.
4. **UserAuditLog.java** — `@Entity @Table(name = "user_audit_logs")` com `id UUID`, `userId UUID nullable` (sem `@ManyToOne` para evitar dependência cíclica; armazenar UUID direto), `eventType String`, `ipAddress String nullable`, `deviceFingerprint String nullable`, `createdAt Instant`.
5. **Repositories** — interfaces estendendo `JpaRepository<Entity, UUID>`:
   - `UserRepository`: `Optional<User> findByEmail(String email)`, `boolean existsByEmail(String email)`.
   - `MerchantRepository`: `Optional<Merchant> findByCnpj(String cnpj)`, `boolean existsByCnpj(String cnpj)`.
   - `RecoveryCodeRepository`: `List<RecoveryCode> findByUserIdAndUsedFalse(UUID userId)`, `long countByUserIdAndUsedFalse(UUID userId)`.
   - `UserAuditLogRepository`: vazio (CRUD básico do `JpaRepository` é suficiente).

### REFACTOR
- Lombok `@Getter` + builder em vez de setters públicos (entidades quase-imutáveis com setters mínimos: `setStatus`, `setMerchant`, `setTotpEnabled`, `setTotpSecretEncrypted`, `setPasswordHash`, `setFullName`).
- `@ToString(exclude = {"merchant", "owner"})` para evitar recursão.
- Garantir que campos sensíveis (`passwordHash`, `totpSecretEncrypted`) **não** aparecem em `toString()`.

## Acceptance Criteria
- [ ] 4 entidades JPA criadas com mapeamento exato para os schemas das migrations
- [ ] 4 repositories Spring Data criados; queries derivadas (`findByEmail`, `findByCnpj`, `findByUserIdAndUsedFalse`) funcionam
- [ ] Aplicação sobe contra Testcontainers PostgreSQL **sem erro de validate** (`ddl-auto: validate` happy)
- [ ] `UserRepositoryIT`, `MerchantRepositoryIT`, `RecoveryCodeRepositoryIT` passam (10+ testes verdes no total)
- [ ] `toString()` em `User`, `Merchant`, `RecoveryCode` NÃO inclui `passwordHash`, `totpSecretEncrypted`, `codeHash`
- [ ] Deletar `User` → recovery codes associados são deletados em cascade (testado em RecoveryCodeRepositoryIT)
