# Spec: Frontend Auth

**ID:** SPEC-FE-AUTH-001
**Área:** front-auth
**Status:** Draft

---

## 1. Visão Geral

Implementa o fluxo completo de autenticação do sistema: registro de usuários (CUSTOMER e MERCHANT_OWNER), login com suporte a 2FA, verificação em duas etapas (TOTP), configuração de 2FA com QR code e recovery codes, e confirmação de email. Consome a API pública do user-service (`/api/v1/auth/*`) e depende do `AuthContext` e `apiClient` providos pela área front-shared.

---

## 2. User Stories

### P1: Register — Cadastro de Usuário ⭐ MVP

**User Story:** As a visitor, I want to create an account so that I can use the payment system as a customer or merchant owner.

**Acceptance Criteria:**
1. WHEN user fills email, password, fullName and selects CUSTOMER role THEN form SHALL submit only those 3 fields
2. WHEN user selects MERCHANT_OWNER role THEN form SHALL show additional required fields companyName and cnpj (14 digits)
3. WHEN CNPJ is provided THEN form SHALL validate 14-digit format + proper input masking
4. WHEN register succeeds (HTTP 201) THEN form SHALL show success toast and redirect to `/auth/confirm-email?email=<email>`
5. WHEN email already exists (409 EMAIL_ALREADY_EXISTS) THEN form SHALL show inline error "Este email já está cadastrado"
6. WHEN password is weak (400 WEAK_PASSWORD) THEN form SHALL show validation requirements (8+ chars, 1 uppercase, 1 number, 1 special)
7. WHEN CNPJ is invalid (400 INVALID_CNPJ) THEN form SHALL show inline error "CNPJ inválido"
8. WHEN merchant data is missing for MERCHANT_OWNER (400 MISSING_MERCHANT_DATA) THEN form SHALL highlight the empty required fields

**Independent Test:** Mock `apiClient.post('/auth/register')` and assert correct body sent for each role variant. Assert redirect after success.

---

### P1: Login — Autenticação ⭐ MVP

**User Story:** As a registered user, I want to log in with my email and password so that I can access my account.

**Acceptance Criteria:**
1. WHEN credentials are valid and 2FA is disabled THEN form SHALL call login API, AuthContext SHALL store accessToken, and user SHALL be redirected to dashboard (`/`)
2. WHEN user has 2FA enabled THEN form SHALL call login API, receive `requiresTwoFactor: true` + `twoFactorToken`, and redirect to `/auth/2fa/verify` with the token
3. WHEN account is locked (423 ACCOUNT_LOCKED) THEN form SHALL display lockout message with `unlockAt` time (ISO 8601) — "Conta bloqueada até HH:mm"
4. WHEN email is not confirmed (403 ACCOUNT_NOT_CONFIRMED) THEN form SHALL show message "Confirme seu email antes de fazer login" with link to `/auth/confirm-email`
5. WHEN credentials are invalid (401 INVALID_CREDENTIALS) THEN form SHALL show generic error "Email ou senha inválidos"
6. WHEN rate limit is exceeded (429 TOO_MANY_REQUESTS) THEN form SHALL display cooldown message and disable submit button temporarily
7. WHEN account is disabled (403 ACCOUNT_DISABLED) THEN form SHALL show "Conta desativada. Entre em contato com o suporte."

**Independent Test:** Mock `apiClient.post('/auth/login')` with each response variant and assert correct UI state.

---

### P1: 2FA Verify — Verificação em Duas Etapas ⭐ MVP

**User Story:** As a user with 2FA enabled, I want to enter my TOTP code so that I can complete the login process.

**Acceptance Criteria:**
1. WHEN user enters a valid 6-digit TOTP code THEN form SHALL call `/auth/2fa/verify` with `{ twoFactorToken, totpCode }`, AuthContext SHALL store the returned accessToken, and redirect to dashboard
2. WHEN user enters an invalid TOTP code (401 INVALID_TOTP_CODE) THEN form SHALL show error "Código inválido ou expirado" and allow retry
3. WHEN user clicks "Usar código de recuperação" THEN form SHALL hide TOTP input and show recovery code input
4. WHEN user enters a valid recovery code THEN form SHALL call `/auth/2fa/recovery` and complete login
5. WHEN recovery code is invalid (401 RECOVERY_CODE_INVALID) THEN form SHALL show error "Código de recuperação inválido"
6. WHEN all recovery codes are exhausted (422 RECOVERY_CODE_EXHAUSTED) THEN form SHALL show message "Todos os códigos de recuperação foram usados. Reconfigure o 2FA."

