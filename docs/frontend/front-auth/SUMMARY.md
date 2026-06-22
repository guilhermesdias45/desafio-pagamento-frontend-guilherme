# Documentation — front-auth

**Documenter:** agent-documenter
**Date:** 2026-06-22
**Cycle:** 2

---

## What Was Built

| Arquivo | Propósito |
|---------|-----------|
| `src/pages/auth/RegisterPage.tsx` | Página de cadastro com suporte a CUSTOMER e MERCHANT_OWNER, validação CNPJ |
| `src/pages/auth/LoginPage.tsx` | Página de login com suporte a 2FA, tratamento de erros (locked, not confirmed, etc) |
| `src/pages/auth/TwoFactorVerifyPage.tsx` | Página de verificação 2FA com TOTP code e fallback para recovery code |
| `src/pages/auth/TwoFactorSetupPage.tsx` | Página de configuração 2FA com QR code, secret e recovery codes |
| `src/pages/auth/ConfirmEmailPage.tsx` | Página de confirmação de email com auto-submit via URL params |
| `src/api/auth-api.ts` | Funções de API para auth (register, login, confirm2FA, verify2FA, recover2FA, confirmEmail) |

## Tests Created

| Arquivo | Tipo | Qtde testes | Cobertura |
|---------|------|-------------|-----------|
| `src/pages/auth/RegisterPage.test.tsx` | Component | 9 | Renderização, campos condicionais (MERCHANT_OWNER), CNPJ mask/validation, submit success, erros (EMAIL_ALREADY_EXISTS, WEAK_PASSWORD, INVALID_CNPJ) |
| `src/pages/auth/LoginPage.test.tsx` | Component | 7 | Login success, 2NVALFA redirect, erros (IID_CREDENTIALS, ACCOUNT_LOCKED, ACCOUNT_NOT_CONFIRMED, ACCOUNT_DISABLED, TOO_MANY_REQUESTS) |
| `src/pages/auth/TwoFactorVerifyPage.test.tsx` | Component | 5 | TOTP verify success, TOTP invalid, recovery code fallback, recovery code invalid, recovery code exhausted |
| `src/pages/auth/TwoFactorSetupPage.test.tsx` | Component | 4 | Setup display (QR, secret, recovery codes), confirm success, confirm invalid, already enabled |
| `src/pages/auth/ConfirmEmailPage.test.tsx` | Component | 3 | Auto-submit com token na URL, manual submit, token inválido/expirado |

**Total de testes:** 28

## Mocked Functions

| Função mockada | Interface original | Motivo | Localização do mock | Comportamento do mock | Status |
|---------------|-------------------|--------|--------------------|-----------------------|--------|
| `IApiClient.post` | `.specs/features/front-shared/spec.md §3` | front-shared não estava pronto durante desenvolvimento | `src/__mocks__/apiClient.ts` | Retorna `Promise.resolve({ data: null, meta: {...}, errors: [] })` | ✅ Resolvido (front-shared implementado) |
| `IApiClient.get` | `.specs/features/front-shared/spec.md §3` | front-shared não estava pronto durante desenvolvimento | `src/__mocks__/apiClient.ts` | Retorna `Promise.resolve({ data: null, meta: {...}, errors: [] })` | ✅ Resolvido (front-shared implementado) |
| `IApiClient.patch` | `.specs/features/front-shared/spec.md §3` | front-shared não estava pronto durante desenvolvimento | `src/__mocks__/apiClient.ts` | Retorna `Promise.resolve({ data: null, meta: {...}, errors: [] })` | ✅ Resolvido (front-shared implementado) |
| `IAuthContext.login` | `.specs/features/front-shared/spec.md §3` | front-shared não estava pronto durante desenvolvimento | `src/__mocks__/authContext.ts` | Mock function via `vi.fn()` | ✅ Resolvido (front-shared implementado) |
| `IAuthContext.logout` | `.specs/features/front-shared/spec.md §3` | front-shared não estava pronto durante desenvolvimento | `src/__mocks__/authContext.ts` | Mock function via `vi.fn()` | ✅ Resolvido (front-shared implementado) |
| `IAuthContext.refreshToken` | `.specs/features/front-shared/spec.md §3` | front-shared não estava pronto durante desenvolvimento | `src/__mocks__/authContext.ts` | Retorna `Promise.resolve(null)` | ✅ Resolvido (front-shared implementado) |

## Technical Decisions

