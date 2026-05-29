# Task 22: UserController — GET /me + PATCH /me (fullName only)

## Objective
Implementar `UserController` com `GET /api/v1/users/me` (retorna perfil do usuário autenticado) e `PATCH /api/v1/users/me` (atualiza apenas `fullName`). Ambos requerem JWT.

## Context
**Quick Context:**
- Sprint 1: PATCH só `fullName`. Alterar email/senha é Sprint 2 (com fluxo de reconfirmação).
- `UserProfileResponse` Record: `userId`, `email`, `fullName`, `role`, `merchantId` (nullable), `twoFactorEnabled`, `createdAt`.
- UserService: `getProfile(UUID userId)`, `updateFullName(UUID userId, String fullName)`.

Ler antes:
- `specs/user-service/spec.md` §8 (PATCH /me — Sprint 1)
- `tasks/dev2/task-08` (User entity, UserRepository)
- `tasks/dev2/task-20` (padrão de Controller)

## Target Files
**Create:**
- `services/user-service/src/main/java/com/acaboumony/user/controller/UserController.java`
- `services/user-service/src/main/java/com/acaboumony/user/service/UserService.java`
- `services/user-service/src/main/java/com/acaboumony/user/dto/request/UpdateProfileRequest.java` (Record: `@NotBlank @Size(min=2,max=100) String fullName`)
- `services/user-service/src/main/java/com/acaboumony/user/dto/response/UserProfileResponse.java`
- `services/user-service/src/main/java/com/acaboumony/user/exception/UserNotFoundException.java`
- `services/user-service/src/test/java/com/acaboumony/user/controller/UserControllerTest.java`

## Dependencies
- Depends on: task-08, task-20 (GlobalExceptionHandler)
- Blocks: task-23, task-24

## TDD Mode

### RED
`UserControllerTest` (`@WebMvcTest(UserController.class)` + Mockito):
- `deve_retornar_200_com_perfil_quando_GET_me_com_JWT_valido()`.
- `deve_retornar_401_quando_GET_me_sem_JWT()`.
- `deve_retornar_perfil_com_merchantId_quando_user_e_MERCHANT_OWNER()`.
- `deve_retornar_perfil_com_merchantId_null_quando_user_e_CUSTOMER()`.
- `deve_retornar_200_e_atualizar_fullName_quando_PATCH_me_com_payload_valido()`.
- `deve_retornar_400_quando_PATCH_me_com_fullName_blank()`.
- `deve_retornar_400_quando_PATCH_me_com_fullName_acima_de_100_chars()`.
- `deve_ignorar_outros_campos_quando_PATCH_me_payload_inclui_email_ou_password()` — Sprint 1: payload só tem fullName, outros campos rejeitados pelo Spring (sem `@JsonProperty` para email/password no Record → ignorados ou erro de unknown property).

Roda → falha.

### GREEN
1. **`UserProfileResponse` Record** — `(UUID userId, String email, String fullName, String role, UUID merchantId, boolean twoFactorEnabled, Instant createdAt)`.
2. **`UpdateProfileRequest` Record** — `(@NotBlank @Size(min = 2, max = 100) String fullName)`.
3. **`UserService`**:
   ```java
   @Transactional(readOnly = true)
   public UserProfileResponse getProfile(UUID userId) { ... }

   @Transactional
   public UserProfileResponse updateFullName(UUID userId, String fullName) {
       User u = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
       u.setFullName(fullName);
       return toResponse(userRepository.save(u));
   }
   ```
4. **`UserController`**:
   ```java
   @RestController
   @RequestMapping("/api/v1/users")
   public class UserController {
       @GetMapping("/me")
       public UserProfileResponse me(@AuthenticationPrincipal JwtAuthenticationToken jwt) {
           return userService.getProfile(UUID.fromString(jwt.getName()));
       }

       @PatchMapping("/me")
       public UserProfileResponse updateMe(@AuthenticationPrincipal JwtAuthenticationToken jwt,
                                            @Valid @RequestBody UpdateProfileRequest req) {
           return userService.updateFullName(UUID.fromString(jwt.getName()), req.fullName());
       }
   }
   ```

### REFACTOR
- Considerar `UserMapper` (MapStruct) para `User → UserProfileResponse`. Sprint 1 pode hardcode; mapper é opcional.

## Acceptance Criteria
- [ ] `UserControllerTest` passa (8 testes)
- [ ] GET `/users/me` retorna 401 sem JWT
- [ ] PATCH `/users/me` aceita apenas `fullName`; outros campos rejeitados ou ignorados
- [ ] `UserProfileResponse` NÃO inclui `passwordHash` nem `totpSecretEncrypted`
- [ ] `MERCHANT_OWNER` retorna `merchantId` não-null; `CUSTOMER` retorna `merchantId` null
- [ ] Update persiste no DB (verificar com Mockito ou IT)
