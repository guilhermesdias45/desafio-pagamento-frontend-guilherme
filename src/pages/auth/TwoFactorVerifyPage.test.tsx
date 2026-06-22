import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { TwoFactorVerifyPage } from './TwoFactorVerifyPage';
import type { IApiClient, IAuthContext, ApiResponse, LoginSuccessResponse } from '@/types/auth';

function createMockApiClient(overrides?: Partial<IApiClient>): IApiClient {
  return {
    post: vi.fn(),
    get: vi.fn(),
    patch: vi.fn(),
    ...overrides,
  };
}

function createMockAuthContext(overrides?: Partial<IAuthContext>): IAuthContext {
  return {
    user: null,
    accessToken: null,
    isAuthenticated: false,
    isMerchant: false,
    login: vi.fn(),
    logout: vi.fn(),
    refreshToken: vi.fn(),
    loading: false,
    ...overrides,
  };
}

function successResponse(data: LoginSuccessResponse): ApiResponse<LoginSuccessResponse> {
  return { data, meta: { timestamp: '', requestId: '' }, errors: [] };
}

function errorResponse(code: string): ApiResponse<null> {
  return { data: null, meta: { timestamp: '', requestId: '' }, errors: [{ code, message: code, retryable: false }] };
}

describe('TwoFactorVerifyPage', () => {
  beforeEach(() => {
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      ...window.location,
      search: '?token=fake_token_123&email=user%40test.com',
    } as Location);
  });

  it('renders TOTP code input', () => {
    const apiClient = createMockApiClient();
    const authContext = createMockAuthContext();
    render(
      <TwoFactorVerifyPage
        apiClient={apiClient}
        authContext={authContext}
        twoFactorToken="abc"
        email="user@test.com"
      />
    );

    expect(screen.getByLabelText('Código TOTP (6 dígitos)')).toBeInTheDocument();
    expect(screen.getByText('Usar código de recuperação')).toBeInTheDocument();
  });

  it('accepts valid 6-digit TOTP code and completes login', async () => {
    const navigate = vi.fn();
    const mockLogin = vi.fn();
    const authContext = createMockAuthContext({ login: mockLogin });
    const mockPost = vi.fn().mockResolvedValue(
      successResponse({ accessToken: 'token_2fa', tokenType: 'Bearer', expiresIn: 900, requiresTwoFactor: false })
    );
    const apiClient = createMockApiClient({ post: mockPost });

    render(
      <TwoFactorVerifyPage
        apiClient={apiClient}
        authContext={authContext}
        navigate={navigate}
        twoFactorToken="abc123"
        email="user@test.com"
      />
    );

    const input = screen.getByLabelText('Código TOTP (6 dígitos)');
    fireEvent.change(input, { target: { value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: 'Verificar' }));

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith(
        '/api/v1/auth/2fa/verify',
        { twoFactorToken: 'abc123', totpCode: '123456' }
      );
    });

    expect(mockLogin).toHaveBeenCalledWith('token_2fa', expect.any(Object));
    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('shows error for invalid TOTP code', async () => {
    const apiClient = createMockApiClient({
      post: vi.fn().mockResolvedValue(errorResponse('INVALID_TOTP_CODE')),
    });

    render(
      <TwoFactorVerifyPage
        apiClient={apiClient}
        authContext={createMockAuthContext()}
        twoFactorToken="abc"
        email="user@test.com"
      />
    );

    const input = screen.getByLabelText('Código TOTP (6 dígitos)');
    fireEvent.change(input, { target: { value: '000000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Verificar' }));

    await waitFor(() => {
      expect(screen.getByText('Código inválido ou expirado')).toBeInTheDocument();
    });
  });

  it('toggles to recovery code mode', () => {
    const apiClient = createMockApiClient();
    render(
      <TwoFactorVerifyPage
        apiClient={apiClient}
        authContext={createMockAuthContext()}
        twoFactorToken="abc"
        email="user@test.com"
      />
    );

    fireEvent.click(screen.getByText('Usar código de recuperação'));

    expect(screen.getByLabelText('Código de Recuperação')).toBeInTheDocument();
    expect(screen.getByText('Usar código TOTP')).toBeInTheDocument();
  });

  it('submits recovery code and completes login', async () => {
    const navigate = vi.fn();
    const mockLogin = vi.fn();
    const authContext = createMockAuthContext({ login: mockLogin });
    const mockPost = vi.fn().mockResolvedValue(
      successResponse({ accessToken: 'token_recovery', tokenType: 'Bearer', expiresIn: 900, requiresTwoFactor: false })
    );
    const apiClient = createMockApiClient({ post: mockPost });

    render(
      <TwoFactorVerifyPage
        apiClient={apiClient}
        authContext={authContext}
        navigate={navigate}
        twoFactorToken="abc"
        email="user@test.com"
      />
    );

    fireEvent.click(screen.getByText('Usar código de recuperação'));

    const recoveryInput = screen.getByLabelText('Código de Recuperação');
    fireEvent.change(recoveryInput, { target: { value: 'RECOV-1234-CODE' } });
    fireEvent.click(screen.getByRole('button', { name: 'Verificar' }));

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith(
        '/api/v1/auth/2fa/recovery',
        { email: 'user@test.com', recoveryCode: 'RECOV-1234-CODE' }
      );
    });

    expect(mockLogin).toHaveBeenCalledWith('token_recovery', expect.any(Object));
    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('shows error for invalid recovery code', async () => {
    const apiClient = createMockApiClient({
      post: vi.fn().mockResolvedValue(errorResponse('RECOVERY_CODE_INVALID')),
    });

    render(
      <TwoFactorVerifyPage
        apiClient={apiClient}
        authContext={createMockAuthContext()}
        twoFactorToken="abc"
        email="user@test.com"
      />
    );

    fireEvent.click(screen.getByText('Usar código de recuperação'));

    fireEvent.change(screen.getByLabelText('Código de Recuperação'), { target: { value: 'INVALID' } });
    fireEvent.click(screen.getByRole('button', { name: 'Verificar' }));

    await waitFor(() => {
      expect(screen.getByText('Código de recuperação inválido')).toBeInTheDocument();
    });
  });

  it('shows exhausted recovery codes message', async () => {
    const apiClient = createMockApiClient({
      post: vi.fn().mockResolvedValue(errorResponse('RECOVERY_CODE_EXHAUSTED')),
    });

    render(
      <TwoFactorVerifyPage
        apiClient={apiClient}
        authContext={createMockAuthContext()}
        twoFactorToken="abc"
        email="user@test.com"
      />
    );

    fireEvent.click(screen.getByText('Usar código de recuperação'));

    fireEvent.change(screen.getByLabelText('Código de Recuperação'), { target: { value: 'EXHAUSTED' } });
    fireEvent.click(screen.getByRole('button', { name: 'Verificar' }));

    await waitFor(() => {
      expect(screen.getByText('Todos os códigos de recuperação foram usados. Reconfigure o 2FA.')).toBeInTheDocument();
    });
  });
});
