import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { TwoFactorSetupPage } from './TwoFactorSetupPage';
import type { IApiClient, IAuthContext, ApiResponse, TwoFactorSetupResponse } from '@/types/auth';

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

function successResponse(data: TwoFactorSetupResponse): ApiResponse<TwoFactorSetupResponse> {
  return { data, meta: { timestamp: '', requestId: '' }, errors: [] };
}

const mockSetupData: TwoFactorSetupResponse = {
  secret: 'JBSWY3DPEHPK3PXP',
  qrCodeUrl: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUg...',
  otpAuthUrl: 'otpauth://totp/AcabouMony:user@test.com?secret=JBSWY3DPEHPK3PXP&issuer=AcabouMony',
  recoveryCodes: [
    'CODE-1AAAAA',
    'CODE-2BBBBB',
    'CODE-3CCCCC',
    'CODE-4DDDDD',
    'CODE-5EEEEE',
    'CODE-6FFFFF',
    'CODE-7GGGGG',
    'CODE-8HHHHH',
  ],
};

describe('TwoFactorSetupPage', () => {
  beforeEach(() => {
    Object.assign(navigator, {
      clipboard: { writeText: vi.fn().mockResolvedValue(undefined) },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('calls setup API on mount and displays QR code, secret, and recovery codes', async () => {
    const mockPost = vi.fn().mockResolvedValue(successResponse(mockSetupData));
    const apiClient = createMockApiClient({ post: mockPost });

    render(<TwoFactorSetupPage apiClient={apiClient} authContext={createMockAuthContext()} />);

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith('/api/v1/auth/2fa/setup');
    });

    await waitFor(() => {
      expect(screen.getByAltText('QR Code 2FA')).toBeInTheDocument();
    });

    expect(screen.getByText('JBSWY3DPEHPK3PXP')).toBeInTheDocument();
    expect(screen.getByText('Copiar')).toBeInTheDocument();

    expect(screen.getByText('CODE-1AAAAA')).toBeInTheDocument();
    expect(screen.getByText('CODE-8HHHHH')).toBeInTheDocument();
  });

  it('shows warning about saving recovery codes', async () => {
    const mockPost = vi.fn().mockResolvedValue(successResponse(mockSetupData));
    const apiClient = createMockApiClient({ post: mockPost });

    render(<TwoFactorSetupPage apiClient={apiClient} authContext={createMockAuthContext()} />);

    await waitFor(() => {
      expect(screen.getByText('Salve estes códigos em um local seguro. Eles não serão exibidos novamente.')).toBeInTheDocument();
    });
  });

  it('submits TOTP confirmation and redirects to security on success', async () => {
    const navigate = vi.fn();
    const mockPost = vi.fn()
      .mockResolvedValueOnce(successResponse(mockSetupData))
      .mockResolvedValueOnce({ data: { message: '2FA ativado' }, meta: { timestamp: '', requestId: '' }, errors: [] });
    const apiClient = createMockApiClient({ post: mockPost });

    render(<TwoFactorSetupPage apiClient={apiClient} authContext={createMockAuthContext()} navigate={navigate} />);

    await waitFor(() => {
      expect(screen.getByAltText('QR Code 2FA')).toBeInTheDocument();
    });

    const confirmInput = screen.getByLabelText('Código TOTP');
    fireEvent.change(confirmInput, { target: { value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }));

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith(
        '/api/v1/auth/2fa/confirm',
        { code: '123456' }
      );
    });

    await waitFor(() => {
      expect(screen.getByText('2FA ativado com sucesso! Redirecionando...')).toBeInTheDocument();
    }, { timeout: 3000 });
  });

  it('shows error for invalid confirmation code', async () => {
    const mockPost = vi.fn()
      .mockResolvedValueOnce(successResponse(mockSetupData))
      .mockResolvedValueOnce({
        data: null,
        meta: { timestamp: '', requestId: '' },
        errors: [{ code: 'INVALID_TOTP_CODE', message: 'Código inválido', retryable: false }],
      });
    const apiClient = createMockApiClient({ post: mockPost });

    render(<TwoFactorSetupPage apiClient={apiClient} authContext={createMockAuthContext()} />);

    await waitFor(() => {
      expect(screen.getByAltText('QR Code 2FA')).toBeInTheDocument();
    });

    const confirmInput = screen.getByLabelText('Código TOTP');
    fireEvent.change(confirmInput, { target: { value: '000000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }));

    await waitFor(() => {
      expect(screen.getByText('Código inválido. Tente novamente.')).toBeInTheDocument();
    });
  });

  it('copies secret key to clipboard', async () => {
    const mockPost = vi.fn().mockResolvedValue(successResponse(mockSetupData));
    const apiClient = createMockApiClient({ post: mockPost });

    render(<TwoFactorSetupPage apiClient={apiClient} authContext={createMockAuthContext()} />);

    await waitFor(() => {
      expect(screen.getByText('JBSWY3DPEHPK3PXP')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Copiar'));

    await waitFor(() => {
      expect(screen.getByText('Copiado!')).toBeInTheDocument();
    });
  });
});