| Decisão | Opção | Motivo |
|---------|-------|--------|
| CNPJ Validation | Implementação completa com dígitos verificadores | Segue regras oficiais da Receita Federal. Evita cadastros inválidos. |
| CNPJ Formatting | Máscara automática (XX.XXX.XXX/XXXX-XX) | UX melhorada. Usuário vê formato correto enquanto digita. |
| Error Mapping | Função `getErrorMessage` por página | Centraliza mapeamento de códigos de erro para mensagens amigáveis. |
| Dependency Injection | apiClient e authContext via props | Facilita testes. Permite substituir por mocks. Segue SOLID. |
| Navigate Abstraction | Prop `navigate` opcional com fallback para `window.location.href` | Facilita testes sem React Router. Produção usa router real. |
| 2FA Flow | Redirect para página dedicada com token na URL | Separa concerns. Permite deep linking. Segue spec do backend. |
| Recovery Codes | Display com aviso destacado | Segurança. Usuário é alertado que códigos não serão exibidos novamente. |

## Requirements Status

| ID | Story | Status |
|----|-------|--------|
| AUTH-01 | P1: Register — campos condicionais por role | ✅ Implemented |
| AUTH-02 | P1: Register — validação CNPJ 14 dígitos | ✅ Implemented |
| AUTH-03 | P1: Register — sucesso → redirect confirm-email | ✅ Implemented |
| AUTH-04 | P1: Register — erro EMAIL_ALREADY_EXISTS | ✅ Implemented |
| AUTH-05 | P1: Register — erro WEAK_PASSWORD | ✅ Implemented |
| AUTH-06 | P1: Register — erro MISSING_MERCHANT_DATA | ✅ Implemented |
| AUTH-07 | P1: Login — sucesso sem 2FA → redirect dashboard | ✅ Implemented |
| AUTH-08 | P1: Login — 2FA ativo → redirect 2FA verify | ✅ Implemented |
| AUTH-09 | P1: Login — erro ACCOUNT_LOCKED com unlockAt | ✅ Implemented |
| AUTH-10 | P1: Login — erro ACCOUNT_NOT_CONFIRMED | ✅ Implemented |
| AUTH-11 | P1: Login — erro INVALID_CREDENTIALS genérico | ✅ Implemented |
| AUTH-12 | P1: Login — erro TOO_MANY_REQUESTS | ✅ Implemented |
| AUTH-13 | P1: Login — erro ACCOUNT_DISABLED | ✅ Implemented |
| AUTH-14 | P1: 2FA Verify — TOTP code válido → login completo | ✅ Implemented |
| AUTH-15 | P1: 2FA Verify — TOTP inválido → retry | ✅ Implemented |
| AUTH-16 | P1: 2FA Verify — fallback recovery code | ✅ Implemented |
| AUTH-17 | P1: 2FA Setup — exibir QR code, secret, recovery codes | ✅ Implemented |
| AUTH-18 | P1: 2FA Setup — confirmar com primeiro TOTP | ✅ Implemented |
| AUTH-19 | P1: 2FA Setup — aviso recovery codes único | ✅ Implemented |
| AUTH-20 | P1: Confirm Email — auto-submit com token na URL | ✅ Implemented |
| AUTH-21 | P1: Confirm Email — input manual de token | ✅ Implemented |
| AUTH-22 | P1: Confirm Email — sucesso → redirect login | ✅ Implemented |
| AUTH-23 | P1: Confirm Email — erro token inválido/expirado | ✅ Implemented |

## Test Commands

```bash
# Rodar todos os testes de front-auth
npm run test:run -- src/pages/auth src/api/auth-api

# Rodar com cobertura
npm run test:coverage -- src/pages/auth src/api/auth-api

# Typecheck
npx tsc --noEmit
```

## Next Cycle Input

### Dependências não resolvidas
✅ Todas as dependências de front-shared foram resolvidas (ApiClient e AuthContext implementados).

### Bloqueios ativos
Nenhum.

### Sugestões
1. **Migrar para UI Components**: Páginas ainda usam HTML nativo (`<input>`, `<button>`). Considerar migrar para componentes de front-shared (`Button`, `Input`) para consistência visual.
2. **Extrair validadores**: Funções `formatCNPJ` e `validateCNPJ` poderiam ser movidas para `src/lib/validators.ts` para reutilização em outras áreas (ex: front-merchant).
3. **Extrair error mapping**: Função `getErrorMessage` poderia ser compartilhada entre páginas ou movida para `src/lib/errors.ts`.
4. **Adicionar loading states**: Páginas poderiam usar `<Spinner>` de front-shared durante submits.

### Mocks pendentes
✅ Todos os mocks foram resolvidos. front-auth agora pode usar as implementações reais de front-shared.

### Issues conhecidas
Nenhuma issue bloqueante. Sugestões acima são melhorias não-críticas para ciclos futuros.