**Independent Test:** Mock `apiClient.post('/auth/2fa/verify')` and assert redirect. Mock `apiClient.post('/auth/2fa/recovery')` and assert redirect.

---

### P1: 2FA Setup — Configuração do 2FA ⭐ MVP

**User Story:** As an authenticated user, I want to enable two-factor authentication so that my account is more secure.

**Acceptance Criteria:**
1. WHEN user navigates to 2FA setup page THEN page SHALL call `/auth/2fa/setup` and display:
   - QR code rendered from `qrCodeUrl` (data:image/png;base64) into an `<img>` tag
   - Secret key as plain text (Base32, user can copy)
   - 8 recovery codes displayed as a numbered list
2. WHEN user enters the first TOTP code from their authenticator app and submits THEN page SHALL call `/auth/2fa/confirm` with `{ code }`, and on success show confirmation message and redirect to security settings
3. WHEN 2FA is already enabled (409 TWO_FACTOR_ALREADY_ENABLED) THEN page SHALL display warning and redirect to security settings
4. Recovery codes SHALL be displayed with a prominent warning: "Salve estes códigos em um local seguro. Eles não serão exibidos novamente."
5. WHEN user confirms setup with invalid code (401 INVALID_TOTP_CODE) THEN page SHALL show error and allow retry

**Independent Test:** Mock `apiClient.post('/auth/2fa/setup')` returning mock secret, qrCodeUrl, and recoveryCodes. Render and assert all elements visible.

---

### P1: Confirm Email — Confirmação de Email ⭐ MVP

**User Story:** As a newly registered user, I want to confirm my email address so that I can log in.

**Acceptance Criteria:**
1. WHEN user arrives on `/auth/confirm-email?email=<email>&token=<token>` with both URL params THEN page SHALL auto-submit the confirmation with the token
2. WHEN user arrives on `/auth/confirm-email?email=<email>` without token THEN page SHALL show a form with email (pre-filled) and token input field
3. WHEN confirmation succeeds (HTTP 200) THEN page SHALL redirect to `/auth/login` with success message "Email confirmado! Faça seu login."
4. WHEN token is invalid or expired (401) THEN page SHALL show error "Token inválido ou expirado. Solicite um novo link de confirmação."
5. WHEN the form is submitted with empty or malformed token THEN form SHALL show validation error "Token é obrigatório"

**Independent Test:** Mock `apiClient.post('/auth/confirm-email')` with success/failure and assert redirect and error states.

---

## 3. Interface Types (TypeScript)

