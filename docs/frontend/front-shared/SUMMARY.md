# Documentation — front-shared

**Documenter:** agent-documenter
**Date:** 2026-06-22
**Cycle:** 2

---

## What Was Built

| Arquivo | Propósito |
|---------|-----------|
| `src/lib/api-client.ts` | ApiClient class com interceptor JWT, refresh automático, error handling e headers customizados |
| `src/lib/palette.ts` | Definição da paleta de cores aprovada (primary, cream, dark) |
| `src/lib/contrast.ts` | Funções para cálculo de contraste WCAG AA |
| `src/contexts/AuthContext.tsx` | AuthProvider e useAuth hook para gerenciamento de autenticação global |
| `src/components/ui/Button.tsx` | Componente Button com variantes (primary, secondary, danger, ghost) e loading state |
| `src/components/ui/Input.tsx` | Componente Input com label e exibição de erro |
| `src/components/ui/Spinner.tsx` | Componente Spinner animado com tamanhos configuráveis (sm, md, lg) |
| `src/components/ui/ErrorMessage.tsx` | Componente ErrorMessage com título, mensagem e botão retry opcional |
| `src/components/AuthLayout.tsx` | Layout para páginas de autenticação (fundo cream, card centralizado) |
| `src/components/AppLayout.tsx` | Layout principal da aplicação (header azul, área de conteúdo) |
| `src/components/ProtectedRoute.tsx` | Guard para rotas protegidas (redireciona não autenticados para /login) |
| `src/components/GuestRoute.tsx` | Guard para rotas de guest (redireciona autenticados para /dashboard) |

## Tests Created

| Arquivo | Tipo | Qtde testes | Cobertura |
|---------|------|-------------|-----------|
| `src/lib/api-client.test.ts` | Unit | 17 | Authorization header, Content-Type, Idempotency-Key, X-Merchant-Id, error handling, 401 refresh/retry, HTTP methods, query params |
| `src/lib/palette.test.ts` | Unit | 3 | Validação de hex values da paleta |
| `src/lib/contrast.test.ts` | Unit | 3 | Cálculo de contraste WCAG AA |
| `src/contexts/AuthContext.test.tsx` | Integration | 7 | Initialization, refresh, login, logout, error handling, redirect on failure |
| `src/components/ui/Button.test.tsx` | Component | 9 | Renderização, variantes, loading state, onClick, fullWidth |
| `src/components/ui/Input.test.tsx` | Component | 8 | Label, input, placeholder, error display, type, onChange, htmlFor |
| `src/components/ui/Spinner.test.tsx` | Component | 6 | SVG rendering, animate-spin class, tamanhos (sm, md, lg) |
| `src/components/ui/ErrorMessage.test.tsx` | Component | 7 | Título, mensagem, retry button, onClick, icon, red theme |
| `src/components/AuthLayout.test.tsx` | Component | 4 | Children rendering, cream background, white card, shadow |
| `src/components/AppLayout.test.tsx` | Component | 5 | Children rendering, header primary bg, app name, main element, white text |
| `src/components/ProtectedRoute.test.tsx` | Component | 2 | Authenticated access, loading spinner |
| `src/components/GuestRoute.test.tsx` | Component | 2 | Unauthenticated access, loading spinner |

**Total de testes:** 73

## Mocked Functions

| Função mockada | Interface original | Motivo | Localização do mock | Comportamento do mock | Status |
|---------------|-------------------|--------|--------------------|-----------------------|--------|
| N/A | N/A | front-shared é a camada base, não depende de outras áreas | N/A | N/A | N/A |

**Outras áreas mockam front-shared:**
- `IApiClient` — mockado por front-auth em `src/__mocks__/apiClient.ts`
- `IAuthContext` — mockado por front-auth em `src/__mocks__/authContext.ts`

## Technical Decisions

