import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { TransactionDetailPage } from './TransactionDetailPage';

const mockNavigate = vi.fn();

function createMockClient() {
  return {
    get: vi.fn(),
    post: vi.fn(),
  };
}

vi.mock('@/contexts/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'merchant_user_1', email: 'merchant@test.com', fullName: 'Merchant', role: 'MERCHANT_OWNER', merchantId: 'merchant_123' },
    token: 'test-token',
    isAuthenticated: true,
    isLoading: false,
  }),
  AuthProvider: ({ children }: { children: React.ReactNode }) => children,
}));

const baseDetail = {
  transactionId: 'txn_001',
  mpPaymentId: 12345,
  amountInCents: 100000,
  currency: 'BRL' as const,
  cardBrand: 'visa',
  cardLastFour: '4242',
  orderId: 'ord_001',
  customerId: '550e8400-e29b-41d4-a716-446655440000',
  merchantId: 'merchant_123',
  createdAt: '2026-05-27T14:00:00Z',
  updatedAt: '2026-05-27T14:00:30Z',
  refunds: [] as Array<{
    refundId: string;
    amountInCents: number;
    reason: 'CUSTOMER_REQUEST';
    status: 'COMPLETED';
    createdAt: string;
  }>,
  processingTimeMs: 150,
};

function getMockApproved() {
  return { ...baseDetail, status: 'APPROVED' as const };
}

function getMockDeclined() {
  return { ...baseDetail, status: 'DECLINED' as const };
}

function getMockPartiallyRefunded() {
  return {
    ...baseDetail,
    status: 'PARTIALLY_REFUNDED' as const,
    refunds: [{ refundId: 'ref_001', amountInCents: 30000, reason: 'CUSTOMER_REQUEST' as const, status: 'COMPLETED' as const, createdAt: '2026-06-01T10:00:00Z' }],
  };
}

describe('TransactionDetailPage', () => {
  let mockClient: ReturnType<typeof createMockClient>;

  beforeEach(() => {
    vi.clearAllMocks();
    mockClient = createMockClient();
  });

  it('shows loading spinner initially', () => {
    mockClient.get.mockReturnValue(new Promise(() => {}));
    render(<TransactionDetailPage transactionId="txn_001" apiClient={mockClient as never} navigate={mockNavigate} />);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('renders transaction detail', async () => {
    mockClient.get.mockResolvedValue(getMockApproved());

    render(<TransactionDetailPage transactionId="txn_001" apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText('txn_001')).toBeInTheDocument();
      expect(screen.getByText('R$ 1.000,00')).toBeInTheDocument();
      expect(screen.getByText(/visa/)).toBeInTheDocument();
      expect(screen.getByText('4242')).toBeInTheDocument();
    });
  });

  it('shows Estornar button for APPROVED transaction', async () => {
    mockClient.get.mockResolvedValue(getMockApproved());

    render(<TransactionDetailPage transactionId="txn_001" apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText('Estornar')).toBeInTheDocument();
    });
  });

  it('shows Estornar button for PARTIALLY_REFUNDED transaction', async () => {
    mockClient.get.mockResolvedValue(getMockPartiallyRefunded());

    render(<TransactionDetailPage transactionId="txn_001" apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText('Estornar')).toBeInTheDocument();
    });
  });

  it('hides Estornar button for DECLINED transaction', async () => {
    mockClient.get.mockResolvedValue(getMockDeclined());

    render(<TransactionDetailPage transactionId="txn_001" apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.queryByText('Estornar')).not.toBeInTheDocument();
    });
  });

  it('shows error state on API failure with retry', async () => {
    mockClient.get.mockRejectedValue(new Error('Network error'));

    render(<TransactionDetailPage transactionId="txn_001" apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText(/tentar novamente/i)).toBeInTheDocument();
    });
  });

  it('opens RefundModal when clicking Estornar', async () => {
    mockClient.get.mockResolvedValue(getMockApproved());

    render(<TransactionDetailPage transactionId="txn_001" apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText('Estornar')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Estornar'));

    await waitFor(() => {
      expect(screen.getByText(/estornar transação txn_001/i)).toBeInTheDocument();
    });
  });

  it('can close RefundModal', async () => {
    mockClient.get.mockResolvedValue(getMockApproved());

    render(<TransactionDetailPage transactionId="txn_001" apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText('Estornar')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Estornar'));

    await waitFor(() => {
      expect(screen.getByText(/estornar transação txn_001/i)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Cancelar'));

    await waitFor(() => {
      expect(screen.queryByText(/estornar transação txn_001/i)).not.toBeInTheDocument();
    });
  });
});
