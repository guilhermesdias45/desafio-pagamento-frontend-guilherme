# Sessão 2026-05-29 — Correção dos Testes do user-service

## Resultado final

```
Tests run: 65, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

Ponto de partida: 65 testes, **36 erros + 0 falhas**.  
Ponto de chegada: 65 testes, **0 erros + 0 falhas**.

---

## Problema 1: ApplicationContext não subia nos @WebMvcTest (36 erros)

### Causa raiz
`@WebMvcTest` carrega os beans da camada web, incluindo filtros com `@Component`. Dois filtros causavam falha:

**`InternalSecretFilter`**: é um `@Component` que requer `InternalSecretProperties` no construtor. Em `@WebMvcTest`, o `JwtConfig` (que registra as `@ConfigurationProperties`) **não** é carregado — só o `SecurityConfig` é carregado (por ter `@EnableWebSecurity`). Sem `InternalSecretProperties` disponível, o contexto falhava.

**`JwtAuthenticationFilter`**: é um `@Component` que requer `JwtTokenValidator` no construtor. `JwtTokenValidator` tem `@PostConstruct init()` que parseia uma chave RSA. Em ambiente de teste sem chaves RSA reais, esse `init()` explodia.

### Fix inicial (provisório)
Nos 3 arquivos de controller test, adicionamos mocks para fazer o contexto subir:

```java
@MockBean InternalSecretFilter internalSecretFilter;
@MockBean JwtTokenValidator jwtTokenValidator;
```

**Resultado:** 36 erros → 3 erros (apenas `ConfigurationPropertiesTest`)

---

## Problema 2: ConfigurationPropertiesTest ainda falhava (3 erros)

### Causa raiz
`ConfigurationPropertiesTest` usava `@SpringBootTest(classes = UserServiceApplication.class)` — carregava o contexto **inteiro**. Com JPA excluído via `spring.autoconfigure.exclude`, o `UserRepository` não conseguia ser criado → quebrava `AuthService → AuthController`, etc.

### Fix aplicado
```java
// Antes:
@SpringBootTest(classes = UserServiceApplication.class)

// Depois:
@SpringBootTest(classes = JwtConfig.class)
```

**Por quê:** `JwtConfig` é exatamente a classe `@Configuration` que habilita todos os `@ConfigurationProperties` (`JwtProperties`, `TotpProperties`, `InternalSecretProperties`, `SecurityLoginProperties`). Carregando **apenas** ela, o contexto sobe com as properties sem precisar de controllers, services, repositórios ou infraestrutura. Os `@MockBean` de `JwtTokenProvider` e `JwtTokenValidator` foram removidos — esses beans nem existem nesse contexto mínimo.

**Resultado:** 3 erros → 0 erros de `ApplicationContext`

---

## Problema 3: 30 falhas — todos os testes de controller retornando HTTP 200 com body vazio

### Causa raiz (a mais sutil e importante)

Após os contextos subirem, todos os 33 testes de controller rodavam mas retornavam **HTTP 200 com body vazio** — independente do que o controller fizesse. Um controller que retornava `ResponseEntity.status(201)` recebia 200. Um que retornava `ResponseEntity.noContent()` recebia 200. Exceções nunca chegavam ao `GlobalExceptionHandler`.

Diagnóstico via Surefire report:
```
Caused by: java.lang.IllegalArgumentException: json can not be null or empty
```

O body era literalmente vazio porque **a requisição nunca chegava ao controller**.

**Raiz do problema:** `InternalSecretFilter` estava mockado com `@MockBean`. Mockito, para métodos `void`, não faz nada por padrão. O método herdado `doFilter(request, response, chain)` da `OncePerRequestFilter` nunca era chamado com a implementação real, então **`chain.doFilter()` nunca era invocado**. A filter chain parava ali. O `HttpServletResponse` ficava com status 200 (default) e body vazio.

### Investigação adicional que revelou mais uma causa

`SecurityConfig` (`@EnableWebSecurity @Configuration`) **é carregado pelo `@WebMvcTest`** — faz parte do scan de segurança da camada web. Ele injeta `InternalSecretFilter` na `SecurityFilterChain`. Com o filter mockado dentro da chain, nenhuma requisição de teste chegava ao `DispatcherServlet`.

Além disso, os controllers usavam:
```java
@AuthenticationPrincipal JwtAuthenticationToken jwt
```

Mas `JwtAuthenticationToken.getPrincipal()` retorna `JwtClaims`, não o token em si. O `AuthenticationPrincipalArgumentResolver` do Spring Security detecta incompatibilidade de tipo e retorna `null`. O controller chamava `jwt.getName()` → `NullPointerException`.

### Fix aplicado — três mudanças em conjunto

#### 1. Criar `TestSecurityConfig` em `src/test/`

```java
@TestConfiguration
@EnableWebSecurity
public class TestSecurityConfig {
    @Bean
    public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .build();
    }
}
```

**Por quê:** Substitui a `SecurityFilterChain` restritiva do `SecurityConfig` por uma permissiva. `@EnableWebSecurity` é necessário porque `SecurityAutoConfiguration` está excluído nos testes.

#### 2. Atualizar os 3 controller tests

```java
@WebMvcTest(
    controllers = AuthController.class,
    excludeAutoConfiguration = SecurityAutoConfiguration.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = SecurityConfig.class
    )
)
@Import({GlobalExceptionHandler.class, TestSecurityConfig.class})
class AuthControllerTest {
    @MockBean AuthService authService;
    @MockBean InternalSecretProperties internalSecretProperties; // era @MockBean InternalSecretFilter
    @MockBean JwtTokenValidator jwtTokenValidator;
}
```

- `excludeFilters = SecurityConfig.class` → impede `SecurityConfig` de criar a chain restritiva
- `@Import(TestSecurityConfig.class)` → usa a chain permissiva de teste
- `@MockBean InternalSecretProperties` em vez de `@MockBean InternalSecretFilter` → o filter **real** é carregado com dependência mockada; para rotas não-`/internal/`, `doFilterInternal()` chama `chain.doFilter()` sem jamais acessar `internalSecretProperties`

#### 3. Corrigir controllers — `@AuthenticationPrincipal`

```java
// Antes (errado — getPrincipal() não retorna JwtAuthenticationToken):
@AuthenticationPrincipal JwtAuthenticationToken jwt
UUID userId = UUID.fromString(jwt.getName());