```typescript
// ── Domain Types ───────────────────────────────────────

export type UserRole = 'CUSTOMER' | 'MERCHANT_OWNER';

export interface AuthUser {
  userId: string;
  email: string;
  role: UserRole;
  merchantId: string | null;
}

// ── API Request DTOs ──────────────────────────────────

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  role: UserRole;
  companyName?: string;    // required when role=MERCHANT_OWNER
  cnpj?: string;           // required when role=MERCHANT_OWNER, 14 digits
}

export interface LoginRequest {
  email: string;
  password: string;
  totpCode?: string;       // 6 digits, required when 2FA active
  deviceFingerprint?: string;
}

export interface TwoFactorVerifyRequest {
  twoFactorToken: string;
  totpCode: string;        // 6 digits TOTP
}

export interface TwoFactorRecoveryRequest {
  email: string;
  recoveryCode: string;
}

export interface TwoFactorConfirmRequest {
  code: string;            // first TOTP code from authenticator app
}

export interface ConfirmEmailRequest {
  email: string;
  token: string;
}

// ── API Response DTOs ─────────────────────────────────

export interface RegisterResponse {
  userId: string;
  email: string;
  role: UserRole;
  merchantId: string | null;
  emailConfirmed: false;
}

export interface LoginSuccessResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: 900;              // seconds
  requiresTwoFactor: false;
}

export interface LoginRequiresTwoFactorResponse {
  requiresTwoFactor: true;
  twoFactorToken: string;
}

export type LoginResponse = LoginSuccessResponse | LoginRequiresTwoFactorResponse;

export interface TwoFactorSetupResponse {
  secret: string;              // Base32
  qrCodeUrl: string;           // data:image/png;base64,...
  otpAuthUrl: string;          // otpauth:// URI
  recoveryCodes: string[];     // exactly 8 codes
}

export interface ApiError {
  code: string;
  message: string;
  retryable: boolean;
}

export interface ApiResponse<T> {
  data: T | null;
  meta: { timestamp: string; requestId: string };
  errors: ApiError[];
}

// ── AuthContext Contract (consumido de front-shared) ──

export interface IAuthContext {
  user: AuthUser | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isMerchant: boolean;
  login: (accessToken: string, user: AuthUser) => void;
  logout: () => Promise<void>;
  refreshToken: () => Promise<string | null>;
  loading: boolean;
}

// ── API Client Contract (consumido de front-shared) ───

export interface IApiClient {
  post<TReq, TRes>(url: string, body?: TReq, options?: RequestInit): Promise<ApiResponse<TRes>>;
  get<TRes>(url: string, options?: RequestInit): Promise<ApiResponse<TRes>>;
  patch<TReq, TRes>(url: string, body?: TReq): Promise<ApiResponse<TRes>>;
}

// ── Form State Types ──────────────────────────────────

export interface RegisterFormData {
  email: string;
  password: string;
  fullName: string;
  role: UserRole;
  companyName: string;
  cnpj: string;
}

export interface LoginFormData {
  email: string;
  password: string;
}

export interface TwoFactorVerifyFormData {
  totpCode: string;
}

export interface TwoFactorRecoveryFormData {
  recoveryCode: string;
}

export interface ConfirmEmailFormData {
  email: string;
  token: string;
}
```

---

## 4. API Endpoints Consumidos

| Endpoint | Método | Headers | Request | Response |
|----------|--------|---------|---------|----------|
| `/api/v1/auth/register` | POST | — | `RegisterRequest` | `ApiResponse<RegisterResponse>` |
| `/api/v1/auth/login` | POST | — | `LoginRequest` | `ApiResponse<LoginResponse>` |
| `/api/v1/auth/confirm-email` | POST | — | `ConfirmEmailRequest` | `ApiResponse<{ message: string }>` |
| `/api/v1/auth/refresh` | POST | Cookie: refreshToken | — | `ApiResponse<{ accessToken: string; expiresIn: 900 }>` |
| `/api/v1/auth/logout` | POST | Bearer JWT | — | `ApiResponse<null>` |
| `/api/v1/auth/2fa/setup` | POST | Bearer JWT | — | `ApiResponse<TwoFactorSetupResponse>` |
| `/api/v1/auth/2fa/confirm` | POST | Bearer JWT | `TwoFactorConfirmRequest` | `ApiResponse<{ message: string }>` |
| `/api/v1/auth/2fa/verify` | POST | — | `TwoFactorVerifyRequest` | `ApiResponse<LoginSuccessResponse>` |
| `/api/v1/auth/2fa/recovery` | POST | — | `TwoFactorRecoveryRequest` | `ApiResponse<LoginSuccessResponse>` |
| `/api/v1/auth/2fa/disable` | POST | Bearer JWT | — | `ApiResponse<{ message: string }>` |

---

## 5. Mock Contracts

```typescript
// Dependência: front-shared → IApiClient
// Se front-shared não estiver pronto, front-auth criará mock em:
//   __mocks__/apiClient.ts

export interface IMockApiClient {
  post: <TReq, TRes>(url: string, body?: TReq) => Promise<ApiResponse<TRes>>;
  get: <TRes>(url: string) => Promise<ApiResponse<TRes>>;
}

// Dependência: front-shared → IAuthContext
// Se front-shared não estiver pronto, front-auth criará mock em:
//   __mocks__/authContext.ts

export interface IMockAuthContext {
  user: AuthUser | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  login: (accessToken: string, user: AuthUser) => void;
  logout: () => Promise<void>;
  loading: boolean;
}

// Ambos os mocks seguem as interfaces definidas em §3.
// Injeção via props ou React Context — nunca import direto.
```

