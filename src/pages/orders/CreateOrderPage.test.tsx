import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CreateOrderPage } from './CreateOrderPage';

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

function getByDescription(text: string) {
  const inputs = screen.getAllByRole('textbox');
  return inputs.find((i) => (i as HTMLInputElement).value === text || i.closest('tr')?.textContent?.includes(text));
}

describe('CreateOrderPage', () => {
  let mockClient: ReturnType<typeof createMockClient>;

  beforeEach(() => {
    vi.clearAllMocks();
    mockClient = createMockClient();
  });

  it('renders merchant input and empty items list with add item button', () => {
    render(<CreateOrderPage apiClient={mockClient as never} navigate={mockNavigate} />);
    expect(screen.getByPlaceholderText(/merchant/i)).toBeInTheDocument();
    expect(screen.getByText('Adicionar item')).toBeInTheDocument();
  });

  it('adds an item row when clicking "Adicionar item"', async () => {
    render(<CreateOrderPage apiClient={mockClient as never} navigate={mockNavigate} />);
    fireEvent.click(screen.getByText('Adicionar item'));
    expect(screen.getAllByPlaceholderText(/produto/i).length).toBeGreaterThan(0);
  });

  it('removes an item row when clicking "Remover"', async () => {
    render(<CreateOrderPage apiClient={mockClient as never} navigate={mockNavigate} />);
    fireEvent.click(screen.getByText('Adicionar item'));
    fireEvent.click(screen.getByText('Adicionar item'));
    const removeButtons = screen.getAllByText('Remover');
    expect(removeButtons).toHaveLength(2);
    fireEvent.click(removeButtons[0]);
    expect(screen.getAllByText('Remover')).toHaveLength(1);
  });

  it('displays real-time subtotal per item', async () => {
    render(<CreateOrderPage apiClient={mockClient as never} navigate={mockNavigate} />);
    fireEvent.click(screen.getByText('Adicionar item'));

    const quantityInput = screen.getByRole('spinbutton');
    const priceInput = screen.getAllByRole('textbox')[3];

    fireEvent.change(quantityInput, { target: { value: '3' } });
    fireEvent.change(priceInput, { target: { value: '15000' } });

    expect(screen.getByText(/450,00/)).toBeInTheDocument();
  });

  it('displays running total when 2+ items', async () => {
    render(<CreateOrderPage apiClient={mockClient as never} navigate={mockNavigate} />);
    fireEvent.click(screen.getByText('Adicionar item'));
    fireEvent.click(screen.getByText('Adicionar item'));

    const inputs = screen.getAllByRole('spinbutton');
    const textboxes = screen.getAllByRole('textbox');

    fireEvent.change(inputs[0], { target: { value: '2' } });
    fireEvent.change(textboxes[3], { target: { value: '10000' } });

    fireEvent.change(inputs[1], { target: { value: '3' } });
    fireEvent.change(textboxes[6], { target: { value: '5000' } });

    expect(screen.getByText(/350,00/)).toBeInTheDocument();
  });

  it('shows validation error when submitting with no items', async () => {
    render(<CreateOrderPage apiClient={mockClient as never} navigate={mockNavigate} />);
    fireEvent.change(screen.getByPlaceholderText(/merchant/i), { target: { value: 'merchant_1' } });
    fireEvent.click(screen.getByRole('button', { name: /criar pedido/i }));
    await waitFor(() => {
      expect(screen.getByText(/adicione pelo menos um item/i)).toBeInTheDocument();
    });
  });

  it('calls API and redirects on successful creation', async () => {
    mockClient.post.mockResolvedValue({
      data: { orderId: 'order_123', status: 'PENDING', totalInCents: 20000, items: [], expiresAt: '', createdAt: '' },
      meta: { timestamp: '', requestId: '' },
      errors: [],
    });

    render(<CreateOrderPage apiClient={mockClient as never} navigate={mockNavigate} />);
    const merchantInput = screen.getByPlaceholderText(/merchant/i);
    fireEvent.change(merchantInput, { target: { value: 'merchant_1' } });

    fireEvent.click(screen.getByText('Adicionar item'));
    fireEvent.change(screen.getByPlaceholderText(/produto/i), { target: { value: 'prod_1' } });
    fireEvent.change(screen.getAllByRole('textbox')[2], { target: { value: 'Item de teste' } });
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '2' } });
    fireEvent.change(screen.getAllByRole('textbox')[3], { target: { value: '10000' } });

    fireEvent.click(screen.getByRole('button', { name: /criar pedido/i }));

    await waitFor(() => {
      expect(mockClient.post).toHaveBeenCalled();
      expect(mockNavigate).toHaveBeenCalledWith('/orders/order_123');
    });
  });

  it('redirects on DUPLICATE_ORDER (409)', async () => {
    mockClient.post.mockRejectedValue({
      status: 409,
      errors: [{ code: 'DUPLICATE_ORDER', message: 'order_dup_existing_id_12345', retryable: false }],
    });

    render(<CreateOrderPage apiClient={mockClient as never} navigate={mockNavigate} />);
    const merchantInput = screen.getByPlaceholderText(/merchant/i);
    fireEvent.change(merchantInput, { target: { value: 'merchant_1' } });

    fireEvent.click(screen.getByText('Adicionar item'));
    fireEvent.change(screen.getByPlaceholderText(/produto/i), { target: { value: 'prod_1' } });
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '1' } });
    fireEvent.change(screen.getAllByRole('textbox')[3], { target: { value: '5000' } });

    fireEvent.click(screen.getByRole('button', { name: /criar pedido/i }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/orders/order_dup_existing_id_12345');
    });
  });

  it('shows error when API returns EMPTY_ORDER error', async () => {
    mockClient.post.mockRejectedValue({
      status: 400,
      errors: [{ code: 'EMPTY_ORDER', message: 'Adicione pelo menos um item ao pedido', retryable: false }],
    });

    render(<CreateOrderPage apiClient={mockClient as never} navigate={mockNavigate} />);
    fireEvent.change(screen.getByPlaceholderText(/merchant/i), { target: { value: 'merchant_1' } });
    fireEvent.click(screen.getByText('Adicionar item'));
    fireEvent.change(screen.getAllByRole('textbox')[3], { target: { value: '1000' } });

    fireEvent.click(screen.getByRole('button', { name: /criar pedido/i }));

    await waitFor(() => {
      expect(screen.getByText(/adicione pelo menos um item/i)).toBeInTheDocument();
    });
  });

  it('shows error when API returns MERCHANT_NOT_FOUND', async () => {
    mockClient.post.mockRejectedValue({
      status: 404,
      errors: [{ code: 'MERCHANT_NOT_FOUND', message: 'Merchant não encontrado. Verifique o CNPJ ou selecione outro.', retryable: false }],
    });

    render(<CreateOrderPage apiClient={mockClient as never} navigate={mockNavigate} />);
    fireEvent.change(screen.getByPlaceholderText(/merchant/i), { target: { value: 'invalid_merchant' } });
    fireEvent.click(screen.getByText('Adicionar item'));
    fireEvent.change(screen.getAllByRole('textbox')[3], { target: { value: '1000' } });

    fireEvent.click(screen.getByRole('button', { name: /criar pedido/i }));

    await waitFor(() => {
      expect(screen.getByText(/merchant não encontrado/i)).toBeInTheDocument();
    });
  });

  it('shows error when total exceeds limit', async () => {
    mockClient.post.mockRejectedValue({
      status: 400,
      errors: [{ code: 'TOTAL_EXCEEDS_LIMIT', message: 'Valor total do pedido excede o limite de R$ 9.999,99', retryable: false }],
    });

    render(<CreateOrderPage apiClient={mockClient as never} navigate={mockNavigate} />);
    fireEvent.change(screen.getByPlaceholderText(/merchant/i), { target: { value: 'merchant_1' } });
    fireEvent.click(screen.getByText('Adicionar item'));
    fireEvent.change(screen.getAllByRole('textbox')[3], { target: { value: '1000' } });

    fireEvent.click(screen.getByRole('button', { name: /criar pedido/i }));

    await waitFor(() => {
      expect(screen.getByText(/excede o limite/i)).toBeInTheDocument();
    });
  });
});
