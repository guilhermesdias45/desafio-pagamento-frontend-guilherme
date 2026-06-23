import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { OrderHistoryPage } from './OrderHistoryPage';

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

const mockOrders = [
  { orderId: 'ord_001', status: 'PAID', totalInCents: 15000, createdAt: '2026-06-01T10:00:00Z' },
  { orderId: 'ord_002', status: 'PENDING', totalInCents: 25000, createdAt: '2026-06-02T14:30:00Z' },
];

describe('OrderHistoryPage', () => {
  let mockClient: ReturnType<typeof createMockClient>;

  beforeEach(() => {
    vi.clearAllMocks();
    mockClient = createMockClient();
  });

  it('shows loading spinner initially', () => {
    mockClient.get.mockReturnValue(new Promise(() => {}));
    render(<OrderHistoryPage apiClient={mockClient as never} navigate={mockNavigate} />);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('renders order rows from API', async () => {
    mockClient.get.mockResolvedValue({
      content: mockOrders,
      totalPages: 1,
      totalElements: 2,
      number: 0,
    });

    render(<OrderHistoryPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText('ord_001')).toBeInTheDocument();
      expect(screen.getByText('ord_002')).toBeInTheDocument();
    });
  });

  function findBadge(text: string) {
    return screen.getAllByText(text).find((el) => el.tagName === 'SPAN')!;
  }

  it('displays status badge with correct color for PAID', async () => {
    mockClient.get.mockResolvedValue({
      content: mockOrders,
      totalPages: 1,
      totalElements: 2,
      number: 0,
    });

    render(<OrderHistoryPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      const badge = findBadge('PAID');
      expect(badge.className).toContain('green');
    });
  });

  it('displays status badge with correct color for PENDING', async () => {
    mockClient.get.mockResolvedValue({
      content: mockOrders,
      totalPages: 1,
      totalElements: 2,
      number: 0,
    });

    render(<OrderHistoryPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      const badge = findBadge('PENDING');
      expect(badge.className).toContain('gray');
    });
  });

  it('formats total in BRL', async () => {
    mockClient.get.mockResolvedValue({
      content: mockOrders,
      totalPages: 1,
      totalElements: 2,
      number: 0,
    });

    render(<OrderHistoryPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText('R$ 150,00')).toBeInTheDocument();
      expect(screen.getByText('R$ 250,00')).toBeInTheDocument();
    });
  });

  it('navigates to order detail on row click', async () => {
    mockClient.get.mockResolvedValue({
      content: mockOrders,
      totalPages: 1,
      totalElements: 2,
      number: 0,
    });

    render(<OrderHistoryPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText('ord_001')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('ord_001'));
    expect(mockNavigate).toHaveBeenCalledWith('/orders/ord_001');
  });

  it('shows empty state when no orders', async () => {
    mockClient.get.mockResolvedValue({
      content: [],
      totalPages: 0,
      totalElements: 0,
      number: 0,
    });

    render(<OrderHistoryPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText(/nenhum pedido encontrado/i)).toBeInTheDocument();
    });
  });

  it('shows error message on API failure with retry', async () => {
    mockClient.get.mockRejectedValue(new Error('Network error'));

    render(<OrderHistoryPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText(/tentar novamente/i)).toBeInTheDocument();
    });

    mockClient.get.mockResolvedValue({
      content: mockOrders,
      totalPages: 1,
      totalElements: 2,
      number: 0,
    });

    fireEvent.click(screen.getByText(/tentar novamente/i));

    await waitFor(() => {
      expect(screen.getByText('ord_001')).toBeInTheDocument();
    });
  });

  it('displays pagination controls when there are multiple pages', async () => {
    mockClient.get.mockResolvedValue({
      content: mockOrders,
      totalPages: 3,
      totalElements: 6,
      number: 0,
    });

    render(<OrderHistoryPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText(/anterior/i)).toBeInTheDocument();
      expect(screen.getByText(/próximo/i)).toBeInTheDocument();
    });
  });

  it('calls API with new page on pagination click', async () => {
    mockClient.get.mockResolvedValue({
      content: mockOrders,
      totalPages: 3,
      totalElements: 6,
      number: 0,
    });

    render(<OrderHistoryPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText(/próximo/i)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText(/próximo/i));

    await waitFor(() => {
      const calls = mockClient.get.mock.calls;
      const callWithPage1 = calls.find((c: unknown[]) => {
        const args = c as [string, Record<string, unknown>];
        return args[1]?.params?.page === 1;
      });
      expect(callWithPage1).toBeTruthy();
    });
  });

  it('filters by status when dropdown changes', async () => {
    mockClient.get.mockResolvedValue({
      content: mockOrders,
      totalPages: 1,
      totalElements: 2,
      number: 0,
    });

    render(<OrderHistoryPage apiClient={mockClient as never} navigate={mockNavigate} />);

    await waitFor(() => {
      expect(screen.getByText('ord_001')).toBeInTheDocument();
    });

    const select = screen.getByRole('combobox');
    fireEvent.change(select, { target: { value: 'PAID' } });

    await waitFor(() => {
      const calls = mockClient.get.mock.calls;
      const lastCall = calls[calls.length - 1] as [string, Record<string, unknown>];
      expect(lastCall[1]?.params?.status).toBe('PAID');
      expect(lastCall[1]?.params?.page).toBe(0);
    });
  });
});
