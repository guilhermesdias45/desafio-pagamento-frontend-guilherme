export type UserRole = 'CUSTOMER' | 'MERCHANT_OWNER';

export interface AuthUser {
  userId: string;
  email: string;
  role: UserRole;
  merchantId: string | null;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  role: UserRole;
  companyName?: string;
  cnpj?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
  totpCode?: string;
  deviceFingerprint?: string;
}

export interface TwoFactorVerifyRequest {
  twoFactorToken: string;
  totpCode: string;
}

export interface TwoFactorRecoveryRequest {
  email: string;
  recoveryCode: string;
}

export interface TwoFactorConfirmRequest {
  code: string;
}

export interface ConfirmEmailRequest {
  email: string;
  token: string;
}

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
  expiresIn: 900;
  requiresTwoFactor: false;
}

export interface LoginRequiresTwoFactorResponse {
  requiresTwoFactor: true;
  twoFactorToken: string;
}

export type LoginResponse = LoginSuccessResponse | LoginRequiresTwoFactorResponse;

export interface TwoFactorSetupResponse {
  secret: string;
  qrCodeUrl: string;
  otpAuthUrl: string;
  recoveryCodes: string[];
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

export interface IApiClient {
  post<TReq, TRes>(url: string, body?: TReq, options?: RequestInit): Promise<ApiResponse<TRes>>;
  get<TRes>(url: string, options?: RequestInit): Promise<ApiResponse<TRes>>;
  patch<TReq, TRes>(url: string, body?: TReq): Promise<ApiResponse<TRes>>;
}

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
