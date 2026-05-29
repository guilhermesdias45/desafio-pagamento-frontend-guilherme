---
name: spec
description: "SDD Spec Writer: guia a criação de uma especificação completa em specs/<modulo>/spec.md seguindo o template obrigatório de 11 seções do projeto. Passo 1 do fluxo SDD+TDD — nenhum código sem spec aprovada."
argument-hint: "<nome do módulo ou feature> (ex: user-service/register, payment-service/processTransaction)"
---

# SDD Spec Writer

Você está orquestrando a escrita de uma especificação SDD. Siga os passos em ordem.

## Input

O módulo ou feature a especificar:

$ARGUMENTS

## Step 0: Verificar spec existente

1. Derive `SPEC_PATH` a partir de `$ARGUMENTS`:
   - Normaliza o argumento para lowercase com hífens → `specs/<modulo>/spec.md`
   - Ex: "user-service/register" → `specs/user-service/register.md`
   - Ex: "payment-service" → `specs/payment-service/spec.md`

2. Verifique se `$SPEC_PATH` já existe.
   - Se existir: leia o arquivo, informe o usuário e pergunte via `AskUserQuestion`:
     - **"Revisar spec existente"** — continue para Step 2 com o conteúdo atual
     - **"Reescrever do zero"** — continue para Step 1
     - **"Cancelar"** — encerre

## Step 1: Contexto e clareza da feature

### Step 1a: Verificar se a ideia está madura

Avalie `$ARGUMENTS`:
- Se for vago (menos de uma frase descritiva, sem comportamento concreto, sem critérios de sucesso) → ofereça via `AskUserQuestion`:
  - **"Refinar com /grill-me primeiro (Recomendado)"** — encerre limpo: "Execute `/grill-me <feature>` para amadurecer a ideia, depois volte com `/spec`."
  - **"Continuar assim mesmo"** — prossiga para Step 1b

### Step 1b: Explorar o codebase

Antes de perguntar ao usuário, explore:
- `specs/` — specs existentes do mesmo módulo (contexto, padrões, convenções)
- `services/<modulo>/src/main/java/` — código existente relacionado (entidades, enums, repositórios)
- `services/<modulo>/src/main/resources/application.yml` — configurações relevantes
- `docker-compose.yml` — dependências de infra (quais serviços externos envolvidos)

Use o que encontrar para preencher seções da spec sem precisar perguntar ao usuário.

### Step 1c: Perguntas de clareza (apenas o que o codebase não responde)

Faça **uma pergunta por vez** via `AskUserQuestion`. Para cada pergunta, proponha uma resposta recomendada baseada no que você encontrou no codebase e no CLAUDE.md do projeto.

Perguntas obrigatórias (se não respondidas pelo codebase):
1. Qual é a assinatura do endpoint/método? (HTTP method, path, tipos Java)
2. Quais são os casos de erro e seus códigos? (ex: USER_NOT_FOUND, CARD_DECLINED)
3. Há requisitos de idempotência? (relevante para transações e pedidos)
4. Quais eventos Kafka são emitidos em caso de sucesso? E de falha?
5. Há requisitos de performance específicos além dos defaults (P50 < 550ms, P99 < 1s)?

Não faça perguntas sobre dados que já existem no codebase ou que são deriváveis das specs existentes.

## Step 2: Escrever a spec

Escreva `$SPEC_PATH` seguindo **exatamente** este template de 11 seções:

