import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CheckoutPage } from './CheckoutPage';

function createMockClient() {
  return {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  };
}

vi.mock('@/contexts/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'customer_123', email: 'test@test.com', fullName: 'Test', role: 'CUSTOMER' },
    token: 'test-token',
    isAuthenticated: true,
    isLoading: false,
  }),
  AuthProvider: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn(),
  useSearchParams: () => [new URLSearchParams('orderId=ord_001')],
}));

vi.mock('@/pages/checkout/CardForm', () => ({
  default: () => <div>CardForm Mock</div>,
}));

const mockOrder = {
  orderId: 'ord_001',
  customerId: 'customer_123',
  merchantId: 'merchant_1',
  status: 'PENDING',
  totalInCents: 35000,
  items: [
    { productId: 'prod_1', description: 'Item 1', quantity: 2, unitPriceInCents: 10000, subtotalInCents: 20000 },
    { productId: 'prod_2', description: 'Item 2', quantity: 1, unitPriceInCents: 15000, subtotalInCents: 15000 },
  ],
  transactionId: 'txn_001',
  createdAt: '2026-06-01T10:00:00Z',
  updatedAt: '2026-06-01T10:05:00Z',
  expiresAt: '2026-06-02T15:00:00Z',
};

describe('CheckoutPage', () => {
  let mockClient: ReturnType<typeof createMockClient>;

  beforeEach(() => {
    vi.clearAllMocks();
    mockClient = createMockClient();
  });

  it('shows loading spinner initially', () => {
    mockClient.get.mockReturnValue(new Promise(() => {}));
    render(<CheckoutPage apiClient={mockClient as never} />);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('renders order summary when order is loaded', async () => {
    mockClient.get.mockResolvedValue(mockOrder);

    render(<CheckoutPage apiClient={mockClient as never} />);

    await waitFor(() => {
      expect(screen.getByText('ord_001')).toBeInTheDocument();
      expect(screen.getByText(/R\s*\$\s*350,\s*00/i)).toBeInTheDocument();
      expect(screen.getByText('Item 1')).toBeInTheDocument();
      expect(screen.getByText('Item 2')).toBeInTheDocument();
    });

    expect(screen.getByText('Pendente')).toBeInTheDocument();
  });

  it('shows error message when order fetch fails', async () => {
    mockClient.get.mockRejectedValue(new Error('Network error'));

    render(<CheckoutPage apiClient={mockClient as never} />);

    await waitFor(() => {
      expect(screen.getByText('Falha na conexão com o servidor')).toBeInTheDocument();
    });

    expect(mockClient.get).toHaveBeenCalledWith('/api/v1/orders/ord_001');
  });

  it('shows ORDER_NOT_FOUND error', async () => {
    mockClient.get.mockRejectedValue({
      status: 404,
      errors: [{ code: 'ORDER_NOT_FOUND', message: 'Pedido não encontrado', retryable: false }],
    });

    render(<CheckoutPage apiClient={mockClient as never} />);

    await waitFor(() => {
      expect(screen.getByText('Pedido não encontrado')).toBeInTheDocument();
    });
  });

  it('renders CardForm when order is PENDING', async () => {
    mockClient.get.mockResolvedValue(mockOrder);

    render(<CheckoutPage apiClient={mockClient as never} />);

    await waitFor(() => {
      expect(screen.getByText('CardForm Mock')).toBeInTheDocument();
    });

    expect(screen.getByRole('button', { name: /Pagar/i })).toBeInTheDocument();
  });

  it('shows message when order is not PENDING', async () => {
    const paidOrder = { ...mockOrder, status: 'PAID' };
    mockClient.get.mockResolvedValue(paidOrder);

    render(<CheckoutPage apiClient={mockClient as never} />);

    await waitFor(() => {
      expect(screen.getByText('Este pedido não está mais pendente')).toBeInTheDocument();
    });

    expect(screen.queryByText('CardForm Mock')).not.toBeInTheDocument();
  });

  it('retries order fetch on retry', async () => {
    mockClient.get
      .mockRejectedValueOnce(new Error('Network error'))
      .mockResolvedValueOnce(mockOrder);

    render(<CheckoutPage apiClient={mockClient as never} />);

    await waitFor(() => {
      expect(screen.getByText('Tentar novamente')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Tentar novamente'));

    await waitFor(() => {
      expect(mockClient.get).toHaveBeenCalledTimes(2);
    });

    expect(screen.getByText('ord_001')).toBeInTheDocument();
  });
});