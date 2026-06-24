import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import { ApiClient } from '@/lib/api-client';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import { Button } from '@/components/ui/Button';
import type { OrderDetail, OrderStatus } from '@/types/order';

interface Props {
  orderId: string;
  apiClient?: ApiClient;
  navigate?: (path: string) => void;
}

const formatBRL = (cents: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);

const statusColors: Record<string, string> = {
  PENDING: 'bg-gray-100 text-gray-800',
  PROCESSING: 'bg-blue-100 text-blue-800',
  PAID: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-red-100 text-red-800',
  REFUNDED: 'bg-orange-100 text-orange-800',
  PARTIALLY_REFUNDED: 'bg-yellow-100 text-yellow-800',
};

const statusLabels: Record<string, string> = {
  PENDING: 'Pendente',
  PROCESSING: 'Processando',
  PAID: 'Pago',
  CANCELLED: 'Cancelado',
  REFUNDED: 'Estornado',
  PARTIALLY_REFUNDED: 'Parcialmente estornado',
};

const formatDate = (iso: string) =>
  new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(iso));

export function OrderDetailPage({ orderId, apiClient: externalClient, navigate }: Props) {
  const { token } = useAuth();
  const apiClient = useMemo(() => externalClient ?? new ApiClient(() => token), [externalClient, token]);
  const goTo = useCallback((path: string) => {
    if (navigate) navigate(path);
    else window.location.href = path;
  }, [navigate]);

  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const fetchOrder = useCallback(async () => {
    setLoading(true);
    setError(null);
    setNotFound(false);

    try {
      const result = await apiClient.get<OrderDetail>(`/api/v1/orders/${orderId}`);
      setOrder(result);
    } catch (err: unknown) {
      const apiErr = err as { status?: number; errors?: Array<{ code: string; message: string }> };
      if (apiErr.errors && apiErr.errors.length > 0) {
        if (apiErr.status === 404 && apiErr.errors[0].code === 'ORDER_NOT_FOUND') {
          setNotFound(true);
        }
        setError(apiErr.errors[0].message || 'Erro ao carregar pedido');
      } else {
        setError('Erro de conexão. Verifique sua internet.');
      }
    } finally {
      setLoading(false);
    }
  }, [apiClient, orderId]);

  useEffect(() => {
    fetchOrder();
  }, [fetchOrder]);

  const handleCancel = async () => {
    setCancelling(true);
    setCancelError(null);

    try {
      await apiClient.delete<void>(`/api/v1/orders/${orderId}`);
      setOrder((prev) => prev ? { ...prev, status: 'CANCELLED' as OrderStatus } : null);
      setShowConfirm(false);
    } catch (err: unknown) {
      const apiErr = err as { status?: number; errors?: Array<{ code: string; message: string }> };
      if (apiErr.errors && apiErr.errors.length > 0) {
        const code = apiErr.errors[0].code;
        if (code === 'ORDER_CANNOT_BE_CANCELLED') {
          setCancelError('Este pedido não pode mais ser cancelado');
        } else if (code === 'INSUFFICIENT_PERMISSIONS') {
          setCancelError('Você não tem permissão para realizar esta ação');
        } else {
          setCancelError(apiErr.errors[0].message || 'Erro ao cancelar pedido');
        }
      } else {
        setCancelError('Erro de conexão. Verifique sua internet.');
      }
    } finally {
      setCancelling(false);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center py-12" role="status">
        <Spinner size="lg" />
      </div>
    );
  }

  if (notFound) {
    return (
      <div className="max-w-2xl mx-auto p-6">
        <ErrorMessage
          title="Pedido não encontrado"
          message="O pedido que você está procurando não existe ou foi removido."
        />
        <div className="mt-4">
          <Button variant="secondary" onClick={() => goTo('/orders')}>
            Voltar para Histórico
          </Button>
        </div>
      </div>
    );
  }

  if (error && !order) {
    return (
      <div className="max-w-2xl mx-auto p-6">
        <ErrorMessage title="Erro ao carregar pedido" message={error} onRetry={fetchOrder} />
      </div>
    );
  }

  if (!order) return null;

  const isPending = order.status === 'PENDING';
  const isPaid = order.status === 'PAID' && !!order.transactionId;

  return (
    <div className="max-w-2xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-6">Detalhe do Pedido</h1>

      <div className="bg-white border border-gray-200 rounded-md p-6 space-y-4">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <span className="text-sm text-gray-500">ID do Pedido</span>
            <p className="font-mono text-sm mt-1">{order.orderId}</p>
          </div>
          <div>
            <span className="text-sm text-gray-500">Status</span>
            <p className="mt-1">
              <span
                className={`inline-block px-2 py-1 rounded-full text-xs font-medium ${statusColors[order.status] || 'bg-gray-100 text-gray-800'}`}
              >
                {statusLabels[order.status] || order.status}
              </span>
            </p>
          </div>
          <div>
            <span className="text-sm text-gray-500">Valor Total</span>
            <p className="text-lg font-bold mt-1">{formatBRL(order.totalInCents)}</p>
          </div>
          <div>
            <span className="text-sm text-gray-500">Criado em</span>
            <p className="text-sm mt-1">{formatDate(order.createdAt)}</p>
          </div>
          <div>
            <span className="text-sm text-gray-500">Atualizado em</span>
            <p className="text-sm mt-1">{formatDate(order.updatedAt)}</p>
          </div>
          {order.expiresAt && (
            <div>
              <span className="text-sm text-gray-500">Expira em</span>
              <p className="text-sm mt-1">{formatDate(order.expiresAt)}</p>
            </div>
          )}
        </div>

        {isPaid && (
          <div className="pt-2">
            <Button variant="primary" onClick={() => goTo(`/transactions/${order.transactionId}`)}>
              Ver Transação
            </Button>
          </div>
        )}
      </div>

      <div className="mt-6">
        <h2 className="text-lg font-semibold mb-3">Itens</h2>
        <div className="overflow-x-auto">
          <table className="min-w-full bg-white border border-gray-200 rounded-md">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-200">
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Produto</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Descrição</th>
                <th className="px-4 py-3 text-right text-sm font-medium text-gray-600">Qtd</th>
                <th className="px-4 py-3 text-right text-sm font-medium text-gray-600">Preço Unit.</th>
                <th className="px-4 py-3 text-right text-sm font-medium text-gray-600">Subtotal</th>
              </tr>
            </thead>
            <tbody>
              {order.items.map((item, idx) => (
                <tr key={idx} className="border-b border-gray-100">
                  <td className="px-4 py-3 text-sm font-mono">{item.productId}</td>
                  <td className="px-4 py-3 text-sm">{item.description}</td>
                  <td className="px-4 py-3 text-sm text-right">{item.quantity}</td>
                  <td className="px-4 py-3 text-sm text-right">{formatBRL(item.unitPriceInCents)}</td>
                  <td className="px-4 py-3 text-sm text-right font-medium">{formatBRL(item.subtotalInCents)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {isPending && (
        <div className="mt-6">
          {showConfirm ? (
            <div className="bg-yellow-50 border border-yellow-200 rounded-md p-4 space-y-3">
              <p className="text-sm font-medium text-yellow-800">Confirmar Cancelamento</p>
              <p className="text-sm text-yellow-700">Tem certeza que deseja cancelar este pedido?</p>
              {cancelError && (
                <p className="text-sm text-red-600">{cancelError}</p>
              )}
              <div className="flex gap-2">
                <Button variant="danger" onClick={handleCancel} loading={cancelling} disabled={cancelling}>
                  Confirmar
                </Button>
                <Button variant="secondary" onClick={() => { setShowConfirm(false); setCancelError(null); }} disabled={cancelling}>
                  Voltar
                </Button>
              </div>
            </div>
          ) : (
            <Button variant="danger" onClick={() => setShowConfirm(true)}>
              Cancelar Pedido
            </Button>
          )}
        </div>
      )}
    </div>
  );
}
