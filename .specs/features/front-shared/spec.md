# Spec: Frontend Shared — API Client, Auth, Route Guards, Layouts & UI Components

**ID:** SPEC-FE-SHARED-01
**Área:** front-shared
**Status:** Draft

---

## 1. Visão Geral

Camada de infraestrutura compartilhada que toda aplicação consome. Fornece o API Client com JWT interceptor e refresh automático, o AuthContext com hook `useAuth`, os guards de rota `ProtectedRoute`/`GuestRoute`, os layouts `AuthLayout`/`AppLayout`, os componentes UI primitivos (`Button`, `Input`, `Spinner`, `ErrorMessage`), os tipos compartilhados (`User`, `AuthResponse`, `ApiError`, `PaginatedResponse`, `UserRole`) e a configuração Tailwind com a paleta aprovada. Nenhuma outra área importa código diretamente — tudo passa pelo barrel export em `@/lib/shared`.

## 2. User Stories

### P1: Contexto de Autenticação + Hook useAuth ⭐ MVP

**User Story:** As a user, I want to stay authenticated across page reloads and have my session automatically refreshed so that I don't need to re-login every 15 minutes.

**Acceptance Criteria:**
1. WHEN app initializes THEN AuthContext SHALL call `POST /api/v1/auth/refresh` with `credentials: 'include'` to restore session silently
2. WHEN refresh succeeds THEN context SHALL store the new JWT + user profile from response
3. WHEN refresh fails (401/no cookie) THEN context SHALL remain in `unauthenticated` state, no redirect
4. WHEN user calls `login(email, password)` THEN context SHALL call `POST /api/v1/auth/login` and store JWT + user
5. WHEN user calls `logout()` THEN context SHALL call `POST /api/v1/auth/logout` and clear state
6. WHEN JWT expires during normal usage THEN API client interceptor SHALL attempt refresh and retry the original request once
7. WHEN refresh also fails (401) THEN context SHALL clear user and redirect to `/login`
8. WHEN user profile is fetched via `GET /api/v1/users/me` THEN context SHALL update stored user

**Independent Test:** Mock `POST /api/v1/auth/refresh` returning a valid token. Render `AuthProvider` wrapping a component that calls `useAuth()`. Assert `user` is populated after mount.

---

### P1: API Client com Interceptor JWT ⭐ MVP

**User Story:** As a developer, I want a thin fetch wrapper that automatically injects the Authorization header, handles 401 with retry, and parses API errors into typed objects so that every page doesn't need its own error handling.

**Acceptance Criteria:**
1. WHEN making any authenticated request THEN client SHALL add `Authorization: Bearer <token>` header
2. WHEN API returns 401 AND a refresh is possible THEN client SHALL call `/api/v1/auth/refresh` and retry the original request exactly once
3. WHEN API returns 4xx/5xx THEN client SHALL parse the `errors[]` array from response body and throw `ApiError`
4. WHEN `Idempotency-Key` header is required THEN client SHALL auto-generate a UUID v4 if not explicitly provided
5. WHEN `X-Merchant-Id` header is required THEN client SHALL accept it via options parameter
6. WHEN network fails (no connection, DNS, timeout) THEN client SHALL throw `ApiError` with code `NETWORK_ERROR` and `retryable: true`
7. WHEN the request includes a body THEN client SHALL auto-set `Content-Type: application/json`

**Independent Test:** Mock a 401 response, assert the client calls refresh endpoint then retries the original request. Assert the final response is returned to the caller.

---

### P1: Route Guards — ProtectedRoute & GuestRoute ⭐ MVP

**User Story:** As a developer, I want route guards that redirect unauthenticated users to login and authenticated users away from login so that the app enforces access rules declaratively.

**Acceptance Criteria:**
1. WHEN unauthenticated user visits a protected path (`/orders`, `/checkout`, `/merchant`) THEN `ProtectedRoute` SHALL redirect to `/login`
2. WHEN authenticated user visits `/login` or `/register` THEN `GuestRoute` SHALL redirect to `/dashboard`
3. WHEN auth state is loading (refresh in progress) THEN both guards SHALL render `<Spinner />` instead of redirecting
4. AFTER auth state resolves THEN the guard SHALL evaluate the rule and redirect accordingly

**Independent Test:** Render `ProtectedRoute` wrapping `<div>protected</div>` outside `AuthProvider` with `unauthenticated` state. Assert `Navigate` to `/login` is rendered.

---

### P1: Layouts — AuthLayout & AppLayout ⭐ MVP

**User Story:** As a designer, I want two layout shells that enforce the approved color palette and structure so that all pages have consistent branding.

