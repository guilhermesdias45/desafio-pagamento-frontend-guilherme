---
name: api-qa
description: "API QA Pipeline: testa endpoints REST, eventos Kafka, idempotência e rate limiting sem browser. Gera testes de integração JUnit 5 + Testcontainers para regressão. Use no lugar de /qa para este projeto backend."
argument-hint: "[--fresh] [serviço ou endpoint a testar — vazio testa todos os serviços rodando]"
---

# API QA Pipeline

Você está orquestrando a validação de qualidade das APIs do projeto. Sem browser — apenas HTTP, Kafka e banco. Siga os passos em ordem.

## Input

$ARGUMENTS

## Step 0: Preparar ambiente

### Step 0a: Resolver QA_OUTPUT_DIR

Execute via PowerShell:

```powershell
$branch = git rev-parse --abbrev-ref HEAD 2>$null
if (-not $branch -or $branch -eq "HEAD") {
  $sha = git rev-parse --short HEAD 2>$null
  $branch = if ($sha) { "detached-$sha" } else { "no-git" }
}
$sanitized = $branch -replace '/', '-' -replace '[^A-Za-z0-9._-]', '-' -replace '^-+|-+$', ''
"QA_OUTPUT_DIR=qa-output/$sanitized"
```

Limpe artefatos anteriores: `Remove-Item -Recurse -Force "$QA_OUTPUT_DIR" -ErrorAction SilentlyContinue`
Crie o diretório: `New-Item -ItemType Directory -Force -Path "$QA_OUTPUT_DIR"`

### Step 0b: Parse flags

Se `$ARGUMENTS` começa com `--fresh`, defina `FRESH=true` e remova o flag.
Escopo limpo → `SCOPE` (vazio = todos os serviços).

### Step 0c: Descobrir serviços rodando

Execute: `docker-compose ps --format json` ou `docker-compose ps`

Para cada serviço com status `running`:
- Extraia nome, porta mapeada e healthcheck
- Derive a URL base: `http://localhost:<porta>`

Serviços esperados e suas portas (do docker-compose.yml):
- `user-service` → porta padrão Spring Boot
- `api-gateway` → porta do gateway
- `payment-service`
- `fraud-service`
- `order-service`
- `notification-service`

Se nenhum serviço estiver rodando, informe o usuário e encerre: "Suba os serviços com `docker-compose up -d` e tente novamente."

Filtre por `$SCOPE` se informado.

## Step 1: Descoberta de endpoints

Para cada serviço rodando, descubra os endpoints disponíveis:

1. Tente `GET <base-url>/actuator` — se habilitado, lista endpoints
2. Tente `GET <base-url>/v3/api-docs` — OpenAPI spec
3. Se nenhum funcionar, leia `services/<nome>/src/main/java/**/controller/*.java` para mapear os `@RequestMapping` e `@GetMapping/@PostMapping`

Compile um mapa de endpoints: `SERVICE → [METHOD PATH, ...]`

Salve em `$QA_OUTPUT_DIR/endpoints-discovered.md`.

## Step 2: Testes por categoria

Execute cada categoria em ordem. Para cada teste, registre: `✓ PASS | ✗ FAIL | ⚠ WARN` com detalhes.

### 2a: Health checks

Para cada serviço:
```
GET <base-url>/actuator/health
GET <base-url>/actuator/info
```
Espera: HTTP 200, `{"status":"UP"}`

### 2b: Contrato de API — Happy path

Para cada endpoint descoberto, construa um request válido baseado na spec em `specs/<modulo>/`:
- Use os exemplos da **seção 8 (Exemplos Concretos)** de cada spec
- Execute o request
- Valide: HTTP status esperado, estrutura do response, campos obrigatórios presentes

Se a spec não existir, documente como `⚠ WARN: spec ausente, teste de contrato pulado`.

### 2c: Casos de erro documentados

Para cada spec encontrada em `specs/`, leia a **seção 5 (Pós-condições de Erro)** e **seção 7 (Casos Extremos)**:
- Construa requests que deveriam retornar erro
- Valide: HTTP status correto, `errorCode` correto, estrutura RFC 7807 (`type`, `title`, `status`, `detail`)

### 2d: Idempotência

Para endpoints que processam transações ou pedidos (identificados por `idempotencyKey` na spec):
1. Execute o mesmo request duas vezes com o mesmo `idempotencyKey`
2. Espera: segunda requisição retorna mesmo resultado sem criar duplicata
3. Valide no banco: `SELECT COUNT(*) WHERE idempotency_key = '<key>'` deve retornar 1

Execute via `docker-compose exec <db-service> psql -U <user> -d <db> -c "<query>"`.

### 2e: Rate limiting

