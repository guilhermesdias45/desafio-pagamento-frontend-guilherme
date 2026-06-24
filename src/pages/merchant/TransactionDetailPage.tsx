import { useState, useEffect, useMemo, useCallback } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import { ApiClient } from '@/lib/api-client';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import { Button } from '@/components/ui/Button';
import { RefundModal } from './RefundModal';
import type { TransactionDetail } from '@/types/merchant';
import { STATUS_BADGE_CLASSES, STATUS_LABELS } from '@/types/merchant';

interface Props {
  transactionId: string;
  apiClient?: ApiClient;
  navigate?: (path: string) => void;
}

const formatBRL = (cents: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);

const formatDate = (iso: string) =>
  new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(iso));

const isRefundable = (status: string) =>
  status === 'APPROVED' || status === 'PARTIALLY_REFUNDED';

export function TransactionDetailPage({ transactionId, apiClient: externalClient, navigate }: Props) {
  const { token, user } = useAuth();
  const apiClient = useMemo(() => externalClient ?? new ApiClient(() => token), [externalClient, token]);
  const goBack = useCallback(() => {
    if (navigate) navigate('/merchant/transactions');
    else window.location.href = '/merchant/transactions';
  }, [navigate]);

  const [transaction, setTransaction] = useState<TransactionDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showRefundModal, setShowRefundModal] = useState(false);
  const [toast, setToast] = useState<{ type: 'success' | 'error' | 'info'; message: string } | null>(null);

  const fetchDetail = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const result = await apiClient.get<TransactionDetail>(`/api/v1/transactions/${transactionId}`, { merchantId: user?.merchantId ?? undefined });
      setTransaction(result);
    } catch (err: unknown) {
      const apiErr = err as { status?: number; errors?: Array<{ code: string; message: string }> };
      if (apiErr.errors && apiErr.errors.length > 0) {
        setError(apiErr.errors[0].message || 'Erro ao carregar transação');
      } else {
        setError('Erro de conexão. Verifique sua internet.');
      }
    } finally {
      setLoading(false);
    }
  }, [apiClient, transactionId]);

  useEffect(() => {
    fetchDetail();
  }, [transactionId]);

  const handleRefundSuccess = () => {
    setShowRefundModal(false);
    setToast({ type: 'success', message: 'Estorno realizado com sucesso' });
    fetchDetail();
  };

  const handleRefundError = (code: string) => {
    setShowRefundModal(false);
    fetchDetail();
    if (code === 'ALREADY_FULLY_REFUNDED') {
      setToast({ type: 'info', message: 'Esta transação já foi totalmente estornada' });
    } else if (code === 'REFUND_WINDOW_EXPIRED') {
      setToast({ type: 'error', message: 'Prazo de 90 dias para estorno expirou' });
    }
  };

  useEffect(() => {
    if (toast) {
      const timer = setTimeout(() => setToast(null), 5000);
      return () => clearTimeout(timer);
    }
  }, [toast]);

  if (loading) {
    return (
      <div className="flex justify-center py-12" role="status">
        <Spinner size="lg" />
      </div>
    );
  }

  if (error && !transaction) {
    return (
      <div className="max-w-3xl mx-auto p-6">
        <Button variant="ghost" onClick={goBack} className="mb-4">&larr; Voltar</Button>
        <ErrorMessage title="Erro ao carregar transação" message={error} onRetry={fetchDetail} />
      </div>
    );
  }

  if (!transaction) return null;

  return (
    <div className="max-w-3xl mx-auto p-6">
      {toast && (
        <div
          className={`mb-4 px-4 py-3 rounded-md text-sm font-medium ${
            toast.type === 'success' ? 'bg-green-100 text-green-800 border border-green-200' :
            toast.type === 'info' ? 'bg-blue-100 text-blue-800 border border-blue-200' :
            'bg-red-100 text-red-800 border border-red-200'
          }`}
          role="alert"
        >
          {toast.message}
        </div>
      )}

      <button
        onClick={goBack}
        className="mb-4 text-sm text-primary hover:underline"
      >
        &larr; Voltar para transações
      </button>

      <div className="bg-white border border-gray-200 rounded-md p-6">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-bold">Detalhes da Transação</h1>
          <span
            className={`px-3 py-1 rounded-full text-sm font-medium ${STATUS_BADGE_CLASSES[transaction.status]}`}
          >
            {STATUS_LABELS[transaction.status]}
          </span>
        </div>

        <div className="grid grid-cols-2 gap-4 mb-6">
          <div>
            <p className="text-sm text-gray-500">ID da Transação</p>
            <p className="text-sm font-mono font-medium" data-testid="transaction-id">{transaction.transactionId}</p>
          </div>
          <div>
            <p className="text-sm text-gray-500">MP Payment ID</p>
            <p className="text-sm font-mono font-medium">{transaction.mpPaymentId}</p>
          </div>
          <div>
            <p className="text-sm text-gray-500">Valor</p>
            <p className="text-lg font-bold">{formatBRL(transaction.amountInCents)}</p>
          </div>
          <div>
            <p className="text-sm text-gray-500">Moeda</p>
            <p className="text-sm font-medium">{transaction.currency}</p>
          </div>
          <div>
            <p className="text-sm text-gray-500">Cartão</p>
            <p className="text-sm font-medium">{transaction.cardBrand} **** {transaction.cardLastFour}</p>
          </div>
          <div>
            <p className="text-sm text-gray-500">Pedido</p>
            <p className="text-sm font-mono font-medium">{transaction.orderId}</p>
          </div>
          <div>
            <p className="text-sm text-gray-500">Cliente</p>
            <p className="text-sm font-mono font-medium">{transaction.customerId}</p>
          </div>
          <div>
            <p className="text-sm text-gray-500">Merchant</p>
            <p className="text-sm font-mono font-medium">{transaction.merchantId}</p>
          </div>
          <div>
            <p className="text-sm text-gray-500">Criado em</p>
            <p className="text-sm font-medium">{formatDate(transaction.createdAt)}</p>
          </div>
          <div>
            <p className="text-sm text-gray-500">Atualizado em</p>
            <p className="text-sm font-medium">{formatDate(transaction.updatedAt)}</p>
          </div>
        </div>

        {transaction.refunds.length > 0 && (
          <div className="mb-6">
            <h2 className="text-lg font-semibold mb-2">Estornos</h2>
            <div className="space-y-2">
              {transaction.refunds.map((refund) => (
                <div key={refund.refundId} className="flex items-center justify-between bg-gray-50 px-4 py-2 rounded-md">
                  <div>
                    <p className="text-sm font-mono">{refund.refundId}</p>
                    <p className="text-xs text-gray-500">{refund.reason}</p>
                  </div>
                  <p className="text-sm font-medium">{formatBRL(refund.amountInCents)}</p>
                </div>
              ))}
            </div>
          </div>
        )}

        {isRefundable(transaction.status) && (
          <Button onClick={() => setShowRefundModal(true)}>
            Estornar
          </Button>
        )}
      </div>

      {transaction && (
        <RefundModal
          transaction={transaction}
          isOpen={showRefundModal}
          onClose={() => setShowRefundModal(false)}
          onRefundSuccess={handleRefundSuccess}
          onRefundError={handleRefundError}
          apiClient={apiClient}
          userId={user?.id || ''}
          merchantId={user?.merchantId ?? undefined}
        />
      )}
    </div>
  );
}
