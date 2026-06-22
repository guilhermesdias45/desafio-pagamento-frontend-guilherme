import type {
  IApiClient,
  RegisterRequest,
  LoginRequest,
  TwoFactorVerifyRequest,
  TwoFactorRecoveryRequest,
  TwoFactorConfirmRequest,
  ConfirmEmailRequest,
  RegisterResponse,
  LoginResponse,
  LoginSuccessResponse,
  TwoFactorSetupResponse,
} from '@/types/auth';

export function register(apiClient: IApiClient, data: RegisterRequest) {
  return apiClient.post<RegisterRequest, RegisterResponse>('/api/v1/auth/register', data);
}

export function login(apiClient: IApiClient, data: LoginRequest) {
  return apiClient.post<LoginRequest, LoginResponse>('/api/v1/auth/login', data);
}

export function confirmEmail(apiClient: IApiClient, data: ConfirmEmailRequest) {
  return apiClient.post<ConfirmEmailRequest, { message: string }>('/api/v1/auth/confirm-email', data);
}

export function setup2FA(apiClient: IApiClient) {
  return apiClient.post<void, TwoFactorSetupResponse>('/api/v1/auth/2fa/setup');
}

export function confirm2FA(apiClient: IApiClient, data: TwoFactorConfirmRequest) {
  return apiClient.post<TwoFactorConfirmRequest, { message: string }>('/api/v1/auth/2fa/confirm', data);
}

export function verify2FA(apiClient: IApiClient, data: TwoFactorVerifyRequest) {
  return apiClient.post<TwoFactorVerifyRequest, LoginSuccessResponse>('/api/v1/auth/2fa/verify', data);
}

export function recover2FA(apiClient: IApiClient, data: TwoFactorRecoveryRequest) {
  return apiClient.post<TwoFactorRecoveryRequest, LoginSuccessResponse>('/api/v1/auth/2fa/recovery', data);
}
