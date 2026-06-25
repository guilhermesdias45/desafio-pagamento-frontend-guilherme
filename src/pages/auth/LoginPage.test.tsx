import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { LoginPage } from './LoginPage';
import type { IApiClient, IAuthContext, ApiResponse, LoginResponse, AuthUser } from '@/types/auth';

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

function successLogin(data: LoginResponse): ApiResponse<LoginResponse> {
  return { data, meta: { timestamp: '', requestId: '' }, errors: [] };
}

function errorLogin(code: string, message?: string): ApiResponse<null> {
  return { data: null, meta: { timestamp: '', requestId: '' }, errors: [{ code, message: message || code, retryable: false }] };
}

describe('LoginPage', () => {
  it('renders login form with email and password fields', () => {
    const apiClient = createMockApiClient();
    const authContext = createMockAuthContext();
    render(<LoginPage apiClient={apiClient} authContext={authContext} />);

    expect(screen.getByLabelText('Email')).toBeInTheDocument();
    expect(screen.getByLabelText('Senha')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeInTheDocument();
  });

  it('stores token and redirects to / on successful login without 2FA', async () => {
    const navigate = vi.fn();
    const mockLogin = vi.fn();
    const authContext = createMockAuthContext({ login: mockLogin });
    const mockPost = vi.fn().mockResolvedValue(
      successLogin({ accessToken: 'token123', tokenType: 'Bearer', expiresIn: 900, requiresTwoFactor: false })
    );
    const apiClient = createMockApiClient({ post: mockPost });

    render(<LoginPage apiClient={apiClient} authContext={authContext} navigate={navigate} />);

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'user@test.com' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'Senha123!' } });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith(
        '/api/v1/auth/login',
        { email: 'user@test.com', password: 'Senha123!' }
      );
    });

    expect(mockLogin).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('redirects to 2FA verify page when 2FA is enabled', async () => {
    const navigate = vi.fn();
    const authContext = createMockAuthContext();
    const mockPost = vi.fn().mockResolvedValue(
      successLogin({ requiresTwoFactor: true, twoFactorToken: '2fa_token_abc' } as LoginResponse)
    );
    const apiClient = createMockApiClient({ post: mockPost });

    render(<LoginPage apiClient={apiClient} authContext={authContext} navigate={navigate} />);

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'user@test.com' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'Senha123!' } });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => {
      expect(navigate).toHaveBeenCalledWith('/2fa/verify?token=2fa_token_abc&email=user%40test.com');
    });
  });

  it('shows INVALID_CREDENTIALS error', async () => {
    const apiClient = createMockApiClient({
      post: vi.fn().mockResolvedValue(errorLogin('INVALID_CREDENTIALS', 'Credenciais inválidas')),
    });
    render(<LoginPage apiClient={apiClient} authContext={createMockAuthContext()} />);

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'user@test.com' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'wrong' } });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => {
      expect(screen.getByText('Email ou senha inválidos')).toBeInTheDocument();
    });
  });

  it('shows ACCOUNT_LOCKED error with unlock time', async () => {
    const unlockDate = new Date('2026-06-22T15:30:00Z');
    const apiClient = createMockApiClient({
      post: vi.fn().mockResolvedValue(errorLogin('ACCOUNT_LOCKED', unlockDate.toISOString())),
    });
    render(<LoginPage apiClient={apiClient} authContext={createMockAuthContext()} />);

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'user@test.com' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'Senha123!' } });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => {
      const time = unlockDate.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
      expect(screen.getByText(`Conta bloqueada até ${time}`)).toBeInTheDocument();
    });
  });

  it('shows ACCOUNT_NOT_CONFIRMED error with link', async () => {
    const apiClient = createMockApiClient({
      post: vi.fn().mockResolvedValue(errorLogin('ACCOUNT_NOT_CONFIRMED', 'Email não confirmado')),
    });
    render(<LoginPage apiClient={apiClient} authContext={createMockAuthContext()} />);

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'user@test.com' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'Senha123!' } });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => {
      expect(screen.getByText('Confirme seu email antes de fazer login')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Confirmar email' })).toHaveAttribute(
        'href',
        '/confirm-email?email=user%40test.com'
      );
    });
  });

  it('shows ACCOUNT_DISABLED error', async () => {
    const apiClient = createMockApiClient({
      post: vi.fn().mockResolvedValue(errorLogin('ACCOUNT_DISABLED', 'Conta desativada')),
    });
    render(<LoginPage apiClient={apiClient} authContext={createMockAuthContext()} />);

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'user@test.com' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'Senha123!' } });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => {
      expect(screen.getByText('Conta desativada. Entre em contato com o suporte.')).toBeInTheDocument();
    });
  });

  it('shows TOO_MANY_REQUESTS error and disables button', async () => {
    const apiClient = createMockApiClient({
      post: vi.fn().mockResolvedValue(errorLogin('TOO_MANY_REQUESTS', 'Muitas tentativas')),
    });
    render(<LoginPage apiClient={apiClient} authContext={createMockAuthContext()} />);

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'user@test.com' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'Senha123!' } });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => {
      expect(screen.getByText('Muitas tentativas. Aguarde alguns minutos.')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Entrar' })).toBeDisabled();
    });
  });
});
