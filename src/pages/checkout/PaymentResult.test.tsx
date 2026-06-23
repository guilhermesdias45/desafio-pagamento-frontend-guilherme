import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { PaymentResult } from './PaymentResult';
import type { PaymentResultData } from '@/types/checkout';

const mockOnRetry = vi.fn();
const mockOnViewOrder = vi.fn();

function createResult(overrides?: Partial<PaymentResultData>): PaymentResultData {
  return {
    transactionId: 'txn_123',
    orderId: 'ord_123',
    status: 'APPROVED',
    amountInCents: 123456,
    processingTimeMs: 350,
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('PaymentResult', () => {
  describe('APPROVED (CHECKOUT-09)', () => {
    it('shows green checkmark icon', () => {
      render(
        <PaymentResult
          result={createResult()}
          onRetry={mockOnRetry}
          onViewOrder={mockOnViewOrder}
        />,
      );
      const icon = document.querySelector('svg');
      expect(icon?.getAttribute('class')).toContain('text-green-500');
    });

    it('shows "Pagamento aprovado!" title', () => {
      render(
        <PaymentResult
          result={createResult()}
          onRetry={mockOnRetry}
          onViewOrder={mockOnViewOrder}
        />,
      );
      expect(screen.getByText('Pagamento aprovado!')).toBeInTheDocument();
    });

    it('shows formatted amount in BRL', () => {
      render(
        <PaymentResult
          result={createResult()}
          onRetry={mockOnRetry}
          onViewOrder={mockOnViewOrder}
        />,
      );
      expect(screen.getByText('R$ 1.234,56')).toBeInTheDocument();
    });

    it('shows transaction ID', () => {
      render(
        <PaymentResult
          result={createResult()}
          onRetry={mockOnRetry}
          onViewOrder={mockOnViewOrder}
        />,
      );
      expect(screen.getByText(/txn_123/)).toBeInTheDocument();
    });

    it('shows processing time formatted', () => {
      render(
        <PaymentResult
          result={createResult()}
          onRetry={mockOnRetry}
          onViewOrder={mockOnViewOrder}
        />,
      );
      expect(screen.getByText('Processado em 350 ms')).toBeInTheDocument();
    });

    it('shows "Ver pedido" button that calls onViewOrder', () => {
      render(
        <PaymentResult
          result={createResult()}
          onRetry={mockOnRetry}
          onViewOrder={mockOnViewOrder}
        />,
      );
      const button = screen.getByRole('button', { name: /ver pedido/i });
      fireEvent.click(button);
      expect(mockOnViewOrder).toHaveBeenCalledWith('ord_123');
    });

    it('does not show retry button', () => {
      render(
        <PaymentResult
          result={createResult()}
          onRetry={mockOnRetry}
          onViewOrder={mockOnViewOrder}
        />,
      );
      expect(screen.queryByText(/tentar novamente/i)).not.toBeInTheDocument();
    });
  });

  describe('CARD_DECLINED (CHECKOUT-10)', () => {
    it('shows red X icon', () => {
      render(
        <PaymentResult
          result={createResult({
            status: 'FAILURE',
            errorCode: 'CARD_DECLINED',
            errorMessage: 'Cartão recusado',
            retryable: true,
          })}
          onRetry={mockOnRetry}
          onViewOrder={mockOnViewOrder}
        />,
      );
      const icon = document.querySelector('svg');
      expect(icon?.getAttribute('class')).toContain('text-red-500');
    });

    it('shows "Cartão recusado" title', () => {
      render(
        <PaymentResult
          result={createResult({
            status: 'FAILURE',
            errorCode: 'CARD_DECLINED',
            errorMessage: 'Cartão recusado',
            retryable: true,
          })}
          onRetry={mockOnRetry}
          onViewOrder={mockOnViewOrder}
        />,
      );
      expect(screen.getByText('Cartão recusado')).toBeInTheDocument();
    });

    it('shows retry message and button', () => {
      render(
        <PaymentResult
          result={createResult({
            status: 'FAILURE',
            errorCode: 'CARD_DECLINED',
            errorMessage: 'Cartão recusado',
            retryable: true,
          })}
          onRetry={mockOnRetry}
          onViewOrder={mockOnViewOrder}
        />,
      );
      expect(
        screen.getByText('Seu cartão foi recusado. Verifique os dados ou tente outro cartão.'),
      ).toBeInTheDocument();
      const retryBtn = screen.getByRole('button', { name: /tentar novamente com outro cartão/i });
      fireEvent.click(retryBtn);
      expect(mockOnRetry).toHaveBeenCalled();
    });
  });

  describe('SUSPECTED_FRAUD (CHECKOUT-11)', () => {
    it('shows red X icon', () => {
      render(
        <PaymentResult
          result={createResult({
            status: 'FAILURE',
            errorCode: 'SUSPECTED_FRAUD',
            errorMessage: 'Transação suspeita',
            retryable: false,
          })}
          onRetry={mockOnRetry}
          onViewOrder={mockOnViewOrder}
        />,
      );
      const icon = document.querySelector('svg');
      expect(icon?.getAttribute('class')).toContain('text-red-500');
    });

    it('shows "Transação suspeita" title and no retry button', () => {
      render(
        <PaymentResult
          result={createResult({
            status: 'FAILURE',
            errorCode: 'SUSPECTED_FRAUD',
            errorMessage: 'Transação suspeita',
            retryable: false,
          })}
          onRetry={mockOnRetry}
          onViewOrder={mockOnViewOrder}
        />,
      );
      expect(screen.getByText('Transação suspeita')).toBeInTheDocument();
      expect(
        screen.getByText('Transação suspeita. Entre em contato com o suporte.'),
      ).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /tentar novamente/i })).not.toBeInTheDocument();
    });
  });

  describe('MP_GATEWAY_TIMEOUT (CHECKOUT-12)', () => {
    it('shows orange warning icon', () => {
      render(
        <PaymentResult
          result={createResult({
            status: 'FAILURE',
            errorCode: 'MP_GATEWAY_TIMEOUT',
            errorMessage: 'Gateway timeout',
            retryable: true,
          })}
          onRetry={mockOnRetry}
          onViewOrder={mockOnViewOrder}
        />,
      );
      const icon = document.querySelector('svg');
      expect(icon?.getAttribute('class')).toContain('text-orange-500');
    });

    it('shows "Tempo limite excedido" title and retry button', () => {
      render(
        <PaymentResult
          result={createResult({
            status: 'FAILURE',
            errorCode: 'MP_GATEWAY_TIMEOUT',
            errorMessage: 'Gateway timeout',
            retryable: true,
          })}
          onRetry={mockOnRetry}
          onViewOrder={mockOnViewOrder}
        />,
      );
      expect(screen.getByText('Tempo limite excedido')).toBeInTheDocument();
      expect(
        screen.getByText('O gateway de pagamento não respondeu a tempo. Tente novamente.'),
      ).toBeInTheDocument();
      const retryBtn = screen.getByRole('button', { name: /tentar novamente/i });
      fireEvent.click(retryBtn);
      expect(mockOnRetry).toHaveBeenCalled();
    });
  });

  describe('INVALID_CARD_TOKEN (CHECKOUT-13)', () => {
    it('shows "Erro no processamento" title and retry button', () => {
      render(
        <PaymentResult
          result={createResult({
            status: 'FAILURE',
            errorCode: 'INVALID_CARD_TOKEN',
            errorMessage: 'Token inválido',
            retryable: true,
          })}
          onRetry={mockOnRetry}
          onViewOrder={mockOnViewOrder}
        />,
      );
      expect(screen.getByText('Erro no processamento')).toBeInTheDocument();
      expect(
        screen.getByText('Ocorreu um erro ao processar seu cartão. Tente novamente.'),
      ).toBeInTheDocument();
      const retryBtn = screen.getByRole('button', { name: /tentar novamente/i });
      fireEvent.click(retryBtn);
      expect(mockOnRetry).toHaveBeenCalled();
    });
  });

  describe('Generic retryable error (CHECKOUT-14)', () => {
    it('shows API error message and retry button when retryable is true', () => {
      render(
        <PaymentResult
          result={createResult({
            status: 'FAILURE',
            errorCode: 'INTERNAL_ERROR',
            errorMessage: 'Erro interno do servidor',
            retryable: true,
          })}
          onRetry={mockOnRetry}
          onViewOrder={mockOnViewOrder}
        />,
      );
      expect(screen.getByText('Pagamento não aprovado')).toBeInTheDocument();
      expect(screen.getByText('Erro interno do servidor')).toBeInTheDocument();
      const retryBtn = screen.getByRole('button', { name: /tentar novamente/i });
      fireEvent.click(retryBtn);
      expect(mockOnRetry).toHaveBeenCalled();
    });

    it('does not show retry button when retryable is false', () => {
      render(
        <PaymentResult
          result={createResult({
            status: 'FAILURE',
            errorCode: 'INSUFFICIENT_FUNDS',
            errorMessage: 'Seu cartão não tem limite disponível',
            retryable: false,
          })}
          onRetry={mockOnRetry}
          onViewOrder={mockOnViewOrder}
        />,
      );
      expect(screen.getByText('Pagamento não aprovado')).toBeInTheDocument();
      expect(screen.getByText('Seu cartão não tem limite disponível')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /tentar novamente/i })).not.toBeInTheDocument();
    });
  });

  describe('Processing time formatting (CHECKOUT-13)', () => {
    it('formats processing time with thousands separator', () => {
      render(
        <PaymentResult
          result={createResult({ processingTimeMs: 1500 })}
          onRetry={mockOnRetry}
          onViewOrder={mockOnViewOrder}
        />,
      );
      expect(screen.getByText('Processado em 1.500 ms')).toBeInTheDocument();
    });
  });
});