**Acceptance Criteria:**
1. AuthLayout SHALL render with background `bg-[#FEFCF5]` and a centered card container with white background and shadow
2. AuthLayout SHALL render children (form content) inside the centered card
3. AppLayout SHALL render a fixed top header with background `bg-[#5B8DEE]` and white text displaying the app name and user avatar/logout
4. AppLayout SHALL render children inside a `<main>` content area below the header with padding
5. Both layouts SHALL be responsive (desktop-first, min-width 1024px)

**Independent Test:** Render `AuthLayout` with children text, assert background color class and centered card structure. Render `AppLayout`, assert header color and main content area.

---

### P1: UI Components — Button, Input, Spinner, ErrorMessage ⭐ MVP

**User Story:** As a developer, I want reusable UI components that follow the approved design system so that I can build pages quickly without reinventing styles.

**Acceptance Criteria:**
1. `Button` SHALL render a `<button>` with `bg-[#5B8DEE] text-white` classes and accept `variant` prop (`primary` | `secondary` | `danger` | `ghost`)
2. `Button` SHALL show a `<Spinner>` when `loading` prop is `true` and disable the button
3. `Input` SHALL render a `<label>`, an `<input>` with placeholder, and an optional error message in red below the field
4. `Input` SHALL pass through `type`, `placeholder`, `value`, `onChange` props to the native input
5. `Spinner` SHALL render an animated SVG circle (rotate animation via Tailwind `animate-spin`) with configurable `size` (`sm` | `md` | `lg`)
6. `ErrorMessage` SHALL render a red-tinted card with an error icon, a `title` string, and a `message` string, plus an optional `onRetry` callback that renders a "Tentar novamente" button

**Independent Test:** Render `Button` with `loading={true}`, assert the spinner appears and button is disabled. Render `Input` with `error="Campo obrigatório"`, assert error text is visible.

---

### P2: Paleta Visual — Testes de Configuração e Contraste ⭐

**User Story:** As a QA engineer, I want automated tests that verify the Tailwind config tokens match the approved palette and that text/background color pairs meet WCAG AA contrast so that design consistency is enforced in CI.

