import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { OrderDetailPage } from './OrderDetailPage';

const mockNavigate = vi.fn();

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

const paidOrder = {
  orderId: 'ord_001',
  customerId: 'customer_123',
  merchantId: 'merchant_1',
  status: 'PAID',
  totalInCents: 35000,
  items: [
    { productId: 'prod_1', description: 'Item 1', quantity: 2, unitPriceInCents: 10000, subtotalInCents: 20000 },
    { productId: 'prod_2', description: 'Item 2', quantity: 1, unitPriceInCents: 15000, subtotalInCents: 15000 },
  ],
  transactionId: 'txn_001',
  createdAt: '2026-06-01T10:00:00Z',
  updatedAt: '2026-06-01T10:05:00Z',
};

const pendingOrder = {
  orderId: 'ord_002',
  customerId: 'customer_123',
  merchantId: 'merchant_1',
  status: 'PENDING',
  totalInCents: 25000,
  items: [
    { productId: 'prod_3', description: 'Item Pendente', quantity: 1, unitPriceInCents: 25000, subtotalInCents: 25000 },
  ],
  createdAt: '2026-06-02T14:30:00Z',
  updatedAt: '2026-06-02T14:30:00Z',
  expiresAt: '2026-06-02T15:00:00Z',
};

describe('OrderDetailPage', () => {
  let mockClient: ReturnType<typeof createMockClient>;

  beforeEach(() => {
    vi.clearAllMocks();
    mockClient = createMockClient();
  });

  it('shows loading spinner initially', () => {
    mockClient.get.mockReturnValue(new Promise(() => {}));
    render(<OrderDetailPage orderId="ord_001" apiClient={mockClient as never} navigate={mockNavigate} />);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('renders PAID order with transaction link', async () => {
    mockClient.get.mockResolvedValue(paidOrder);

    render(<OrderDetailPage orderId="ord_001" apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText('ord_001')).toBeInTheDocument();
      expect(screen.getByText('Pago')).toBeInTheDocument();
      expect(screen.getByText('R$ 350,00')).toBeInTheDocument();
    });

    const transactionLink = screen.getByText(/transação/i);
    expect(transactionLink).toBeInTheDocument();
    fireEvent.click(transactionLink);
    expect(mockNavigate).toHaveBeenCalledWith('/transactions/txn_001');
  });

  it('renders items table with subtotals', async () => {
    mockClient.get.mockResolvedValue(paidOrder);

    render(<OrderDetailPage orderId="ord_001" apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText('prod_1')).toBeInTheDocument();
      expect(screen.getByText('Item 1')).toBeInTheDocument();
      expect(screen.getByText('prod_2')).toBeInTheDocument();
      expect(screen.getByText('Item 2')).toBeInTheDocument();
      const rs200 = screen.getAllByText('R$ 200,00');
      expect(rs200.length).toBeGreaterThan(0);
      const rs150 = screen.getAllByText('R$ 150,00');
      expect(rs150.length).toBeGreaterThan(1);
    });
  });

  it('shows cancel button for PENDING order', async () => {
    mockClient.get.mockResolvedValue(pendingOrder);

    render(<OrderDetailPage orderId="ord_002" apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText(/cancelar pedido/i)).toBeInTheDocument();
    });
  });

  it('shows expiry time for PENDING order', async () => {
    mockClient.get.mockResolvedValue(pendingOrder);

    render(<OrderDetailPage orderId="ord_002" apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText(/expira/i)).toBeInTheDocument();
    });
  });

  it('cancels order and shows Cancelado status', async () => {
    mockClient.get.mockResolvedValue(pendingOrder);
    mockClient.delete.mockResolvedValue(undefined);

    render(<OrderDetailPage orderId="ord_002" apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText(/cancelar pedido/i)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText(/cancelar pedido/i));

    await waitFor(() => {
      expect(screen.getByText(/confirmar cancelamento/i)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /confirmar/i }));

    await waitFor(() => {
      expect(mockClient.delete).toHaveBeenCalledWith('/api/v1/orders/ord_002');
    });
  });

  it('shows error when cancel fails with ORDER_CANNOT_BE_CANCELLED', async () => {
    mockClient.get.mockResolvedValue(pendingOrder);
    mockClient.delete.mockRejectedValue({
      status: 422,
      errors: [{ code: 'ORDER_CANNOT_BE_CANCELLED', message: 'Este pedido não pode mais ser cancelado', retryable: false }],
    });

    render(<OrderDetailPage orderId="ord_002" apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText(/cancelar pedido/i)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText(/cancelar pedido/i));

    await waitFor(() => {
      expect(screen.getByText(/confirmar cancelamento/i)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /confirmar/i }));

    await waitFor(() => {
      expect(screen.getByText(/não pode mais ser cancelado/i)).toBeInTheDocument();
    });
  });

  it('shows ORDER_NOT_FOUND error with link to history', async () => {
    mockClient.get.mockRejectedValue({
      status: 404,
      errors: [{ code: 'ORDER_NOT_FOUND', message: 'Pedido não encontrado', retryable: false }],
    });

    render(<OrderDetailPage orderId="invalid" apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText(/pedido não encontrado/i)).toBeInTheDocument();
    });

    const historyLink = screen.getByText(/histórico/i);
    expect(historyLink).toBeInTheDocument();
    fireEvent.click(historyLink);
    expect(mockNavigate).toHaveBeenCalledWith('/orders');
  });

  it('does not show cancel button for non-PENDING orders', async () => {
    mockClient.get.mockResolvedValue(paidOrder);

    render(<OrderDetailPage orderId="ord_001" apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText('ord_001')).toBeInTheDocument();
    });

    expect(screen.queryByText(/cancelar pedido/i)).not.toBeInTheDocument();
  });

  it('does not show transaction link when no transactionId', async () => {
    mockClient.get.mockResolvedValue(pendingOrder);

    render(<OrderDetailPage orderId="ord_002" apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText('ord_002')).toBeInTheDocument();
    });

    expect(screen.queryByText(/transação/i)).not.toBeInTheDocument();
  });
});
