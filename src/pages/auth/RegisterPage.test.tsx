import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { RegisterPage } from './RegisterPage';
import type { IApiClient, ApiResponse, RegisterResponse, ApiError } from '@/types/auth';

function createMockApiClient(overrides?: Partial<IApiClient>): IApiClient {
  return {
    post: vi.fn(),
    get: vi.fn(),
    patch: vi.fn(),
    ...overrides,
  };
}

function successResponse<T>(data: T): ApiResponse<T> {
  return { data, meta: { timestamp: '', requestId: '' }, errors: [] };
}

function errorResponse(code: string): ApiResponse<null> {
  return { data: null, meta: { timestamp: '', requestId: '' }, errors: [{ code, message: code, retryable: false }] };
}

describe('RegisterPage', () => {
  it('renders registration form with all fields for CUSTOMER role', () => {
    const apiClient = createMockApiClient();
    const navigate = vi.fn();
    render(<RegisterPage apiClient={apiClient} navigate={navigate} />);

    expect(screen.getByLabelText('Email')).toBeInTheDocument();
    expect(screen.getByLabelText('Senha')).toBeInTheDocument();
    expect(screen.getByLabelText('Nome Completo')).toBeInTheDocument();
    expect(screen.getByLabelText('Tipo de Conta')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cadastrar' })).toBeInTheDocument();
  });

  it('shows merchant fields when MERCHANT_OWNER is selected', () => {
    const apiClient = createMockApiClient();
    render(<RegisterPage apiClient={apiClient} />);

    const roleSelect = screen.getByLabelText('Tipo de Conta');
    fireEvent.change(roleSelect, { target: { value: 'MERCHANT_OWNER' } });

    expect(screen.getByLabelText('Nome da Empresa')).toBeInTheDocument();
    expect(screen.getByLabelText('CNPJ')).toBeInTheDocument();
  });

  it('masks CNPJ input correctly', () => {
    const apiClient = createMockApiClient();
    render(<RegisterPage apiClient={apiClient} />);

    fireEvent.change(screen.getByLabelText('Tipo de Conta'), { target: { value: 'MERCHANT_OWNER' } });

    const cnpjInput = screen.getByLabelText('CNPJ');
    fireEvent.change(cnpjInput, { target: { value: '12345678000195' } });

    expect(cnpjInput).toHaveValue('12.345.678/0001-95');
  });

  it('submits CUSTOMER registration and redirects on success', async () => {
    const mockPost = vi.fn().mockResolvedValue(successResponse({ userId: '1', email: 'test@test.com', role: 'CUSTOMER', merchantId: null, emailConfirmed: false } as RegisterResponse));
    const apiClient = createMockApiClient({ post: mockPost });
    const navigate = vi.fn();
    render(<RegisterPage apiClient={apiClient} navigate={navigate} />);

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'test@test.com' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'Senha123!' } });
    fireEvent.change(screen.getByLabelText('Nome Completo'), { target: { value: 'Test User' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cadastrar' }));

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith(
        '/api/v1/auth/register',
        { email: 'test@test.com', password: 'Senha123!', fullName: 'Test User', role: 'CUSTOMER' }
      );
    });

    expect(navigate).toHaveBeenCalledWith('/auth/confirm-email?email=test%40test.com');
  });

  it('submits MERCHANT_OWNER registration with company data', async () => {
    const mockPost = vi.fn().mockResolvedValue(successResponse({ userId: '2', email: 'merchant@test.com', role: 'MERCHANT_OWNER', merchantId: 'm1', emailConfirmed: false } as RegisterResponse));
    const apiClient = createMockApiClient({ post: mockPost });
    const navigate = vi.fn();
    render(<RegisterPage apiClient={apiClient} navigate={navigate} />);

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'merchant@test.com' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'Senha123!' } });
    fireEvent.change(screen.getByLabelText('Nome Completo'), { target: { value: 'Merchant User' } });
    fireEvent.change(screen.getByLabelText('Tipo de Conta'), { target: { value: 'MERCHANT_OWNER' } });
    fireEvent.change(screen.getByLabelText('Nome da Empresa'), { target: { value: 'Minha Loja' } });
    fireEvent.change(screen.getByLabelText('CNPJ'), { target: { value: '11222333000181' } });

    fireEvent.click(screen.getByRole('button', { name: 'Cadastrar' }));

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith(
        '/api/v1/auth/register',
        { email: 'merchant@test.com', password: 'Senha123!', fullName: 'Merchant User', role: 'MERCHANT_OWNER', companyName: 'Minha Loja', cnpj: '11222333000181' }
      );
    });
  });

  it('shows error for EMAIL_ALREADY_EXISTS', async () => {
    const mockPost = vi.fn().mockResolvedValue(errorResponse('EMAIL_ALREADY_EXISTS'));
    const apiClient = createMockApiClient({ post: mockPost });
    render(<RegisterPage apiClient={apiClient} />);

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'exists@test.com' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'Senha123!' } });
    fireEvent.change(screen.getByLabelText('Nome Completo'), { target: { value: 'Test' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cadastrar' }));

    await waitFor(() => {
      expect(screen.getByText('Este email já está cadastrado')).toBeInTheDocument();
    });
  });

  it('shows password requirements for WEAK_PASSWORD', async () => {
    const mockPost = vi.fn().mockResolvedValue(errorResponse('WEAK_PASSWORD'));
    const apiClient = createMockApiClient({ post: mockPost });
    render(<RegisterPage apiClient={apiClient} />);

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'test@test.com' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'abc' } });
    fireEvent.change(screen.getByLabelText('Nome Completo'), { target: { value: 'Test' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cadastrar' }));

    await waitFor(() => {
      expect(screen.getByText('Pelo menos 8 caracteres')).toBeInTheDocument();
    });
  });

  it('shows error for INVALID_CNPJ', async () => {
    const mockPost = vi.fn().mockResolvedValue(errorResponse('INVALID_CNPJ'));
    const apiClient = createMockApiClient({ post: mockPost });
    render(<RegisterPage apiClient={apiClient} />);

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'test@test.com' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'Senha123!' } });
    fireEvent.change(screen.getByLabelText('Nome Completo'), { target: { value: 'Test' } });
    fireEvent.change(screen.getByLabelText('Tipo de Conta'), { target: { value: 'MERCHANT_OWNER' } });
    fireEvent.change(screen.getByLabelText('Nome da Empresa'), { target: { value: 'Loja' } });
    fireEvent.change(screen.getByLabelText('CNPJ'), { target: { value: '11222333000181' } });

    fireEvent.click(screen.getByRole('button', { name: 'Cadastrar' }));

    await waitFor(() => {
      expect(screen.getByText('CNPJ inválido')).toBeInTheDocument();
    });
  });

  it('validates CNPJ format client-side and rejects invalid CNPJ', async () => {
    const apiClient = createMockApiClient();
    render(<RegisterPage apiClient={apiClient} />);

    fireEvent.change(screen.getByLabelText('Tipo de Conta'), { target: { value: 'MERCHANT_OWNER' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'test@test.com' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'Senha123!' } });
    fireEvent.change(screen.getByLabelText('Nome Completo'), { target: { value: 'Test' } });
    fireEvent.change(screen.getByLabelText('Nome da Empresa'), { target: { value: 'Loja' } });
    fireEvent.change(screen.getByLabelText('CNPJ'), { target: { value: '11.222.333/0001-00' } });

    fireEvent.click(screen.getByRole('button', { name: 'Cadastrar' }));

    await waitFor(() => {
      expect(screen.getByText('CNPJ inválido')).toBeInTheDocument();
    });
  });

  it('validates CNPJ check digits correctly', () => {
    const apiClient = createMockApiClient();
    render(<RegisterPage apiClient={apiClient} />);

    fireEvent.change(screen.getByLabelText('Tipo de Conta'), { target: { value: 'MERCHANT_OWNER' } });
    const cnpjInput = screen.getByLabelText('CNPJ');

    fireEvent.change(cnpjInput, { target: { value: '11222333000181' } });
    expect(cnpjInput).toHaveValue('11.222.333/0001-81');
  });
});
