# Task 09: CnpjValidator + @Cnpj annotation (Bean Validation custom)

## Objective
Implementar um validador Bean Validation custom (`@Cnpj`) que valida CNPJ por formato (14 dígitos numéricos) **e** dígitos verificadores via algoritmo Módulo 11. Reutilizável em qualquer Record/DTO via annotation.

## Context
**Quick Context:**
- Não há consulta a Receita Federal nesta sprint — apenas formato + dígitos verificadores.
- A annotation será usada por `RegisterRequest` em task-14.
- Validator é standalone (sem dependências de Spring/JPA) — testes são unitários puros.
- Pode rodar em paralelo com task-08, task-10, task-07 etc.

Ler antes:
- `specs/user-service/spec.md` §3.1 (validação de CNPJ)
- `tasks/dev2/updated-prd.md` §5 (CE-REG-003 e CE-REG-004)
- Algoritmo Módulo 11 para CNPJ: documentação Receita Federal ou Wikipedia "Cadastro Nacional da Pessoa Jurídica".

## Target Files
**Create:**
- `services/user-service/src/main/java/com/acaboumony/user/validation/Cnpj.java` (annotation)
- `services/user-service/src/main/java/com/acaboumony/user/validation/CnpjValidator.java` (ConstraintValidator)
- `services/user-service/src/test/java/com/acaboumony/user/validation/CnpjValidatorTest.java`

## Dependencies
- Depends on: None (paralelo a Wave 2)
- Blocks: task-14

## TDD Mode

### RED
Em `CnpjValidatorTest`, escrever testes parametrizados:

- `deve_aceitar_cnpj_quando_14_digitos_e_dv_validos()` — entrada `"11222333000181"` (CNPJ válido conhecido), espera `true`.
- `deve_rejeitar_quando_menos_de_14_digitos()` — entrada `"1122233300018"`, espera `false` (**CE-REG-003**).
- `deve_rejeitar_quando_mais_de_14_digitos()` — entrada `"112223330001811"`, espera `false`.
- `deve_rejeitar_quando_contem_letras()` — entrada `"1122233300018A"`, espera `false`.
- `deve_rejeitar_quando_contem_formatacao_com_pontos_e_barras()` — entrada `"11.222.333/0001-81"`, espera `false` (vamos exigir já-limpo; controller pode normalizar antes — documentar).
- `deve_rejeitar_quando_todos_os_digitos_iguais()` — entrada `"11111111111111"`, espera `false` (CNPJs com todos os dígitos iguais passam no Módulo 11 mas são inválidos por convenção).
- `deve_rejeitar_quando_dv_incorretos()` — entrada `"11222333000199"` (dv errado), espera `false` (**CE-REG-004**).
- `deve_aceitar_null_quando_role_nao_e_merchant_owner()` — entrada `null`, espera `true` (CnpjValidator aceita null; a obrigatoriedade condicional é problema do `@ValidRegisterRequest` em task-14).

Roda → falha (annotation/validator não existem).

### GREEN
1. **`@Cnpj` annotation** — `@Target({FIELD, PARAMETER})`, `@Retention(RUNTIME)`, `@Constraint(validatedBy = CnpjValidator.class)`, `message() default "INVALID_CNPJ"`, `groups()`, `payload()`.
2. **`CnpjValidator`** implementa `ConstraintValidator<Cnpj, String>`:
   ```java
   public boolean isValid(String value, ConstraintValidatorContext ctx) {
       if (value == null) return true;  // null OK; obrigatoriedade é outra annotation
       if (!value.matches("\\d{14}")) return false;
       if (value.chars().distinct().count() == 1) return false;  // todos iguais
       return checkDigit(value.substring(0, 12), value.substring(12));
   }
   ```
3. Implementar algoritmo Módulo 11 para CNPJ:
   - Primeiros 12 dígitos: pesos `[5,4,3,2,9,8,7,6,5,4,3,2]`, soma, mod 11. DV1 = 0 se resto < 2, senão 11-resto.
   - Primeiros 13 dígitos (incluindo DV1): pesos `[6,5,4,3,2,9,8,7,6,5,4,3,2]`, soma, mod 11. DV2 = 0 se resto < 2, senão 11-resto.
   - Comparar DV1+DV2 com os 2 últimos dígitos do input.

### REFACTOR
- Extrair `Pesos` como `static final int[]` para evitar recriar arrays a cada validação.
- Garantir method `checkDigit` sem alocação (usar `charAt` em vez de `substring` + `chars`).
- Documentar via JavaDoc o porquê de rejeitar "todos dígitos iguais" mesmo passando Módulo 11.

## Acceptance Criteria
- [ ] `@Cnpj` annotation criada com `message = "INVALID_CNPJ"`
- [ ] `CnpjValidator` implementa `ConstraintValidator<Cnpj, String>`
- [ ] `CnpjValidatorTest` passa (8 testes verdes), incluindo CE-REG-003 e CE-REG-004
- [ ] Validator aceita `null` (CE-REG-002 / obrigatoriedade é responsabilidade do `@ValidRegisterRequest` em task-14)
- [ ] Validator rejeita CNPJ com dígitos verificadores errados (Módulo 11)
- [ ] Validator rejeita strings com formatação `11.222.333/0001-81` (input deve estar limpo)
- [ ] Cobertura JaCoCo do pacote `validation` ≥ 95% (validador puro deve ser 100%)