```markdown
# Especificação: [Nome da Feature/Método]

## 1. Assinatura
[Assinatura do método/endpoint com tipos Java 21]
[Ex: POST /api/v1/users/register → TransactionResult]

## 2. Tipos de Dados

### Input
[Record Java com todos os campos, tipos e validações Bean Validation]

### Output
[Sealed interface ou Record de resposta]

## 3. Pré-condições
- [O que deve ser verdade antes de executar]
- [Ex: idempotencyKey não existe no banco]

## 4. Pós-condições (Sucesso)
- [O que deve ser verdade após executar com sucesso]
- [Ex: entidade persistida, evento Kafka emitido]

## 5. Pós-condições (Erro)
| Código | Mensagem | HTTP Status | Retryable |
|--------|----------|-------------|-----------|
| [ERRO_CODE] | [mensagem] | [4xx/5xx] | [true/false] |

## 6. Invariantes
- [O que é sempre verdade durante e após execução]
- [Ex: nunca logar número de cartão, amountInCents sempre positivo]

## 7. Casos Extremos

| ID | Input | Comportamento Esperado | Output |
|----|-------|------------------------|--------|
| CE-001 | [input] | [comportamento] | [output] |

## 8. Exemplos Concretos

### Sucesso
**Input:**
```json
[exemplo de request]
```
**Output:**
```json
[exemplo de response]
```
**Efeitos colaterais:** [eventos emitidos, registros criados, cache invalidado]

### Erro
**Input:**
```json
[exemplo de request inválido]
```
**Output:**
```json
[exemplo de error response RFC 7807]
```

## 9. Efeitos Colaterais
- **Kafka:** [tópico e evento emitido]
- **Redis:** [chaves criadas/invalidadas]
- **Email/Notificação:** [se aplicável]
- **Auditoria:** [campos logados — sem dados sensíveis]

## 10. Performance
- **P50:** [valor em ms]
- **P99:** [valor em ms — default < 1s para endpoints de pagamento]
- **Throughput esperado:** [TPS estimado]

## 11. Segurança
- **Autenticação:** [JWT obrigatório / endpoint público]
- **Autorização:** [roles que podem acessar]
- **Dados sensíveis:** [quais campos NÃO devem aparecer em logs]
- **Rate limiting:** [limite por IP/usuário se aplicável]
- **Validações críticas:** [PCI DSS / LGPD se relevante]
```

## Step 3: Checklist de validação

Após escrever a spec, valide cada item:

- [ ] Todos os inputs documentados com tipos Java 21?
- [ ] Todos os outputs documentados (sucesso + erro)?
- [ ] Todos os casos extremos cobertos (mínimo 3)?
- [ ] Um dev novo consegue implementar sem tirar dúvidas?
- [ ] Requisitos de performance definidos?
- [ ] Dados sensíveis tratados explicitamente na seção 11?
- [ ] Eventos Kafka documentados (seção 9)?
- [ ] Invariantes definidas (seção 6)?

Se algum item falhar, complete a seção correspondente antes de prosseguir.

## Step 4: Apresentar e aprovar

1. Leia `$SPEC_PATH` e apresente um resumo estruturado ao usuário:

```
## Spec pronta: $SPEC_PATH

**Assinatura:** [seção 1]
**Casos extremos cobertos:** [N]
**Erros documentados:** [N]
**Eventos Kafka:** [lista]
**Checklist:** [X/8 itens ✓]
```

2. Use `AskUserQuestion`:
   - **"Spec aprovada — pronto para TDD"** → encerre com instrução: "Próximo passo: execute `/sdd-build <feature>` ou escreva os testes RED baseados nesta spec."
   - **"Ajustar seção X"** — usuário descreve o ajuste via campo "Other". Aplique, revalide o checklist, volte ao topo do Step 4.
   - **"Cancelar"** — encerre sem deletar o arquivo gerado

## Regras

- Nunca gerar código de produção — apenas a spec
- Nunca sobrescrever uma spec existente sem confirmação explícita
- Sempre referenciar o CLAUDE.md do projeto para padrões de tipos, erros e segurança
- A spec deve ser detalhada o suficiente para que três devs diferentes implementem código funcionalmente idêntico
- Campos `amountInCents` nunca em formato decimal — sempre Long em centavos
- Nunca documentar CPF, número de cartão ou CVV como campo logado na seção 9