---

## 6. Error Scenarios

| Erro | Código HTTP | Comportamento esperado |
|------|------------|----------------------|
| EMAIL_ALREADY_EXISTS | 409 | Show inline: "Este email já está cadastrado" |
| WEAK_PASSWORD | 400 | Show validation requirements list |
| INVALID_EMAIL_FORMAT | 400 | Show: "Formato de email inválido" |
| INVALID_ROLE | 400 | Show: "Tipo de usuário inválido" |
| INVALID_CNPJ | 400 | Show: "CNPJ inválido" |
| MISSING_MERCHANT_DATA | 400 | Highlight missing fields for merchant role |
| CNPJ_ALREADY_REGISTERED | 409 | Show: "Este CNPJ já está cadastrado" |
| INVALID_CREDENTIALS | 401 | Show: "Email ou senha inválidos" |
| ACCOUNT_LOCKED | 423 | Show: "Conta bloqueada até {unlockAt}" |
| ACCOUNT_NOT_CONFIRMED | 403 | Show: "Confirme seu email" + link para confirm-email |
| INVALID_TOTP_CODE | 401 | Show: "Código inválido ou expirado" |
| ACCOUNT_DISABLED | 403 | Show: "Conta desativada. Entre em contato com o suporte." |
| TOO_MANY_REQUESTS | 429 | Show cooldown, disable submit button temporarily |
| TWO_FACTOR_ALREADY_ENABLED | 409 | Show: "2FA já está ativo" + redirect to security |
| TWO_FACTOR_NOT_ENABLED | 422 | Show: "2FA não está ativo" |
| RECOVERY_CODE_INVALID | 401 | Show: "Código de recuperação inválido" |
| RECOVERY_CODE_EXHAUSTED | 422 | Show: "Todos os códigos foram usados. Reconfigure o 2FA." |
| REFRESH_TOKEN_INVALID | 401 | Force logout, redirect to login |
| REFRESH_TOKEN_EXPIRED | 401 | Force logout, redirect to login |

---

## 7. Requirement Traceability

| ID | Story | Status |
|----|-------|--------|
| AUTH-01 | P1: Register — campos condicionais por role | Pending |
| AUTH-02 | P1: Register — validação CNPJ 14 dígitos | Pending |
| AUTH-03 | P1: Register — sucesso → redirect confirm-email | Pending |
| AUTH-04 | P1: Register — erro EMAIL_ALREADY_EXISTS | Pending |
| AUTH-05 | P1: Register — erro WEAK_PASSWORD | Pending |
| AUTH-06 | P1: Register — erro MISSING_MERCHANT_DATA | Pending |
| AUTH-07 | P1: Login — sucesso sem 2FA → redirect dashboard | Pending |
| AUTH-08 | P1: Login — 2FA ativo → redirect 2FA verify | Pending |
| AUTH-09 | P1: Login — erro ACCOUNT_LOCKED com unlockAt | Pending |
| AUTH-10 | P1: Login — erro ACCOUNT_NOT_CONFIRMED | Pending |
| AUTH-11 | P1: Login — erro INVALID_CREDENTIALS genérico | Pending |
| AUTH-12 | P1: Login — erro TOO_MANY_REQUESTS | Pending |
| AUTH-13 | P1: Login — erro ACCOUNT_DISABLED | Pending |
| AUTH-14 | P1: 2FA Verify — TOTP code válido → login completo | Pending |
| AUTH-15 | P1: 2FA Verify — TOTP inválido → retry | Pending |
| AUTH-16 | P1: 2FA Verify — fallback recovery code | Pending |
| AUTH-17 | P1: 2FA Setup — exibir QR code, secret, recovery codes | Pending |
| AUTH-18 | P1: 2FA Setup — confirmar com primeiro TOTP | Pending |
| AUTH-19 | P1: 2FA Setup — aviso recovery codes único | Pending |
| AUTH-20 | P1: Confirm Email — auto-submit com token na URL | Pending |
| AUTH-21 | P1: Confirm Email — input manual de token | Pending |
| AUTH-22 | P1: Confirm Email — sucesso → redirect login | Pending |
| AUTH-23 | P1: Confirm Email — erro token inválido/expirado | Pending |
