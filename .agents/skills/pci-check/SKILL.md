---
name: pci-check
description: "PCI DSS + LGPD Security Review: varre o código modificado em busca de dados sensíveis em logs, tokens expostos, JWT mal configurado e violações de compliance. Gera relatório sem bloquear o fluxo. Execute antes de criar um PR."
argument-hint: "[caminho de arquivo ou diretório específico — vazio varre o diff da branch atual]"
---

# PCI DSS + LGPD Security Review

Você está auditando o código para compliance com PCI DSS Level 1 e LGPD. Esta revisão gera um relatório — não bloqueia o fluxo. Siga os passos em ordem.

## Input

$ARGUMENTS

## Step 0: Definir escopo e output

### Step 0a: Resolver escopo

Se `$ARGUMENTS` estiver vazio:
- Escopo = diff da branch atual contra `origin/main`
- Execute: `git diff origin/main...HEAD --name-only` para listar arquivos modificados

Se `$ARGUMENTS` for um path:
- Escopo = arquivos no path especificado (recursivo)

### Step 0b: Resolver output path

Execute via PowerShell:
```powershell
$branch = git rev-parse --abbrev-ref HEAD 2>$null
if (-not $branch -or $branch -eq "HEAD") { $branch = "no-branch" }
$sanitized = $branch -replace '/', '-' -replace '[^A-Za-z0-9._-]', '-' -replace '^-+|-+$', ''
"REPORT_PATH=qa-output/$sanitized/pci-report.md"
```

Garanta que o diretório existe: `New-Item -ItemType Directory -Force -Path (Split-Path "$REPORT_PATH")`

## Step 1: Coletar arquivos para análise

Liste os arquivos no escopo, filtrando apenas código relevante:
- `*.java` — código principal
- `*.yml`, `*.yaml`, `*.properties` — configurações
- `*.sql` — migrações Flyway
- `*.html`, `*.ftl` — templates de email

Exclua: `*Test*.java`, `*test*.java`, arquivos em `target/`, arquivos em `node_modules/`.

Para o modo diff: leia apenas as linhas adicionadas (`+`) do `git diff origin/main...HEAD -- <arquivo>`.

## Step 2: Varredura — Dados Sensíveis em Logs

Para cada arquivo `.java` no escopo, busque padrões de log que possam expor dados sensíveis:

### Padrões PROIBIDOS em logs (severity: CRÍTICO)

```
# Número de cartão em qualquer formato
log.*\d{13,19}
log.*cardNumber
log.*card_number
log.*pan

# CVV/CVC
log.*cvv
log.*cvc
log.*securityCode

# CPF
log.*cpf
log.*\d{3}\.?\d{3}\.?\d{3}-?\d{2}

# Senha
log.*password
log.*senha
log.*passwd

# Token completo (mais de 30 chars em um campo token)
log.*token.*[A-Za-z0-9+/]{30,}

# Chave privada / secret
log.*secret
log.*privateKey
log.*private_key
log.*apiKey
log.*api_key
```

### Padrões SUSPEITOS (severity: AVISO)

```
# Email completo (LGPD — depende do contexto)
log.*email.*@

# Nome completo junto com ID
log.*nome.*userId
log.*name.*customerId

# Endereço completo
log.*endereco
log.*address.*cep
```

**Exceção permitida:** últimos 4 dígitos do cartão podem ser logados — `\*{12}\d{4}` ou `lastFourDigits`.

## Step 3: Varredura — Armazenamento de dados sensíveis

### Step 3a: Entidades JPA e schemas SQL

Para arquivos `*.java` de entidades e `*.sql` de migrações:

Padrões PROIBIDOS (severity: CRÍTICO):
```
# Número completo de cartão em coluna de banco
@Column.*cardNumber(?!.*last)  # exceto lastFourDigits
card_number VARCHAR
pan VARCHAR

# CVV armazenado
@Column.*cvv
cvv VARCHAR

# Senha em plaintext
@Column.*password(?!.*Hash|.*Encoded|.*Bcrypt)
password VARCHAR(?!.*constraint|.*NOT NULL)
```

Padrão OBRIGATÓRIO para senhas:
- Colunas de senha devem ter nome `passwordHash` ou `passwordEncoded`
- Nunca `password` puro

### Step 3b: Transferência em DTOs/Records

Para Records Java (DTOs):

Padrões SUSPEITOS (severity: AVISO):
```
# DTO retornando campo que não deveria sair na response
record.*Response.*password
record.*Response.*cvv
record.*Response.*cardNumber(?!.*last)
```

## Step 4: Varredura — JWT e Autenticação

Para arquivos de configuração JWT e classes de serviço de auth:

### Verificações obrigatórias (severity: CRÍTICO se ausente)

1. **Expiração do JWT definida:**
   - Busque por `expiration`, `expiresIn`, `setExpiration`, `exp`
   - Se não encontrado nos arquivos de config JWT: `CRÍTICO: expiração de JWT não configurada`
   - Valor esperado: ≤ 15 minutos (900 segundos) para access token

2. **Algoritmo de assinatura seguro:**
   - Busque por `HS256`, `HS384`, `HS512`, `RS256`, `RS384`, `RS512`, `ES256`
   - Se encontrar `NONE` ou ausência de algoritmo: `CRÍTICO: JWT sem assinatura`
   - Algoritmos aceitos: RS256, RS384, RS512, ES256 (assimétricos preferidos)
   - `HS*` aceito apenas com chave ≥ 256 bits

