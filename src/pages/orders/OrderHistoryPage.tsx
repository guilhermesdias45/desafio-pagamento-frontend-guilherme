import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import { ApiClient } from '@/lib/api-client';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import type { OrderSummary, OrderStatus } from '@/types/order';

interface Props {
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

const statusOptions = [
  { value: '', label: 'Todos' },
  { value: 'PENDING', label: 'PENDING' },
  { value: 'PROCESSING', label: 'PROCESSING' },
  { value: 'PAID', label: 'PAID' },
  { value: 'CANCELLED', label: 'CANCELLED' },
  { value: 'REFUNDED', label: 'REFUNDED' },
];

const formatDate = (iso: string) =>
  new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(iso));

export function OrderHistoryPage({ apiClient: externalClient, navigate }: Props) {
  const { token } = useAuth();
  const apiClient = useMemo(() => externalClient ?? new ApiClient(() => token), [externalClient, token]);
  const goTo = useCallback((path: string) => {
    if (navigate) navigate(path);
    else window.location.href = path;
  }, [navigate]);

  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchOrders = useCallback(async (page: number, status: string) => {
    setLoading(true);
    setError(null);

    try {
      const params: Record<string, string | number | undefined> = {
        page,
        size: 20,
        sort: 'createdAt,desc',
        ...(status ? { status } : {}),
      };

      const result = await apiClient.get<{
        content: OrderSummary[];
        totalPages: number;
        totalElements: number;
        number: number;
      }>('/api/v1/orders', { params });

      setOrders(result.content);
      setTotalPages(result.totalPages);
      setCurrentPage(result.number);
    } catch (err: unknown) {
      const apiErr = err as { status?: number; errors?: Array<{ code: string; message: string }> };
      if (apiErr.errors && apiErr.errors.length > 0) {
        setError(apiErr.errors[0].message || 'Erro ao carregar pedidos');
      } else {
        setError('Erro de conexão. Verifique sua internet.');
      }
    } finally {
      setLoading(false);
    }
  }, [apiClient]);

  useEffect(() => {
    fetchOrders(currentPage, statusFilter);
  }, []);

  const handleStatusChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const newStatus = e.target.value;
    setStatusFilter(newStatus);
    setCurrentPage(0);
    fetchOrders(0, newStatus);
  };

  const handlePrevPage = () => {
    if (currentPage > 0) {
      const newPage = currentPage - 1;
      setCurrentPage(newPage);
      fetchOrders(newPage, statusFilter);
    }
  };

  const handleNextPage = () => {
    if (currentPage < totalPages - 1) {
      const newPage = currentPage + 1;
      setCurrentPage(newPage);
      fetchOrders(newPage, statusFilter);
    }
  };

  const handleRetry = () => {
    fetchOrders(currentPage, statusFilter);
  };

  if (error && orders.length === 0) {
    return (
      <div className="max-w-4xl mx-auto p-6">
        <h1 className="text-2xl font-bold mb-6">Histórico de Pedidos</h1>
        <ErrorMessage title="Erro ao carregar pedidos" message={error} onRetry={handleRetry} />
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-6">Histórico de Pedidos</h1>

      <div className="mb-4">
        <label className="block text-sm font-medium text-gray-700 mb-1">Filtrar por status</label>
        <select
          value={statusFilter}
          onChange={handleStatusChange}
          className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
          role="combobox"
        >
          {statusOptions.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      </div>

      {loading ? (
        <div className="flex justify-center py-12" role="status">
          <Spinner size="lg" />
        </div>
      ) : orders.length === 0 ? (
        <div className="text-center py-12 text-gray-500">
          <p className="text-lg">Nenhum pedido encontrado</p>
        </div>
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="min-w-full bg-white border border-gray-200 rounded-md">
              <thead>
                <tr className="bg-gray-50 border-b border-gray-200">
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Pedido</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Status</th>
                  <th className="px-4 py-3 text-right text-sm font-medium text-gray-600">Valor</th>
                  <th className="px-4 py-3 text-right text-sm font-medium text-gray-600">Data</th>
                </tr>
              </thead>
              <tbody>
                {orders.map((order) => (
                  <tr
                    key={order.orderId}
                    className="border-b border-gray-100 hover:bg-gray-50 cursor-pointer transition-colors"
                    onClick={() => goTo(`/orders/${order.orderId}`)}
                  >
                    <td className="px-4 py-3 text-sm font-mono text-primary">
                      {order.orderId}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-block px-2 py-1 rounded-full text-xs font-medium ${statusColors[order.status] || 'bg-gray-100 text-gray-800'}`}
                      >
                        {order.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-sm text-right font-medium">
                      {formatBRL(order.totalInCents)}
                    </td>
                    <td className="px-4 py-3 text-sm text-right text-gray-600">
                      {formatDate(order.createdAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-4 mt-6">
              <button
                onClick={handlePrevPage}
                disabled={currentPage === 0}
                className="px-4 py-2 text-sm font-medium rounded-md border border-gray-300 disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50"
              >
                Anterior
              </button>
              <span className="text-sm text-gray-600">
                Página {currentPage + 1} de {totalPages}
              </span>
              <button
                onClick={handleNextPage}
                disabled={currentPage >= totalPages - 1}
                className="px-4 py-2 text-sm font-medium rounded-md border border-gray-300 disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50"
              >
                Próximo
              </button>
            </div>
          )}

          {error && (
            <div className="mt-4">
              <ErrorMessage title="Erro" message={error} onRetry={handleRetry} />
            </div>
          )}
        </>
      )}
    </div>
  );
}
