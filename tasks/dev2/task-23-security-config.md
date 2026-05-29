# Task 23: SecurityConfig — SecurityFilterChain (Spring Security 6)

## Objective
Configurar `SecurityFilterChain` para Spring Security 6: rotas públicas (`/auth/register`, `/auth/confirm-email`, `/auth/login`, `/auth/refresh`, `/auth/2fa/verify`, `/auth/2fa/recovery`, `/actuator/health`, `/api-docs/**`, `/swagger-ui/**`), rotas internas (`/internal/**` protegidas por `InternalSecretFilter`), e rotas autenticadas (`/users/**`, `/auth/logout`, `/auth/2fa/setup`, `/auth/2fa/confirm`, `/auth/2fa/disable`) que validam JWT via custom converter.

## Context
**Quick Context:**
- Spring Security 6 mudou API: `httpSecurity.authorizeHttpRequests(auth -> auth...)`, `.csrf(csrf -> csrf.disable())`, etc. Usar lambdas.
- JWT resource server: usar `oauth2ResourceServer.jwt()` com `JwtDecoder` custom que delega para `JwtTokenValidator` (task-11). Alternativa simpler: custom filter `JwtAuthenticationFilter`.
- `InternalSecretFilter` (task-19) registrado como `OncePerRequestFilter` — adicionar ao chain antes do JWT filter.
- CORS: por enquanto allow `*` em dev; restrição é responsabilidade do api-gateway. Documentar.
- CSRF disabled (stateless API).
- Sessões: `STATELESS`.
- 401 / 403 responses: usar `AuthenticationEntryPoint` e `AccessDeniedHandler` custom para retornar RFC 7807 Problem Details (mesma forma que `GlobalExceptionHandler`).

Ler antes:
- `specs/user-service/spec.md` §2 (matriz pública/autenticado/interno)
- `specs/user-service/plan.md` §"Variáveis de Ambiente" e linha 26 (X-Internal-Secret)
- `tasks/dev2/task-11` (JwtTokenValidator)
- `tasks/dev2/task-19` (InternalSecretFilter)
- Docs: Spring Security 6 + JWT resource server (https://docs.spring.io/spring-security/reference/6.2/servlet/oauth2/resource-server/jwt.html)

## Target Files
**Create:**
- `services/user-service/src/main/java/com/acaboumony/user/config/SecurityConfig.java`
- `services/user-service/src/main/java/com/acaboumony/user/security/JwtAuthenticationFilter.java`
- `services/user-service/src/main/java/com/acaboumony/user/security/ProblemDetailsAuthenticationEntryPoint.java`
- `services/user-service/src/main/java/com/acaboumony/user/security/ProblemDetailsAccessDeniedHandler.java`
- `services/user-service/src/test/java/com/acaboumony/user/security/SecurityConfigIT.java`

## Dependencies
- Depends on: task-11, task-19, task-20, task-21, task-22
- Blocks: task-24

## TDD Mode

### RED
`SecurityConfigIT extends BaseIntegrationTest`:
- `deve_permitir_acesso_publico_a_auth_register()`.
- `deve_permitir_acesso_publico_a_auth_login()`.
- `deve_permitir_acesso_publico_a_auth_confirm_email()`.
- `deve_permitir_acesso_publico_a_auth_refresh()`.
- `deve_permitir_acesso_publico_a_auth_2fa_verify()`.
- `deve_permitir_acesso_publico_a_auth_2fa_recovery()`.
- `deve_permitir_acesso_publico_a_actuator_health()`.
- `deve_retornar_401_em_users_me_sem_JWT()`.
- `deve_permitir_acesso_a_users_me_com_JWT_valido()`.
- `deve_retornar_401_em_users_me_com_JWT_expirado()`.
- `deve_retornar_401_em_users_me_com_JWT_assinatura_invalida()`.
- `deve_retornar_403_em_internal_validate_token_sem_X_Internal_Secret()` (**CE-INTERNAL-001**).
- `deve_retornar_403_em_internal_validate_token_com_X_Internal_Secret_invalido()` (**CE-INTERNAL-002**).
- `deve_permitir_internal_validate_token_com_secret_valido_e_JWT_valido()`.
- `deve_retornar_RFC_7807_problem_details_quando_401()` — body Content-Type `application/problem+json`.
- `deve_retornar_RFC_7807_problem_details_quando_403()`.

Roda → falha.

### GREEN
1. **`SecurityConfig`** — `@Configuration @EnableWebSecurity`:
   ```java
   @Bean
   public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
       return http
           .csrf(CsrfConfigurer::disable)
           .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
           .authorizeHttpRequests(auth -> auth
               .requestMatchers(
                   "/api/v1/auth/register", "/api/v1/auth/confirm-email",
                   "/api/v1/auth/login", "/api/v1/auth/refresh",
                   "/api/v1/auth/2fa/verify", "/api/v1/auth/2fa/recovery",
                   "/api/v1/auth/resend-confirmation",
                   "/actuator/health", "/api-docs/**", "/swagger-ui/**"
               ).permitAll()
               .requestMatchers("/internal/**").permitAll() // InternalSecretFilter cuida
               .anyRequest().authenticated())
           .addFilterBefore(internalSecretFilter, UsernamePasswordAuthenticationFilter.class)
           .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
           .exceptionHandling(eh -> eh
               .authenticationEntryPoint(problemDetailsAuthEntryPoint)
               .accessDeniedHandler(problemDetailsAccessDeniedHandler))
           .build();
   }
   ```
2. **`JwtAuthenticationFilter`** — `extends OncePerRequestFilter`:
   - Skip se path começa com `/internal/` (já tem filter) ou se path é público.
   - Lê `Authorization: Bearer <token>`; se ausente em rota autenticada → deixa chain seguir (entry point lida com 401).
   - Valida via `JwtTokenValidator.validate(token)` → cria `Authentication` (ex: `JwtAuthenticationToken` com principal = userId UUID, authorities = `[ROLE_<UserRole>]`); seta no `SecurityContextHolder`.
   - Exceptions → deixa entry point lidar.
3. **`ProblemDetailsAuthenticationEntryPoint`** + **`ProblemDetailsAccessDeniedHandler`** — escrevem `application/problem+json` com `errorCode`, status, title.

### REFACTOR
- Considerar usar `oauth2ResourceServer.jwt()` com `NimbusJwtDecoder` para reduzir código custom — porém JJWT + RS256 com chave PEM customizada é mais consistente com o `JwtTokenValidator` (task-11). Manter custom filter por consistência.
- Cachear `Authentication` por request (default do Spring Security).

## Acceptance Criteria
- [ ] `SecurityConfigIT` passa (15+ testes)
- [ ] Todas as rotas públicas, internas e autenticadas comportam-se conforme spec §2
- [ ] `InternalSecretFilter` aplicado APENAS a `/internal/**`
- [ ] JWT inválido/expirado retorna 401 com RFC 7807
- [ ] Sem JWT em rota autenticada retorna 401 com RFC 7807
- [ ] Sem X-Internal-Secret em `/internal/**` retorna 403 com RFC 7807
- [ ] Session policy STATELESS, CSRF disabled
