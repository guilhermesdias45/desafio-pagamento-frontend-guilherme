import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ConfirmEmailPage } from './ConfirmEmailPage';
import type { IApiClient, ApiResponse } from '@/types/auth';

function createMockApiClient(overrides?: Partial<IApiClient>): IApiClient {
  return {
    post: vi.fn(),
    get: vi.fn(),
    patch: vi.fn(),
    ...overrides,
  };
}

function successResponse(): ApiResponse<{ message: string }> {
  return { data: { message: 'Email confirmado' }, meta: { timestamp: '', requestId: '' }, errors: [] };
}

function errorResponse(code: string): ApiResponse<null> {
  return { data: null, meta: { timestamp: '', requestId: '' }, errors: [{ code, message: code, retryable: false }] };
}

describe('ConfirmEmailPage', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders form with email and token inputs when no token in URL', () => {
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      ...window.location,
      search: '?email=user%40test.com',
    } as Location);

    const apiClient = createMockApiClient();
    render(<ConfirmEmailPage apiClient={apiClient} />);

    expect(screen.getByLabelText('Email')).toHaveValue('user@test.com');
    expect(screen.getByLabelText('Token de Confirmação')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirmar' })).toBeInTheDocument();
  });

  it('auto-submits when both email and token are in URL', async () => {
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      ...window.location,
      search: '?email=user%40test.com&token=abc123token',
    } as Location);

    const navigate = vi.fn();
    const mockPost = vi.fn().mockResolvedValue(successResponse());
    const apiClient = createMockApiClient({ post: mockPost });

    render(<ConfirmEmailPage apiClient={apiClient} navigate={navigate} />);

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith(
        '/api/v1/auth/confirm-email',
        { email: 'user@test.com', token: 'abc123token' }
      );
    });
  });

  it('shows success message and redirects to login', async () => {
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      ...window.location,
      search: '?email=user%40test.com&token=abc123token',
    } as Location);

    const navigate = vi.fn();
    const mockPost = vi.fn().mockResolvedValue(successResponse());
    const apiClient = createMockApiClient({ post: mockPost });

    render(<ConfirmEmailPage apiClient={apiClient} navigate={navigate} />);

    await waitFor(() => {
      expect(screen.getByText('Email confirmado! Faça seu login.')).toBeInTheDocument();
    });
  });

  it('shows error when token is invalid or expired', async () => {
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      ...window.location,
      search: '?email=user%40test.com&token=badtoken',
    } as Location);

    const mockPost = vi.fn().mockResolvedValue(errorResponse('TOKEN_INVALID'));
    const apiClient = createMockApiClient({ post: mockPost });

    render(<ConfirmEmailPage apiClient={apiClient} />);

    await waitFor(() => {
      expect(screen.getByText('Token inválido ou expirado. Solicite um novo link de confirmação.')).toBeInTheDocument();
    });
  });

  it('validates that token is required in manual form', async () => {
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      ...window.location,
      search: '?email=user%40test.com',
    } as Location);

    const apiClient = createMockApiClient();
    render(<ConfirmEmailPage apiClient={apiClient} />);

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }));

    await waitFor(() => {
      expect(screen.getByText('Token é obrigatório')).toBeInTheDocument();
    });
  });

  it('submits manual form with token and succeeds', async () => {
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      ...window.location,
      search: '?email=user%40test.com',
    } as Location);

    const navigate = vi.fn();
    const mockPost = vi.fn().mockResolvedValue(successResponse());
    const apiClient = createMockApiClient({ post: mockPost });

    render(<ConfirmEmailPage apiClient={apiClient} navigate={navigate} />);

    fireEvent.change(screen.getByLabelText('Token de Confirmação'), { target: { value: 'manualtoken123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }));

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith(
        '/api/v1/auth/confirm-email',
        { email: 'user@test.com', token: 'manualtoken123' }
      );
    });
  });
});
