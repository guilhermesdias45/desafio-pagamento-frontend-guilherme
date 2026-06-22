# Testing — Acabou o Mony (Backend)

## Stack de Testes (Backend)

| Tipo | Framework | Onde encontrar exemplos |
|------|-----------|------------------------|
| Unitário | JUnit 5 + Mockito | `services/*/src/test/` (ex: `*ServiceTest.java`) |
| Integração | Testcontainers (PG + Redis + Kafka) | `services/*/src/test/` (ex: `*IntegrationTest.java`) |
| API | MockMvc | `services/*/controller/*Test.java` |
| Carga | k6 | `scripts/k6/` |
| Cobertura | JaCoCo ≥ 90% | `qa-output/` |

## Stack de Testes (Frontend — a ser criada)

| Tipo | Framework | Quando executar |
|------|-----------|----------------|
| Unitário | Vitest + React Testing Library | Durante ciclo do Coder (TDD) |
| Componente | Vitest + RTL (`render`, `screen`) | Durante ciclo do Coder |
| Hook | Vitest + `renderHook` | Durante ciclo do Coder |
| E2E | Playwright | **Apenas após TODO código pronto** (fase Integration) |

## Comandos Esperados (Frontend)

```bash
npm run test          # Vitest modo watch (dev)
npm run test:run      # Vitest modo single-run (CI)
npm run test:coverage # Vitest com cobertura
npm run lint          # ESLint
npm run typecheck     # tsc --noEmit
npm run build         # Vite build
```

## Regras para o Pipeline

- Coder sempre escreve teste ANTES do código (RED → GREEN)
- Reviewer verifica FIRST (Fast, Isolated, Repeatable, Self-validating, Timely)
- E2E blocante: só executa na Integration Phase, após resolver todos os mocks
- Cobertura mínima por área: ≥ 80% (linhas)
