import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { TransactionsListPage } from './TransactionsListPage';

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

const mockTransactions = [
  {
    transactionId: 'txn_uuid_001abc',
    amountInCents: 123456,
    currency: 'BRL' as const,
    status: 'APPROVED' as const,
    cardBrand: 'visa',
    cardLastFour: '4242',
    customerId: '550e8400-e29b-41d4-a716-446655440000',
    createdAt: '2026-05-27T14:00:00Z',
  },
  {
    transactionId: 'txn_uuid_002',
    amountInCents: 5000,
    currency: 'BRL' as const,
    status: 'DECLINED' as const,
    cardBrand: 'mastercard',
    cardLastFour: '1234',
    customerId: '660e8400-e29b-41d4-a716-446655440001',
    createdAt: '2026-05-28T10:30:00Z',
  },
];

describe('TransactionsListPage', () => {
  let mockClient: ReturnType<typeof createMockClient>;

  beforeEach(() => {
    vi.clearAllMocks();
    mockClient = createMockClient();
  });

  it('shows loading spinner initially', () => {
    mockClient.get.mockReturnValue(new Promise(() => {}));
    render(<TransactionsListPage apiClient={mockClient as never} navigate={mockNavigate} />);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('renders transaction rows from API', async () => {
    mockClient.get.mockResolvedValue({
      content: mockTransactions,
      totalPages: 1,
      totalElements: 2,
      number: 0,
    });

    render(<TransactionsListPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText(/txn_uuid_001/)).toBeInTheDocument();
      expect(screen.getByText('R$ 1.234,56')).toBeInTheDocument();
      expect(screen.getByText('R$ 50,00')).toBeInTheDocument();
    });
  });

  it('displays status badge with correct colors', async () => {
    mockClient.get.mockResolvedValue({
      content: mockTransactions,
      totalPages: 1,
      totalElements: 2,
      number: 0,
    });

    render(<TransactionsListPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      const approvedBadge = screen.getByText('Aprovado');
      const declinedBadge = screen.getByText('Recusado');
      expect(approvedBadge.className).toContain('green');
      expect(declinedBadge.className).toContain('red');
    });
  });

  it('shows card brand and last four', async () => {
    mockClient.get.mockResolvedValue({
      content: mockTransactions,
      totalPages: 1,
      totalElements: 2,
      number: 0,
    });

    render(<TransactionsListPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText('visa **** 4242')).toBeInTheDocument();
      expect(screen.getByText('mastercard **** 1234')).toBeInTheDocument();
    });
  });

  it('shows empty state when no transactions', async () => {
    mockClient.get.mockResolvedValue({
      content: [],
      totalPages: 0,
      totalElements: 0,
      number: 0,
    });

    render(<TransactionsListPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText(/nenhuma transação encontrada/i)).toBeInTheDocument();
    });
  });

  it('shows error message on API failure with retry', async () => {
    mockClient.get.mockRejectedValue(new Error('Network error'));

    render(<TransactionsListPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText(/tentar novamente/i)).toBeInTheDocument();
    });

    mockClient.get.mockResolvedValue({
      content: mockTransactions,
      totalPages: 1,
      totalElements: 2,
      number: 0,
    });

    fireEvent.click(screen.getByText(/tentar novamente/i));

    await waitFor(() => {
      expect(screen.getByText('R$ 1.234,56')).toBeInTheDocument();
    });
  });

  it('displays pagination controls when there are multiple pages', async () => {
    mockClient.get.mockResolvedValue({
      content: mockTransactions,
      totalPages: 3,
      totalElements: 6,
      number: 0,
    });

    render(<TransactionsListPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText(/anterior/i)).toBeInTheDocument();
      expect(screen.getByText(/próximo/i)).toBeInTheDocument();
      expect(screen.getByText(/página 1 de 3/i)).toBeInTheDocument();
    });
  });

  it('calls API with new page on pagination click', async () => {
    mockClient.get.mockResolvedValue({
      content: mockTransactions,
      totalPages: 3,
      totalElements: 6,
      number: 0,
    });

    render(<TransactionsListPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText(/próximo/i)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText(/próximo/i));

    await waitFor(() => {
      const calls = mockClient.get.mock.calls as Array<[string, Record<string, { params?: Record<string, unknown> }>]>;
      const callWithPage1 = calls.find(([, opts]) => (opts?.params as Record<string, unknown> | undefined)?.page === 1);
      expect(callWithPage1).toBeTruthy();
    });
  });

  it('navigates to transaction detail on row click', async () => {
    mockClient.get.mockResolvedValue({
      content: mockTransactions,
      totalPages: 1,
      totalElements: 2,
      number: 0,
    });

    render(<TransactionsListPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText(/txn_uuid_001/)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText(/txn_uuid_001/));
    expect(mockNavigate).toHaveBeenCalledWith('/merchant/transactions/txn_uuid_001abc');
  });

  it('disables pagination buttons when on first page', async () => {
    mockClient.get.mockResolvedValue({
      content: mockTransactions,
      totalPages: 3,
      totalElements: 6,
      number: 0,
    });

    render(<TransactionsListPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      const prevBtn = screen.getByText(/anterior/i).closest('button');
      expect(prevBtn).toBeDisabled();
      const nextBtn = screen.getByText(/próximo/i).closest('button');
      expect(nextBtn).not.toBeDisabled();
    });
  });

  it('renders formatted date', async () => {
    mockClient.get.mockResolvedValue({
      content: mockTransactions,
      totalPages: 1,
      totalElements: 2,
      number: 0,
    });

    render(<TransactionsListPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText(/27\/05\/2026/)).toBeInTheDocument();
    });
  });
});