// Depois (correto — getPrincipal() retorna JwtClaims):
@AuthenticationPrincipal JwtClaims claims
UUID userId = claims.sub();
```

Aplicado em: `AuthController.logout()`, `TwoFactorController.setup/confirm/disable()`, `UserController.me/updateMe()`.

**Por quê:** O principal de autenticação **é** `JwtClaims` (a identidade do usuário). `JwtAuthenticationToken` é o wrapper Spring Security. `getPrincipal()` retorna a identidade — convenção correta do Spring Security.

**Resultado:** 30 falhas → 2 falhas

---

## Problema 4: 2 falhas — errorCode errado para validações de negócio

### Causa raiz

Dois testes falhavam com:
```
JSON path "$.errorCode" expected:<INVALID_ROLE> but was:<VALIDATION_FAILED>
JSON path "$.errorCode" expected:<MISSING_MERCHANT_DATA> but was:<VALIDATION_FAILED>
```

O `RegisterRequestValidator` (class-level `@ValidRegisterRequest`) já colocava os códigos corretos (`INVALID_ROLE`, `MISSING_MERCHANT_DATA`) nas mensagens das constraint violations. Mas `GlobalExceptionHandler.handleValidation()` sempre sobrescrevia o `errorCode` com `VALIDATION_FAILED`, ignorando esse contexto de negócio.

### Fix aplicado

Adicionado `resolveValidationErrorCode()` em `GlobalExceptionHandler`:

```java
private String resolveValidationErrorCode(BindingResult bindingResult) {
    List<String> distinct = bindingResult.getAllErrors().stream()
            .map(ObjectError::getDefaultMessage)
            .filter(m -> m != null && m.matches("[A-Z][A-Z0-9_]+"))
            .distinct()
            .toList();
    return distinct.size() == 1 ? distinct.get(0) : "VALIDATION_FAILED";
}
```

**Lógica:** Se todas as violations têm o mesmo código no padrão `ALL_CAPS_WITH_UNDERSCORES`, é um código de negócio específico — promove ao `errorCode` top-level. Caso contrário (mensagens genéricas misturadas), usa `VALIDATION_FAILED`.

| Cenário | Violations | `errorCode` |
|---|---|---|
| `STAFF` role | `["INVALID_ROLE"]` | `INVALID_ROLE` |
| `MERCHANT_OWNER` sem cnpj | `["MISSING_MERCHANT_DATA", "MISSING_MERCHANT_DATA"]` | `MISSING_MERCHANT_DATA` |
| email em branco | `["must not be blank", "must be well-formed email"]` | `VALIDATION_FAILED` |

**Resultado:** 2 falhas → **0 falhas — BUILD SUCCESS**

---

## Resumo completo das mudanças

### Arquivos de teste modificados
| Arquivo | O que mudou |
|---|---|
| `ConfigurationPropertiesTest.java` | `@SpringBootTest(UserServiceApplication)` → `@SpringBootTest(JwtConfig.class)`; removidos mocks desnecessários |
| `AuthControllerTest.java` | `excludeFilters` SecurityConfig; `@Import` TestSecurityConfig; `@MockBean InternalSecretProperties` em vez de InternalSecretFilter |
| `TwoFactorControllerTest.java` | idem |
| `UserControllerTest.java` | idem |

### Arquivos de produção modificados
| Arquivo | O que mudou |
|---|---|
| `AuthController.java` | `@AuthenticationPrincipal JwtAuthenticationToken jwt` → `JwtClaims claims`; `jwt.getName()` → `claims.sub()` |
| `TwoFactorController.java` | idem em setup, confirm, disable; removido import UUID |
| `UserController.java` | idem em me, updateMe; removido import UUID |
| `GlobalExceptionHandler.java` | `resolveValidationErrorCode()` para promover códigos de negócio ao errorCode top-level |

### Arquivo novo
| Arquivo | O que é |
|---|---|
| `src/test/.../config/TestSecurityConfig.java` | `@TestConfiguration @EnableWebSecurity` permit-all para slices `@WebMvcTest` |

---

## Lições para sessões futuras

1. **Nunca mockar filtros `@Component` em `@WebMvcTest`** — Mockito não chama `chain.doFilter()` em mocks de `void`, travando toda a filter chain.
2. **Mockar a dependência do filtro**, não o filtro em si — o filter real funciona corretamente.
3. **Sempre excluir o `SecurityConfig` principal** em `@WebMvcTest` e importar um `TestSecurityConfig` permit-all.
4. **`@AuthenticationPrincipal` deve corresponder ao tipo de `getPrincipal()`** — verificar o que o token retorna antes de anotar o parâmetro.
5. **`@SpringBootTest` para testes de config properties** deve carregar só a classe `@Configuration` que registra as properties, nunca o `Application.class` inteiro.