Para endpoints com rate limiting documentado (seção 11 das specs):
1. Envie N+1 requests em sequência rápida (onde N é o limite configurado)
2. Espera: último request retorna HTTP 429 com `Retry-After` header
3. Aguarde o tempo de reset e confirme que o limite foi liberado

### 2f: Autenticação e autorização

Para endpoints protegidos (JWT obrigatório na spec):
1. Request sem token → espera HTTP 401
2. Request com token expirado → espera HTTP 401
3. Request com token de role incorreta → espera HTTP 403
4. Request com token válido → espera HTTP 2xx

Gere um token de teste via `POST /api/v1/auth/login` com credenciais de teste, ou use o secret do `.env.example` para assinar um token manualmente.

### 2g: Eventos Kafka (se Kafka rodando)

Para cada spec com eventos documentados na **seção 9**:

1. Execute o request que deveria emitir o evento
2. Consuma o tópico: `docker-compose exec kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic <topico> --from-beginning --max-messages 1 --timeout-ms 5000`
3. Valide: evento recebido com os campos esperados (tipo, payload, headers)

Tópicos esperados baseados no código existente:
- `transaction-events`, `fraud-events`, `order-events`, `user-events`, `notification-events`

## Step 3: Gerar testes de integração JUnit

Para cada falha encontrada nos Steps 2a-2g, ou para fluxos sem cobertura de teste existente, gere um teste de integração Java:

### Template base para cada teste gerado

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class <NomeDoFluxo>IntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("deve_<comportamento>_quando_<condição>")
    void deve_<comportamento>_quando_<condição>() {
        // GIVEN - setup baseado no exemplo da spec
        // WHEN - execute o request
        // THEN - valide response e efeitos colaterais
    }
}
```

Salve cada arquivo gerado em `services/<modulo>/src/test/java/com/acaboumony/<modulo>/integration/`.

Respeite as convenções do projeto:
- Nome do teste descreve comportamento, não método
- Testcontainers com PostgreSQL real (nunca H2)
- Cada teste é independente (sem estado compartilhado)

## Step 4: Validação de segurança básica

Verifique rapidamente:

1. **Headers de segurança presentes** em qualquer response:
   - `X-Content-Type-Options: nosniff`
   - `X-Frame-Options: DENY`
   - Ausência de `X-Powered-By` ou `Server` com versão exposta

2. **Dados sensíveis em responses** — scan dos responses coletados:
   - Nenhum campo com nome `cardNumber`, `cvv`, `cpf`, `password`, `token` deve aparecer em clear text
   - Se encontrado: `✗ FAIL — CRÍTICO: dado sensível exposto`

3. **Logs durante os testes** — verifique `docker-compose logs <servico>` após os testes:
   - Busque por padrões de cartão: `\d{13,19}` ou `cvv`
   - Se encontrado: `✗ FAIL — CRÍTICO: dado sensível em log`

## Step 5: Relatório final

Salve em `$QA_OUTPUT_DIR/qa-report.md`:

```markdown
# API QA Report

**Data:** [timestamp]
**Branch:** [branch atual]
**Serviços testados:** [lista]

## Resultados

| Categoria | Passou | Falhou | Avisos |
|-----------|--------|--------|--------|
| Health checks | N | N | N |
| Contrato de API | N | N | N |
| Casos de erro | N | N | N |
| Idempotência | N | N | N |
| Rate limiting | N | N | N |
| Autenticação/Autorização | N | N | N |
| Eventos Kafka | N | N | N |
| Segurança básica | N | N | N |
| **Total** | **N** | **N** | **N** |

## Falhas críticas
[Lista de falhas com severidade CRÍTICA — dados sensíveis, auth bypass, etc.]

## Falhas
[Lista completa de falhas com endpoint, request enviado e resposta recebida]

## Avisos
[Specs ausentes, endpoints sem cobertura, Kafka offline, etc.]

## Testes de regressão gerados
[Lista de arquivos .java criados]

## Próximos passos
[O que precisa ser corrigido antes do merge]
```

Apresente o resumo ao usuário:

```
## API QA Completo

**Resultados:** [N passed · N failed · N warnings]

**Críticos:** [lista ou "Nenhum"]

**Testes gerados:** [N arquivos em services/*/src/test/]

**Relatório completo:** $QA_OUTPUT_DIR/qa-report.md
```

## Regras

- Nunca modificar código de produção — apenas observar e testar
- Se um serviço não está rodando, pular seus testes e documentar como aviso
- Testes gerados devem compilar — verifique imports e dependências antes de salvar
- Prioridade: falhas de segurança > falhas de contrato > falhas de comportamento > avisos
- Kafka offline não é erro fatal — documentar como aviso e pular Step 2g
