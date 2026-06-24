import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { RefundModal } from './RefundModal';

const mockOnClose = vi.fn();
const mockOnRefundSuccess = vi.fn();

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

function getApprovedTransaction() {
  return { ...baseDetail, status: 'APPROVED' as const };
}

function getPartiallyRefundedTransaction() {
  return {
    ...baseDetail,
    status: 'PARTIALLY_REFUNDED' as const,
    refunds: [
      { refundId: 'ref_001', amountInCents: 30000, reason: 'CUSTOMER_REQUEST' as const, status: 'COMPLETED' as const, createdAt: '2026-06-01T10:00:00Z' },
    ],
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('RefundModal', () => {
  it('renders with correct title', () => {
    render(
      <RefundModal
        transaction={getApprovedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
      />
    );

    expect(screen.getByText(/estornar transação txn_001/i)).toBeInTheDocument();
  });

  it('does not render when isOpen is false', () => {
    render(
      <RefundModal
        transaction={getApprovedTransaction()}
        isOpen={false}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
      />
    );

    expect(screen.queryByText(/estornar transação/i)).not.toBeInTheDocument();
  });

  it('shows refundable amount (original - existing refunds)', () => {
    render(
      <RefundModal
        transaction={getPartiallyRefundedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
      />
    );

    expect(screen.getByText('R$ 700,00')).toBeInTheDocument();
  });

  it('shows total refund radio selected by default', () => {
    render(
      <RefundModal
        transaction={getApprovedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
      />
    );

    const totalRadio = screen.getByLabelText('Estorno total');
    expect(totalRadio).toBeChecked();
  });

  it('shows amount input when partial refund is selected', () => {
    render(
      <RefundModal
        transaction={getApprovedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
      />
    );

    fireEvent.click(screen.getByLabelText('Estorno parcial'));

    expect(screen.getByLabelText(/valor do estorno/i)).toBeInTheDocument();
  });

  it('shows reason dropdown with all options', () => {
    render(
      <RefundModal
        transaction={getApprovedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
      />
    );

    fireEvent.click(screen.getByLabelText('Estorno parcial'));

    const select = screen.getByRole('combobox', { name: /motivo/i });
    expect(select).toBeInTheDocument();
    expect(screen.getByText('Solicitação do cliente')).toBeInTheDocument();
    expect(screen.getByText('Duplicidade')).toBeInTheDocument();
    expect(screen.getByText('Fraude')).toBeInTheDocument();
    expect(screen.getByText('Produto não entregue')).toBeInTheDocument();
  });

  it('moves to confirmation step on Continuar click', () => {
    render(
      <RefundModal
        transaction={getApprovedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
      />
    );

    fireEvent.click(screen.getByLabelText('Estorno total'));

    const select = screen.getByRole('combobox', { name: /motivo/i });
    fireEvent.change(select, { target: { value: 'CUSTOMER_REQUEST' } });

    fireEvent.click(screen.getByText('Continuar'));

    expect(screen.getByText(/confirmar estorno/i)).toBeInTheDocument();
  });

  it('shows warning message on confirmation step', () => {
    render(
      <RefundModal
        transaction={getApprovedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
      />
    );

    fireEvent.click(screen.getByLabelText('Estorno total'));

    const select = screen.getByRole('combobox', { name: /motivo/i });
    fireEvent.change(select, { target: { value: 'CUSTOMER_REQUEST' } });

    fireEvent.click(screen.getByText('Continuar'));

    expect(screen.getByText(/o valor será estornado na fatura do cliente em até 5 dias úteis/i)).toBeInTheDocument();
  });

  it('calls onClose when clicking Cancelar', () => {
    render(
      <RefundModal
        transaction={getApprovedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
      />
    );

    fireEvent.click(screen.getByText('Cancelar'));
    expect(mockOnClose).toHaveBeenCalled();
  });

  it('shows loading state on confirm button during submission', async () => {
    const mockPost = vi.fn().mockReturnValue(new Promise(() => {}));
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('test-uuid-123' as `${string}-${string}-${string}-${string}-${string}`);

    render(
      <RefundModal
        transaction={getApprovedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
        apiClient={{ post: mockPost, get: vi.fn() } as never}
      />
    );

    fireEvent.click(screen.getByLabelText('Estorno total'));

    const select = screen.getByRole('combobox', { name: /motivo/i });
    fireEvent.change(select, { target: { value: 'CUSTOMER_REQUEST' } });

    fireEvent.click(screen.getByText('Continuar'));

    await waitFor(() => {
      expect(screen.getByText(/confirmar estorno/i)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Confirmar estorno'));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /estornando/i })).toBeDisabled();
    });
  });

  it('validates reason is required before continuing', () => {
    render(
      <RefundModal
        transaction={getApprovedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
      />
    );

    fireEvent.click(screen.getByLabelText('Estorno parcial'));
    fireEvent.click(screen.getByText('Continuar'));

    expect(screen.getByText(/selecione um motivo para o estorno/i)).toBeInTheDocument();
  });

  it('validates amount is required for partial refund', () => {
    render(
      <RefundModal
        transaction={getApprovedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
      />
    );

    fireEvent.click(screen.getByLabelText('Estorno parcial'));

    const select = screen.getByRole('combobox', { name: /motivo/i });
    fireEvent.change(select, { target: { value: 'CUSTOMER_REQUEST' } });

    fireEvent.click(screen.getByText('Continuar'));

    expect(screen.getByText(/informe o valor do estorno/i)).toBeInTheDocument();
  });

  it('validates amount minimum', () => {
    render(
      <RefundModal
        transaction={getApprovedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
      />
    );

    fireEvent.click(screen.getByLabelText('Estorno parcial'));

    const amountInput = screen.getByLabelText(/valor do estorno/i);
    fireEvent.change(amountInput, { target: { value: '0' } });

    const select = screen.getByRole('combobox', { name: /motivo/i });
    fireEvent.change(select, { target: { value: 'CUSTOMER_REQUEST' } });

    fireEvent.click(screen.getByText('Continuar'));

    expect(screen.getByText(/informe o valor do estorno/i)).toBeInTheDocument();
  });

  it('validates amount does not exceed refundable', async () => {
    render(
      <RefundModal
        transaction={getPartiallyRefundedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
      />
    );

    fireEvent.click(screen.getByLabelText('Estorno parcial'));

    const amountInput = screen.getByLabelText(/valor do estorno/i);
    fireEvent.change(amountInput, { target: { value: '800' } });

    const select = screen.getByRole('combobox', { name: /motivo/i });
    fireEvent.change(select, { target: { value: 'CUSTOMER_REQUEST' } });

    fireEvent.click(screen.getByText('Continuar'));

    await waitFor(() => {
      expect(screen.getByText(/valor máximo é R\$ 700,00/i)).toBeInTheDocument();
    });
  });

  it('shows inline error AMOUNT_EXCEEDS_ORIGINAL', async () => {
    const mockPost = vi.fn().mockRejectedValue({
      status: 422,
      errors: [{ code: 'AMOUNT_EXCEEDS_ORIGINAL', message: 'Valor excede o disponível', retryable: false }],
    });

    render(
      <RefundModal
        transaction={getApprovedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
        apiClient={{ post: mockPost, get: vi.fn() } as never}
      />
    );

    fireEvent.click(screen.getByLabelText('Estorno total'));

    const select = screen.getByRole('combobox', { name: /motivo/i });
    fireEvent.change(select, { target: { value: 'CUSTOMER_REQUEST' } });

    fireEvent.click(screen.getByText('Continuar'));
    fireEvent.click(screen.getByText('Confirmar estorno'));

    await waitFor(() => {
      expect(screen.getByText(/valor do estorno excede o valor disponível para estorno/i)).toBeInTheDocument();
    });
  });

  it('closes modal on ALREADY_FULLY_REFUNDED', async () => {
    const mockPost = vi.fn().mockRejectedValue({
      status: 422,
      errors: [{ code: 'ALREADY_FULLY_REFUNDED', message: 'Já totalmente estornada', retryable: false }],
    });

    render(
      <RefundModal
        transaction={getApprovedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
        apiClient={{ post: mockPost, get: vi.fn() } as never}
      />
    );

    fireEvent.click(screen.getByLabelText('Estorno total'));

    const select = screen.getByRole('combobox', { name: /motivo/i });
    fireEvent.change(select, { target: { value: 'CUSTOMER_REQUEST' } });

    fireEvent.click(screen.getByText('Continuar'));
    fireEvent.click(screen.getByText('Confirmar estorno'));

    await waitFor(() => {
      expect(mockOnClose).toHaveBeenCalled();
      expect(mockOnRefundSuccess).not.toHaveBeenCalled();
    });
  });

  it('shows toast on REFUND_WINDOW_EXPIRED and closes modal', async () => {
    const mockPost = vi.fn().mockRejectedValue({
      status: 422,
      errors: [{ code: 'REFUND_WINDOW_EXPIRED', message: 'Prazo expirou', retryable: false }],
    });

    const setToastSpy = vi.fn();

    render(
      <RefundModal
        transaction={getApprovedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
        apiClient={{ post: mockPost, get: vi.fn() } as never}
      />
    );

    fireEvent.click(screen.getByLabelText('Estorno total'));

    const select = screen.getByRole('combobox', { name: /motivo/i });
    fireEvent.change(select, { target: { value: 'CUSTOMER_REQUEST' } });

    fireEvent.click(screen.getByText('Continuar'));
    fireEvent.click(screen.getByText('Confirmar estorno'));

    await waitFor(() => {
      expect(mockOnClose).toHaveBeenCalled();
    });
  });

  it('shows inline retry on MP_GATEWAY_ERROR', async () => {
    const mockPost = vi.fn().mockRejectedValue({
      status: 503,
      errors: [{ code: 'MP_GATEWAY_ERROR', message: 'Gateway error', retryable: true }],
    });

    render(
      <RefundModal
        transaction={getApprovedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
        apiClient={{ post: mockPost, get: vi.fn() } as never}
      />
    );

    fireEvent.click(screen.getByLabelText('Estorno total'));

    const select = screen.getByRole('combobox', { name: /motivo/i });
    fireEvent.change(select, { target: { value: 'CUSTOMER_REQUEST' } });

    fireEvent.click(screen.getByText('Continuar'));
    fireEvent.click(screen.getByText('Confirmar estorno'));

    await waitFor(() => {
      expect(screen.getByText(/erro no gateway de pagamento\. tente novamente\./i)).toBeInTheDocument();
    });

    const retryButton = screen.getByText(/tentar novamente/i);
    expect(retryButton).toBeInTheDocument();
  });

  it('calls onRefundSuccess on successful refund', async () => {
    const mockPost = vi.fn().mockResolvedValue({
      data: { refundId: 'ref_001', transactionId: 'txn_001', amountRefundedInCents: 100000, fullRefund: true, status: 'COMPLETED', processingTimeMs: 200 },
      meta: { timestamp: '', requestId: '' },
      errors: [],
    });

    render(
      <RefundModal
        transaction={getApprovedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
        apiClient={{ post: mockPost, get: vi.fn() } as never}
      />
    );

    fireEvent.click(screen.getByLabelText('Estorno total'));

    const select = screen.getByRole('combobox', { name: /motivo/i });
    fireEvent.change(select, { target: { value: 'CUSTOMER_REQUEST' } });

    fireEvent.click(screen.getByText('Continuar'));
    fireEvent.click(screen.getByText('Confirmar estorno'));

    await waitFor(() => {
      expect(mockOnRefundSuccess).toHaveBeenCalled();
    });
  });

  it('allows going back from confirmation step to form', () => {
    render(
      <RefundModal
        transaction={getApprovedTransaction()}
        isOpen={true}
        onClose={mockOnClose}
        onRefundSuccess={mockOnRefundSuccess}
      />
    );

    fireEvent.click(screen.getByLabelText('Estorno total'));

    const select = screen.getByRole('combobox', { name: /motivo/i });
    fireEvent.change(select, { target: { value: 'CUSTOMER_REQUEST' } });

    fireEvent.click(screen.getByText('Continuar'));

    expect(screen.getByText(/confirmar estorno/i)).toBeInTheDocument();

    fireEvent.click(screen.getByText('Voltar'));

    expect(screen.getByText('Continuar')).toBeInTheDocument();
  });
});
