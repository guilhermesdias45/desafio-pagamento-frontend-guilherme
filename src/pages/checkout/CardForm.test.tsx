import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CardForm } from './CardForm';
import type { PaymentResultData } from '@/types/checkout';

const mockOnPaymentComplete = vi.fn();
const mockOnError = vi.fn();
const mockPostTransaction = vi.fn();

function createMockMercadoPago(resolvedValue: { id: string }) {
  return { cardToken: vi.fn().mockResolvedValue(resolvedValue) };
}

const defaultProps = {
  orderId: 'ord_123',
  amountInCents: 123456,
  customerId: 'cust_1',
  onPaymentComplete: mockOnPaymentComplete,
  onError: mockOnError,
  postTransaction: mockPostTransaction,
  mercadoPagoInstance: createMockMercadoPago({ id: 'tok_default' }),
};

function fillForm(overrides?: Record<string, string>) {
  fireEvent.change(screen.getByLabelText('Número do cartão'), {
    target: { value: overrides?.cardNumber ?? '4111111111111111' },
  });
  fireEvent.change(screen.getByLabelText('Validade'), {
    target: { value: overrides?.expiry ?? '12/30' },
  });
  fireEvent.change(screen.getByLabelText('CVV'), {
    target: { value: overrides?.cvv ?? '123' },
  });
  fireEvent.change(screen.getByLabelText('Nome do titular'), {
    target: { value: overrides?.cardholderName ?? 'João Silva' },
  });
  const installmentSelect = screen.getByLabelText('Parcelas');
  fireEvent.change(installmentSelect, {
    target: { value: overrides?.installments ?? '1' },
  });
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('CardForm', () => {
  describe('Card brand detection (CHECKOUT-01)', () => {
    it('detects Visa for number starting with 4', () => {
      render(<CardForm {...defaultProps} />);
      const input = screen.getByLabelText('Número do cartão');
      fireEvent.change(input, { target: { value: '4123456789012345' } });
      expect(screen.getByText('Visa')).toBeInTheDocument();
    });

    it('detects Mastercard for number starting with 51-55', () => {
      render(<CardForm {...defaultProps} />);
      const input = screen.getByLabelText('Número do cartão');
      fireEvent.change(input, { target: { value: '5123456789012345' } });
      expect(screen.getByText('Mastercard')).toBeInTheDocument();
    });

    it('detects Elo for number starting with 4011', () => {
      render(<CardForm {...defaultProps} />);
      const input = screen.getByLabelText('Número do cartão');
      fireEvent.change(input, { target: { value: '4011123456789012' } });
      expect(screen.getByText('Elo')).toBeInTheDocument();
    });

    it('detects Amex for number starting with 34', () => {
      render(<CardForm {...defaultProps} />);
      const input = screen.getByLabelText('Número do cartão');
      fireEvent.change(input, { target: { value: '341112345678901' } });
      expect(screen.getByText('Amex')).toBeInTheDocument();
    });

    it('detects Hipercard for number starting with 6062', () => {
      render(<CardForm {...defaultProps} />);
      const input = screen.getByLabelText('Número do cartão');
      fireEvent.change(input, { target: { value: '6062123456789012' } });
      expect(screen.getByText('Hipercard')).toBeInTheDocument();
    });

    it('shows unknown for unrecognized prefix', () => {
      render(<CardForm {...defaultProps} />);
      const input = screen.getByLabelText('Número do cartão');
      fireEvent.change(input, { target: { value: '9999123456789012' } });
      expect(screen.getByText('Desconhecida')).toBeInTheDocument();
    });
  });

  describe('Card number masking (CHECKOUT-01)', () => {
    it('formats card number with spaces every 4 digits', () => {
      render(<CardForm {...defaultProps} />);
      const input = screen.getByLabelText('Número do cartão') as HTMLInputElement;
      fireEvent.change(input, { target: { value: '4111111111111111' } });
      expect(input.value).toBe('4111 1111 1111 1111');
    });

    it('shows 4+6+5 grouping for Amex', () => {
      render(<CardForm {...defaultProps} />);
      const input = screen.getByLabelText('Número do cartão') as HTMLInputElement;
      fireEvent.change(input, { target: { value: '341112345678901' } });
      expect(input.value).toBe('3411 123456 78901');
    });
  });

  describe('Expiry mask and validation (CHECKOUT-02)', () => {
    it('formats expiry as MM/AA', () => {
      render(<CardForm {...defaultProps} />);
      const input = screen.getByLabelText('Validade') as HTMLInputElement;
      fireEvent.change(input, { target: { value: '12/30' } });
      expect(input.value).toBe('12/30');
    });

    it('shows error for invalid month', () => {
      render(<CardForm {...defaultProps} />);
      const input = screen.getByLabelText('Validade');
      fireEvent.change(input, { target: { value: '13/30' } });
      fireEvent.blur(input);
      expect(screen.getByText('Mês inválido')).toBeInTheDocument();
    });

    it('shows error for past year', () => {
      render(<CardForm {...defaultProps} />);
      const input = screen.getByLabelText('Validade');
      fireEvent.change(input, { target: { value: '01/20' } });
      fireEvent.blur(input);
      expect(screen.getByText('Cartão vencido')).toBeInTheDocument();
    });
  });

  describe('CVV masking and validation (CHECKOUT-02)', () => {
    it('shows CVV as bullets', () => {
      render(<CardForm {...defaultProps} />);
      const input = screen.getByLabelText('CVV');
      expect(input).toHaveAttribute('type', 'password');
    });

    it('limits CVV to 3 digits for non-Amex', () => {
      render(<CardForm {...defaultProps} />);
      const input = screen.getByLabelText('CVV') as HTMLInputElement;
      fireEvent.change(input, { target: { value: '1234' } });
      expect(input.value).toBe('123');
    });

    it('allows 4 digit CVV for Amex', () => {
      render(<CardForm {...defaultProps} />);
      const cardInput = screen.getByLabelText('Número do cartão');
      fireEvent.change(cardInput, { target: { value: '341112345678901' } });
      const cvvInput = screen.getByLabelText('CVV') as HTMLInputElement;
      fireEvent.change(cvvInput, { target: { value: '1234' } });
      expect(cvvInput.value).toBe('1234');
    });
  });

  describe('Installments selector (CHECKOUT-08)', () => {
    it('renders installment options from 1 to 12', () => {
      render(<CardForm {...defaultProps} />);
      const select = screen.getByLabelText('Parcelas');
      expect(select).toBeInTheDocument();
      const options = screen.getAllByRole('option');
      expect(options).toHaveLength(12);
      expect(options[0]).toHaveTextContent('1x');
      expect(options[11]).toHaveTextContent('12x');
    });
  });

  describe('Order summary display (CHECKOUT-07)', () => {
    it('shows order total formatted as BRL', () => {
      render(<CardForm {...defaultProps} />);
      expect(screen.getByText('R$ 1.234,56')).toBeInTheDocument();
    });

    it('shows order total and pagar label', () => {
      render(<CardForm {...defaultProps} />);
      expect(screen.getByText('Total do pedido')).toBeInTheDocument();
    });
  });

  describe('Form validation (CHECKOUT-02)', () => {
    it('shows Luhn error for invalid card number', () => {
      render(<CardForm {...defaultProps} />);
      fillForm({ cardNumber: '1234567890123456' });
      fireEvent.click(screen.getByRole('button', { name: /pagar/i }));
      expect(screen.getByText('Número de cartão inválido')).toBeInTheDocument();
    });

    it('shows error when name is too short', () => {
      render(<CardForm {...defaultProps} />);
      fillForm({ cardholderName: 'Jo' });
      fireEvent.click(screen.getByRole('button', { name: /pagar/i }));
      expect(screen.getByText('Nome do titular deve ter ao menos 3 caracteres')).toBeInTheDocument();
    });

    it('shows error when expiry is empty', () => {
      render(<CardForm {...defaultProps} />);
      fillForm({ expiry: '' });
      fireEvent.click(screen.getByRole('button', { name: /pagar/i }));
      expect(screen.getByText('Data de validade obrigatória')).toBeInTheDocument();
    });

    it('shows error when CVV is empty', () => {
      render(<CardForm {...defaultProps} />);
      fillForm({ cvv: '' });
      fireEvent.click(screen.getByRole('button', { name: /pagar/i }));
      expect(screen.getByText('CVV obrigatório')).toBeInTheDocument();
    });
  });

  describe('Loading state (CHECKOUT-05)', () => {
    it('shows spinner and disables button during submission', async () => {
      const mpMock = createMockMercadoPago({ id: 'tok_test_123' });

      const postMock = vi.fn().mockImplementation(
        () =>
          new Promise((resolve) =>
            setTimeout(
              () =>
                resolve({
                  data: {
                    transactionId: 'txn_123',
                    mpPaymentId: 12345,
                    orderId: 'ord_123',
                    status: 'APPROVED',
                    processingTimeMs: 350,
                  },
                  errors: [],
                }),
              100,
            ),
          ),
      );

      render(
        <CardForm {...defaultProps} postTransaction={postMock} mercadoPagoInstance={mpMock} />,
      );

      fillForm();
      fireEvent.click(screen.getByRole('button', { name: /pagar/i }));

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /pagar/i })).toBeDisabled();
      });
    });
  });

  describe('Full submission flow (CHECKOUT-03, CHECKOUT-04)', () => {
    it('calls MercadoPago.cardToken and POST on valid form', async () => {
      const mpMock = createMockMercadoPago({ id: 'tok_test_456' });

      const postMock = vi.fn().mockResolvedValue({
        data: {
          transactionId: 'txn_456',
          mpPaymentId: 67890,
          orderId: 'ord_123',
          status: 'APPROVED',
          processingTimeMs: 350,
        },
        errors: [],
      });

      render(
        <CardForm {...defaultProps} postTransaction={postMock} mercadoPagoInstance={mpMock} />,
      );

      fillForm();
      fireEvent.click(screen.getByRole('button', { name: /pagar/i }));

      await waitFor(() => {
        expect(mpMock.cardToken).toHaveBeenCalledWith(
          expect.objectContaining({
            cardNumber: '4111111111111111',
            cardholderName: 'João Silva',
          }),
        );
      });

      await waitFor(() => {
        expect(postMock).toHaveBeenCalledWith(
          '/api/v1/transactions',
          expect.objectContaining({
            amountInCents: 123456,
            currency: 'BRL',
            customerId: 'cust_1',
            orderId: 'ord_123',
            cardToken: 'tok_test_456',
            paymentMethodId: 'credit',
            installments: 1,
          }),
        );
      });

      await waitFor(() => {
        expect(mockOnPaymentComplete).toHaveBeenCalledWith(
          expect.objectContaining({
            transactionId: 'txn_456',
            status: 'APPROVED',
          }),
        );
      });
    });

    it('handles API error response with failure details', async () => {
      const mpMock = createMockMercadoPago({ id: 'tok_test_err' });

      const postMock = vi.fn().mockResolvedValue({
        data: null,
        errors: [{ code: 'CARD_DECLINED', message: 'Cartão recusado', retryable: true }],
      });

      render(
        <CardForm {...defaultProps} postTransaction={postMock} mercadoPagoInstance={mpMock} />,
      );

      fillForm();
      fireEvent.click(screen.getByRole('button', { name: /pagar/i }));

      await waitFor(() => {
        expect(mockOnPaymentComplete).toHaveBeenCalledWith(
          expect.objectContaining({
            status: 'FAILURE',
            errorCode: 'CARD_DECLINED',
            errorMessage: 'Cartão recusado',
            retryable: true,
          }),
        );
      });
    });
  });

  describe('PCI compliance (CHECKOUT-06)', () => {
    it('does not log card data to console', () => {
      const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
      render(<CardForm {...defaultProps} />);
      const input = screen.getByLabelText('Número do cartão');
      fireEvent.change(input, { target: { value: '4111111111111111' } });
      expect(consoleSpy).not.toHaveBeenCalled();
      consoleSpy.mockRestore();
    });
  });
});