| Decisão | Opção | Motivo |
|---------|-------|--------|
| API Client | Classe com injeção de token via callback | Permite testar sem instanciar AuthContext. Token é obtido dinamicamente. |
| Error Handling | ApiError class com status + errors array | Segue padrão da API backend. Facilita tratamento de múltiplos erros. |
| Refresh Strategy | Interceptor 401 → refresh → retry once | Transparente para o usuário. Evita re-login desnecessário. |
| AuthContext | React Context + hooks | Padrão React para estado global. Evita prop drilling. |
| UI Components | Tailwind utility classes | Consistência visual. Fácil manutenção. Sem CSS customizado. |
| Route Guards | HOC com Navigate | Declarativo. Fácil de testar. Segue padrão React Router v6. |
| Layouts | Componentes wrapper simples | Reutilizáveis. Não impõem estrutura rígida. |

## Requirements Status

| ID | Story | Status |
|----|-------|--------|
| SHARED-01 | P1: AuthContext carrega sessão ao iniciar | ✅ Implemented |
| SHARED-02 | P1: AuthContext armazena JWT + user após login bem-sucedido | ✅ Implemented |
| SHARED-03 | P1: AuthContext faz refresh automático via cookie httpOnly | ✅ Implemented |
| SHARED-04 | P1: AuthContext limpa estado e redireciona se refresh falhar | ✅ Implemented |
| SHARED-05 | P1: API Client injeta `Authorization: Bearer` header | ✅ Implemented |
| SHARED-06 | P1: API Client tenta refresh e retry em 401 | ✅ Implemented |
| SHARED-07 | P1: API Client parseia `errors[]` e lança `ApiError` | ✅ Implemented |
| SHARED-08 | P1: API Client gera Idempotency-Key UUID automático | ✅ Implemented |
| SHARED-09 | P1: ProtectedRoute redireciona não autenticado para `/login` | ✅ Implemented |
| SHARED-10 | P1: GuestRoute redireciona autenticado para `/dashboard` | ✅ Implemented |
| SHARED-11 | P1: AuthLayout com fundo cream (`#FEFCF5`) e card centralizado | ✅ Implemented |
| SHARED-12 | P1: AppLayout com header azul (`#5B8DEE`) e área main | ✅ Implemented |
| SHARED-13 | P1: Button com bg-primary, white text, variantes e loading state | ✅ Implemented |
| SHARED-14 | P1: Input com label, placeholder e exibição de erro | ✅ Implemented |
| SHARED-15 | P1: Spinner animado com tamanhos configuráveis | ✅ Implemented |
| SHARED-16 | P1: ErrorMessage com título, mensagem e botão retry | ✅ Implemented |
| SHARED-17 | P2: Paleta Tailwind config coincide com hex aprovados | ✅ Implemented |
| SHARED-18 | P2: Contraste WCAG AA ≥ 4.5:1 para pares texto/fundo | ✅ Implemented |
| SHARED-19 | P2: Componentes usam classes Tailwind semânticas, nunca raw hex | ✅ Implemented |

## Test Commands

```bash
# Rodar todos os testes de front-shared
npm run test:run -- src/lib src/contexts src/components

# Rodar com cobertura
npm run test:coverage -- src/lib src/contexts src/components

# Typecheck
npx tsc --noEmit
```

## Next Cycle Input

### Dependências não resolvidas
Nenhuma. front-shared é a camada base.

### Bloqueios ativos
Nenhum bloqueio crítico.

### Sugestões
1. **ISSUE-001**: Refatorar AuthContext para usar ApiClient injetado em vez de fetch direto (melhora testabilidade)
2. Considerar adicionar testes E2E para fluxo completo de autenticação (login → refresh → logout)
3. Adicionar storybook para documentação visual dos componentes UI

### Mocks pendentes
Nenhum. front-shared não usa mocks.

### Issues conhecidas
- **ISSUE-001**: AuthContext viola Dependency Inversion (fetch direto) — severidade média, não bloqueante
