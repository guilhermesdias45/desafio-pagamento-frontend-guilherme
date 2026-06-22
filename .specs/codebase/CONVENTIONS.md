# Conventions — Acabou o Mony

## Backend (Java)

| Convenção | Detalhe | Onde ver exemplos |
|-----------|---------|-------------------|
| Pacotes | `com.acaboumony.{service}` | `services/*/src/main/java/com/acaboumony/` |
| DTOs | Records (Java 21) | `services/*/dto/` |
| Result | Sealed interface (Success/Failure) | `services/*/result/` |
| IDs negócio | `txn_`, `ref_`, `ord_` prefix | `specs/payment-service/spec.md` |
| Moeda | BIGINT em centavos (nunca DECIMAL) | `specs/payment-service/spec.md §3.2` |
| Timestamps | TIMESTAMPTZ (UTC) | `specs/payment-service/spec.md §5.2` |
| Testes | JUnit 5 + Mockito + Testcontainers | `services/*/src/test/` |
| Cobertura | JaCoCo ≥ 90% | `qa-output/` |

## Frontend (a ser definido nos specs de cada área)

| Convenção | Padrão |
|-----------|--------|
| Naming componentes | PascalCase (`LoginForm.tsx`) |
| Naming funções/var | camelCase |
| Naming arquivos API | kebab-case (`auth-api.ts`) |
| CSS | Tailwind utility classes |
| Imports | Absolute paths (`@/api/`, `@/components/`) |
| Testes | Co-localizados: `Component.tsx` + `Component.test.tsx` |
| Estado | React Context para auth, props para o resto |