3. **Secret não hardcoded:**
   - Busque por `jwt.secret.*=.*[A-Za-z0-9]{20,}` em arquivos `.yml`/`.properties` fora de `.env.example`
   - Se secret estiver hardcoded fora de variável de ambiente: `CRÍTICO: secret JWT hardcoded`

### Verificações recomendadas (severity: AVISO se ausente)

4. Refresh token com rotação implementada
5. Blacklist/revogação de tokens implementada

## Step 5: Varredura — SQL Injection

Para arquivos com queries SQL:

### Padrões PROIBIDOS (severity: CRÍTICO)

```java
// Concatenação de string em query
"SELECT.*" + variable
"WHERE.*" + userInput
entityManager.createNativeQuery("SELECT.*" + 

// String.format em queries
String.format("SELECT.*%s", userInput)
```

### Padrões CORRETOS (esperado)

```java
// Prepared statements / JPA params
@Query("SELECT u FROM User u WHERE u.email = :email")
findByEmail(@Param("email") String email)

// Criteria API
criteriaBuilder.equal(root.get("field"), value)
```

## Step 6: Varredura — TLS e Comunicação

Para arquivos de configuração (`*.yml`, `*.properties`):

### Verificações (severity: CRÍTICO se violado)

1. Conexões com banco sem SSL em produção:
   - Busque por `spring.datasource.url` sem `sslmode=require` ou `ssl=true`
   - Exceção: perfil `test` ou `local`

2. Kafka sem TLS em produção:
   - Busque por `security.protocol=PLAINTEXT` fora de perfil de teste

3. Mercado Pago / Stripe — chaves de produção em config:
   - Busque por `ACCESS_TOKEN=APP_USR-` (padrão de key real do MP) fora de `.env.example`
   - Se encontrado: `CRÍTICO: credencial de produção hardcoded`

## Step 7: Varredura — LGPD

Para toda a base de código no escopo:

### Verificações LGPD (severity: AVISO — requer revisão humana)

1. **Dados pessoais sem contexto de consentimento:**
   - Presença de campos `cpf`, `phone`, `email`, `birthDate`, `address` em entidades sem documentação de finalidade
   - Não é um bug automático — gera aviso para revisão humana

2. **Retenção de dados:**
   - Entidades com dados pessoais sem campo `deletedAt` (soft delete) ou sem mecanismo de anonimização
   - Gera aviso

3. **Logs com dados de identificação:**
   - `log.*userId.*email` juntos no mesmo statement — combinação pode ser desnecessária
   - Gera aviso

## Step 8: Compilar relatório

Salve em `$REPORT_PATH`:

```markdown
# PCI DSS + LGPD Security Report

**Data:** [timestamp]
**Branch:** [branch]
**Escopo:** [arquivos analisados / diff]
**Arquivos verificados:** N

---

## Resumo

| Severidade | Quantidade |
|------------|-----------|
| CRÍTICO | N |
| AVISO | N |
| OK | N |

---

## Findings CRÍTICOS

> Estes itens devem ser corrigidos antes do merge.

### [CRÍTICO-001] [Título]
- **Arquivo:** `path/to/file.java:linha`
- **Regra violada:** [PCI DSS 3.4 / LGPD Art. 46 / etc.]
- **Evidência:** `[trecho de código]`
- **Correção sugerida:** [como corrigir]

---

## Avisos

> Requerem revisão humana — podem ser falsos positivos dependendo do contexto.

### [AVISO-001] [Título]
- **Arquivo:** `path/to/file.java:linha`
- **Contexto:** [por que é suspeito]
- **Ação:** [revisar e documentar decisão se intencional]

---

## Itens verificados sem problemas

- [ ] Dados sensíveis em logs: OK
- [ ] Armazenamento de dados sensíveis: OK
- [ ] JWT configuração: OK
- [ ] SQL injection: OK
- [ ] TLS/comunicação: OK
- [ ] LGPD básico: OK

---

## Referências
- PCI DSS v4.0 Requirements: 3.3, 3.4, 6.2, 8.3
- LGPD: Art. 6, Art. 46, Art. 15
- OWASP Top 10: A02 (Cryptographic Failures), A03 (Injection)
```

## Step 9: Apresentar resultado ao usuário

```
## PCI Check Completo

**Críticos:** [N — lista resumida ou "Nenhum ✓"]
**Avisos:** [N]

**Relatório:** $REPORT_PATH
```

Se houver findings CRÍTICOS:
```
⚠ ATENÇÃO: [N] findings críticos encontrados.
Corrija antes de criar o PR. Ver: $REPORT_PATH
```

Se não houver findings:
```
✓ Nenhum finding crítico. Código dentro dos padrões PCI DSS e LGPD.
```

## Regras

- Esta revisão é **somente leitura** — nunca modifica código
- Falsos positivos são esperados — o relatório é ponto de partida para revisão humana
- Nunca apagar o relatório de uma branch sem o usuário saber
- Varredura de padrões é estática — não substitui pentest real
- Findings em arquivos de teste (`*Test.java`) têm severidade rebaixada para AVISO
