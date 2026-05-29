# Task 14: RegisterRequest + @ValidRegisterRequest + MerchantService + AuthService.register()

## Objective
Implementar o fluxo de registro completo: DTO `RegisterRequest` (Record) com validação condicional via custom validator `@ValidRegisterRequest`, `MerchantService.createAtomically()` (criação atômica de merchant), e `AuthService.register()` cobrindo CUSTOMER e MERCHANT_OWNER, emitindo evento `user.registered` no Kafka.

## Context
**Quick Context:**
- **MERCHANT_OWNER é atômico**: User + Merchant criados na mesma transação. Falha em qualquer um → rollback total (CE-REG-007).
- **STAFF rejeitado**: tentativa de registrar com `role=STAFF` retorna `INVALID_ROLE` (CE-REG-006) — STAFF só via convite no Sprint 2.
- Senha: BCrypt rounds=12 via `BCryptPasswordEncoder(12)`. NUNCA logada.
- Token de confirmação de email: UUID no Redis com key `email_confirm:{token}`, TTL 24h. Endpoint que consome esse token é implementado em task-20 (AuthController).
- `RegisterRequest` é um **Record** com Bean Validation. `@ValidRegisterRequest` é class-level annotation que valida lógica condicional: se role=MERCHANT_OWNER → companyName e cnpj obrigatórios; se CUSTOMER → ignora; se STAFF → falha com message `INVALID_ROLE`.

Ler antes:
- `specs/user-service/spec.md` §3 (Registrar Usuário) inteiro
- `specs/user-service/plan.md` §"MERCHANT_OWNER registration" linha 24
- `tasks/dev2/task-08-entities-and-repositories.md` (User, Merchant, UserRepository, MerchantRepository)
- `tasks/dev2/task-09-cnpj-validator.md` (@Cnpj annotation)
- `tasks/dev2/task-12-user-event-producer.md` (UserEventProducer + UserRegisteredEvent)
- `tasks/dev2/updated-prd.md` §5 (CE-REG-001..007)

## Target Files
**Create:**
- `services/user-service/src/main/java/com/acaboumony/user/dto/request/RegisterRequest.java`
- `services/user-service/src/main/java/com/acaboumony/user/dto/response/RegisterResponse.java`
- `services/user-service/src/main/java/com/acaboumony/user/validation/ValidRegisterRequest.java` (annotation class-level)
- `services/user-service/src/main/java/com/acaboumony/user/validation/RegisterRequestValidator.java` (ConstraintValidator)
- `services/user-service/src/main/java/com/acaboumony/user/service/MerchantService.java`
- `services/user-service/src/main/java/com/acaboumony/user/service/AuthService.java` (apenas método `register` nesta task — outros métodos em tasks 15, 17, 18)
- `services/user-service/src/main/java/com/acaboumony/user/exception/EmailAlreadyExistsException.java`
- `services/user-service/src/main/java/com/acaboumony/user/exception/CnpjAlreadyRegisteredException.java`
- `services/user-service/src/main/java/com/acaboumony/user/exception/InvalidRoleException.java`
- `services/user-service/src/main/java/com/acaboumony/user/exception/MissingMerchantDataException.java`
- `services/user-service/src/main/java/com/acaboumony/user/exception/InvalidCnpjException.java`
- `services/user-service/src/test/java/com/acaboumony/user/validation/RegisterRequestValidatorTest.java`
- `services/user-service/src/test/java/com/acaboumony/user/service/AuthServiceRegisterIT.java`

## Dependencies
- Depends on: task-08, task-09, task-12, task-13 (não estrita, mas Redis vai ser usado para email_confirm token)
- Blocks: task-15, task-20

## TDD Mode

### RED
**`RegisterRequestValidatorTest`** (unitário):
- `deve_aceitar_quando_role_CUSTOMER_sem_companyName_e_cnpj()`.
- `deve_aceitar_quando_role_MERCHANT_OWNER_com_companyName_e_cnpj_validos()`.
- `deve_rejeitar_quando_role_MERCHANT_OWNER_sem_cnpj()` → message `MISSING_MERCHANT_DATA` (**CE-REG-002**).
- `deve_rejeitar_quando_role_MERCHANT_OWNER_sem_companyName()`.
- `deve_rejeitar_quando_role_STAFF()` → message `INVALID_ROLE` (**CE-REG-006**).

**`AuthServiceRegisterIT extends BaseIntegrationTest`** (precisa de PostgreSQL + Kafka):
- `deve_criar_user_CUSTOMER_com_status_PENDING_quando_register_customer()` — assertar `User.status == PENDING_EMAIL_CONFIRMATION`, `merchant_id == null`, password hasheada (não plaintext), evento `user.registered` publicado no Kafka.
- `deve_criar_user_e_merchant_atomicamente_quando_register_MERCHANT_OWNER()` — user.merchant_id == merchant.id, merchant.owner_id == user.id, ambos persistidos.
- `deve_rejeitar_quando_email_duplicado()` → `EmailAlreadyExistsException` HTTP-status-able 409 (**CE-REG-001**).
- `deve_rejeitar_quando_cnpj_ja_cadastrado()` → `CnpjAlreadyRegisteredException` (**CE-REG-005**).
- `deve_fazer_rollback_user_quando_falha_ao_criar_merchant()` (**CE-REG-007**) — simular falha em `MerchantService.createAtomically()` (ex: mock que lança após User salvo); asserta que `userRepository.findByEmail(...)` retorna empty.
- `deve_publicar_evento_user_registered_com_merchantId_quando_MERCHANT_OWNER()`.
- `deve_publicar_evento_user_registered_com_merchantId_null_quando_CUSTOMER()`.