**Acceptance Criteria:**
1. Tailwind config SHALL define custom colors `primary` (`#5B8DEE`), `cream` (`#FEFCF5`), `dark` (`#1A1A2E`), and all secondary variants matching the approved palette
2. Contrast ratio between `primary` bg + `white` text SHALL be ≥ 4.5:1 (WCAG AA for normal text)
3. Contrast ratio between `dark` (#1A1A2E) text on `cream` (#FEFCF5) bg SHALL be ≥ 4.5:1
4. Components SHALL use Tailwind semantic classes (`bg-primary`, `text-dark`, `bg-cream`) — never raw hex values
5. All palette hex values SHALL be defined as constants in a single source-of-truth file and tested against expected values

**Independent Test:** Import Tailwind config resolved colors, assert `primary === '#5B8DEE'`. Compute contrast ratio for `primary`/`white` with a WCAG contrast function, assert ≥ 4.5:1.

---

## 3. Interface Types (TypeScript)

```typescript
// ─── Enums ──────────────────────────────────────────────
export type UserRole = 'CUSTOMER' | 'MERCHANT_OWNER';

// ─── Domain Types ───────────────────────────────────────
export interface User {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  merchantId?: string;
  emailVerified: boolean;
  twoFactorEnabled: boolean;
  createdAt: string; // ISO 8601
}

export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number; // seconds
  user: User;
}

export interface ApiErrorDetail {
  code: string;
  message: string;
  retryable: boolean;
}

export class ApiError extends Error {
  public readonly status: number;
  public readonly errors: ApiErrorDetail[];
  public readonly requestId?: string;
  constructor(status: number, errors: ApiErrorDetail[], requestId?: string);
}

export interface PaginatedResponse<T> {
  data: T[];
  meta: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    timestamp: string;
    requestId: string;
  };
  errors: [];
}

// ─── API Client ─────────────────────────────────────────
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface RequestOptions {
  method?: HttpMethod;
  body?: unknown;
  headers?: Record<string, string>;
  params?: Record<string, string | number | boolean | undefined>;
  idempotencyKey?: string;
  merchantId?: string;
  signal?: AbortSignal;
}

export interface IApiClient {
  request<T>(url: string, options?: RequestOptions): Promise<T>;
  get<T>(url: string, options?: RequestOptions): Promise<T>;
  post<T>(url: string, body?: unknown, options?: RequestOptions): Promise<T>;
  put<T>(url: string, body?: unknown, options?: RequestOptions): Promise<T>;
  patch<T>(url: string, body?: unknown, options?: RequestOptions): Promise<T>;
  delete<T>(url: string, options?: RequestOptions): Promise<T>;
}

// ─── Auth Context ───────────────────────────────────────
export interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

export interface IAuthContext extends AuthState {
  login: (email: string, password: string, totpCode?: string) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
}

// ─── Route Guards Props ─────────────────────────────────
export interface ProtectedRouteProps {
  children: React.ReactNode;
  requiredRole?: UserRole;
}

export interface GuestRouteProps {
  children: React.ReactNode;
}

// ─── UI Component Props ─────────────────────────────────
export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';
export type SpinnerSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  loading?: boolean;
  fullWidth?: boolean;
}

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
}

export interface ErrorMessageProps {
  title: string;
  message: string;
  onRetry?: () => void;
}

// ─── Layout Props ───────────────────────────────────────
export interface LayoutProps {
  children: React.ReactNode;
}
```

## 4. API Endpoints Consumidos

| Endpoint | Método | Headers | Request | Response |
|----------|--------|---------|---------|----------|
| `/api/v1/auth/login` | POST | — | `{email, password, totpCode?}` | `AuthResponse` |
| `/api/v1/auth/refresh` | POST | Cookie `refreshToken` (httpOnly) | — | `AuthResponse` |
| `/api/v1/auth/logout` | POST | `Authorization: Bearer <token>` | — | `{data: null, errors: []}` |
| `/api/v1/users/me` | GET | `Authorization: Bearer <token>` | — | `{data: User, errors: []}` |

## 5. Mock Contracts

Esta área é a fundação — nenhuma dependência de mock de outras áreas.

**Outras áreas mockam esta área:**

```typescript
// Mock que outras áreas DEVEM implementar para isolar AuthContext:
export interface IMockUseAuth {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: Mock;
  logout: Mock;
}

// Mock que outras áreas DEVEM implementar para isolar API Client:
export interface IMockApiClient {
  get: Mock;
  post: Mock;
  put: Mock;
  patch: Mock;
  delete: Mock;
  request: Mock;
}
```

## 6. Error Scenarios

| Erro | Código | Comportamento esperado |
|------|--------|----------------------|
| Refresh token ausente/expirado | `TOKEN_EXPIRED` | AuthContext permanece `unauthenticated`, sem redirect |
| JWT expirado durante requisição | 401 | API client tenta refresh uma vez; se falhar, redireciona para `/login` |
| Rede indisponível | `NETWORK_ERROR` | `ApiError` com `retryable: true`, `ErrorMessage` mostra "Tentar novamente" |
| Validação de formulário | `VALIDATION_ERROR` | `errors[]` array exibido no formulário (cada Input mostra seu erro) |
| Credenciais inválidas | `INVALID_CREDENTIALS` | Login retorna 401, `ErrorMessage` exibe título "Erro de autenticação" |
| 2FA necessário mas não fornecido | `TWO_FACTOR_REQUIRED` | Login retorna 200 com `twoFactorRequired: true`, frontend redireciona para tela de 2FA |
| Rate limit excedido | `RATE_LIMIT_EXCEEDED` | `ApiError` com `retryable: true`, `ErrorMessage` com retry e aviso de aguardar |
| Idempotency Key duplicada | `DUPLICATE_REQUEST` | `ApiError` sem retry, mensagem "Esta operação já foi processada" |

## 7. Requirement Traceability

| ID | Story | Status |
|----|-------|--------|
| SHARED-01 | P1: AuthContext carrega sessão ao iniciar | Pending |
| SHARED-02 | P1: AuthContext armazena JWT + user após login bem-sucedido | Pending |
| SHARED-03 | P1: AuthContext faz refresh automático via cookie httpOnly | Pending |
| SHARED-04 | P1: AuthContext limpa estado e redireciona se refresh falhar | Pending |
| SHARED-05 | P1: API Client injeta `Authorization: Bearer` header | Pending |
| SHARED-06 | P1: API Client tenta refresh e retry em 401 | Pending |
| SHARED-07 | P1: API Client parseia `errors[]` e lança `ApiError` | Pending |
| SHARED-08 | P1: API Client gera Idempotency-Key UUID automático | Pending |
| SHARED-09 | P1: ProtectedRoute redireciona não autenticado para `/login` | Pending |
| SHARED-10 | P1: GuestRoute redireciona autenticado para `/dashboard` | Pending |
| SHARED-11 | P1: AuthLayout com fundo cream (`#FEFCF5`) e card centralizado | Pending |
| SHARED-12 | P1: AppLayout com header azul (`#5B8DEE`) e área main | Pending |
| SHARED-13 | P1: Button com bg-primary, white text, variantes e loading state | Pending |
| SHARED-14 | P1: Input com label, placeholder e exibição de erro | Pending |
| SHARED-15 | P1: Spinner animado com tamanhos configuráveis | Pending |
| SHARED-16 | P1: ErrorMessage com título, mensagem e botão retry | Pending |
| SHARED-17 | P2: Paleta Tailwind config coincide com hex aprovados | Pending |
| SHARED-18 | P2: Contraste WCAG AA ≥ 4.5:1 para pares texto/fundo | Pending |
| SHARED-19 | P2: Componentes usam classes Tailwind semânticas, nunca raw hex | Pending |