Roda → falha.

### GREEN
1. **`RegisterRequest` Record** — campos do spec.md §3.1:
   ```java
   @ValidRegisterRequest
   public record RegisterRequest(
       @NotBlank @Email @Size(max = 255) String email,
       @NotBlank @Size(min = 8, max = 100) String password,
       @NotBlank @Size(min = 2, max = 100) String fullName,
       @NotNull UserRole role,
       @Size(max = 100) String companyName,
       @Cnpj String cnpj
   ) {}
   ```
   - **Importante:** o `@Cnpj` aceita null (validador feito assim em task-09). A obrigatoriedade condicional é do `@ValidRegisterRequest`.
2. **`@ValidRegisterRequest`** class-level — `@Target(TYPE)`, `@Constraint(validatedBy = RegisterRequestValidator.class)`.
3. **`RegisterRequestValidator`** — implementa `ConstraintValidator<ValidRegisterRequest, RegisterRequest>`:
   - Se `role == STAFF` → add violation com message `INVALID_ROLE` no path `role`.
   - Se `role == MERCHANT_OWNER`: se `companyName` blank ou `cnpj` blank → violation com message `MISSING_MERCHANT_DATA`.
   - Se `role == CUSTOMER` → OK independente de companyName/cnpj.
4. **`RegisterResponse` Record** — `(UUID userId, String email, String role, UUID merchantId, boolean emailConfirmed)`.
5. **`MerchantService`** — `@Service`, `@Transactional` mandatório (delega à transação do caller).
   - `createMerchant(User owner, String companyName, String cnpj): Merchant` — verifica `existsByCnpj`; lança `CnpjAlreadyRegisteredException`; salva merchant; retorna.
6. **`AuthService`** — `@Service`. Apenas método `register(RegisterRequest req): RegisterResponse` nesta task.
   ```java
   @Transactional
   public RegisterResponse register(RegisterRequest req) {
       if (userRepository.existsByEmail(req.email())) throw new EmailAlreadyExistsException();
       User user = User.builder()
           .email(req.email())
           .passwordHash(bcrypt.encode(req.password()))
           .fullName(req.fullName())
           .role(req.role())
           .status(UserStatus.PENDING_EMAIL_CONFIRMATION)
           .build();
       user = userRepository.save(user);
       UUID merchantId = null;
       if (req.role() == UserRole.MERCHANT_OWNER) {
           Merchant merchant = merchantService.createMerchant(user, req.companyName(), req.cnpj());
           user.setMerchant(merchant);
           userRepository.save(user);
           merchantId = merchant.getId();
       }
       // gerar token confirmação no Redis (TTL 24h)
       String confirmToken = UUID.randomUUID().toString();
       stringRedisTemplate.opsForValue().set("email_confirm:" + confirmToken, user.getId().toString(), Duration.ofHours(24));
       // publicar evento
       userEventProducer.publishUserRegistered(user.getId(), user.getEmail(), user.getRole(), merchantId);
       return new RegisterResponse(user.getId(), user.getEmail(), user.getRole().name(), merchantId, false);
   }
   ```
7. **Exceptions** — todas com campo `errorCode` (`EMAIL_ALREADY_EXISTS`, `CNPJ_ALREADY_REGISTERED`, `INVALID_ROLE`, `MISSING_MERCHANT_DATA`, `INVALID_CNPJ`); estendem uma base `UserServiceException(String errorCode, String message)` para o `GlobalExceptionHandler` (task-20) tratar uniformemente.

### REFACTOR
- Extrair geração de token de confirmação para um `EmailConfirmationTokenService` se ficar grande — não obrigatório agora.
- Garantir que `password` nunca aparece em logs nem em `RegisterRequest.toString()` — Records geram toString automático. **Override `toString()` no record para omitir `password` e `cnpj`** (ou usar `@JsonIgnore` + `Slf4j` filter). Adicionar teste: `toString().contains("password") == false`.

## Acceptance Criteria
- [ ] `RegisterRequestValidatorTest` passa (5 testes), cobrindo CE-REG-002 e CE-REG-006
- [ ] `AuthServiceRegisterIT` passa (7+ testes), cobrindo CE-REG-001, CE-REG-005, CE-REG-007
- [ ] `RegisterRequest` é um Record Java 21 com `@ValidRegisterRequest`
- [ ] `RegisterRequest.toString()` NÃO contém `password` nem `cnpj` (regex test)
- [ ] Senha persistida via BCrypt rounds=12 (verificar `passwordHash.startsWith("$2a$12$")` ou similar)
- [ ] MERCHANT_OWNER cria User+Merchant na mesma transação (CE-REG-007 valida rollback)
- [ ] Evento `user.registered` publicado em `user-events` com `merchantId` (não-null para MERCHANT_OWNER, null para CUSTOMER)
- [ ] Token de confirmação UUID gravado em `email_confirm:{token}` com TTL 24h
- [ ] Cobertura JaCoCo dos pacotes alterados ≥ 90%
