import { useState, useEffect, useMemo, useCallback } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import { ApiClient } from '@/lib/api-client';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import type { TransactionSummary, PaginatedResponse } from '@/types/merchant';
import { STATUS_BADGE_CLASSES, STATUS_LABELS } from '@/types/merchant';

interface Props {
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

const truncate = (str: string, max: number) =>
  str.length > max ? str.substring(0, max) + '\u2026' : str;

export function TransactionsListPage({ apiClient: externalClient, navigate }: Props) {
  const { token } = useAuth();
  const apiClient = useMemo(() => externalClient ?? new ApiClient(() => token), [externalClient, token]);
  const goTo = useCallback((path: string) => {
    if (navigate) navigate(path);
    else window.location.href = path;
  }, [navigate]);

  const [transactions, setTransactions] = useState<TransactionSummary[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchTransactions = useCallback(async (page: number) => {
    setLoading(true);
    setError(null);

    try {
      const params: Record<string, string | number | undefined> = {
        page,
        size: 20,
        sort: 'createdAt,desc',
      };

      const result = await apiClient.get<PaginatedResponse<TransactionSummary>>('/api/v1/transactions', { params });

      setTransactions(result.content);
      setTotalPages(result.totalPages);
      setCurrentPage(result.number);
    } catch (err: unknown) {
      const apiErr = err as { status?: number; errors?: Array<{ code: string; message: string }> };
      if (apiErr.errors && apiErr.errors.length > 0) {
        setError(apiErr.errors[0].message || 'Erro ao carregar transações');
      } else {
        setError('Erro de conexão. Verifique sua internet.');
      }
    } finally {
      setLoading(false);
    }
  }, [apiClient]);

  useEffect(() => {
    fetchTransactions(0);
  }, []);

  const handlePrevPage = () => {
    if (currentPage > 0) {
      const newPage = currentPage - 1;
      setCurrentPage(newPage);
      fetchTransactions(newPage);
    }
  };

  const handleNextPage = () => {
    if (currentPage < totalPages - 1) {
      const newPage = currentPage + 1;
      setCurrentPage(newPage);
      fetchTransactions(newPage);
    }
  };

  const handleRetry = () => {
    fetchTransactions(currentPage);
  };

  if (error && transactions.length === 0) {
    return (
      <div className="max-w-5xl mx-auto p-6">
        <h1 className="text-2xl font-bold mb-6">Transações</h1>
        <ErrorMessage title="Erro ao carregar transações" message={error} onRetry={handleRetry} />
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-6">Transações</h1>

      {loading ? (
        <div className="flex justify-center py-12" role="status">
          <Spinner size="lg" />
        </div>
      ) : transactions.length === 0 ? (
        <div className="text-center py-12 text-gray-500">
          <p className="text-lg">Nenhuma transação encontrada</p>
        </div>
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="min-w-full bg-white border border-gray-200 rounded-md">
              <thead>
                <tr className="bg-gray-50 border-b border-gray-200">
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Transação</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Valor</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Status</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Cartão</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Cliente</th>
                  <th className="px-4 py-3 text-right text-sm font-medium text-gray-600">Data</th>
                </tr>
              </thead>
              <tbody>
                {transactions.map((tx) => (
                  <tr
                    key={tx.transactionId}
                    className="border-b border-gray-100 hover:bg-gray-50 cursor-pointer transition-colors"
                    onClick={() => goTo(`/merchant/transactions/${tx.transactionId}`)}
                  >
                    <td className="px-4 py-3 text-sm font-mono text-primary">
                      {truncate(tx.transactionId, 12)}
                    </td>
                    <td className="px-4 py-3 text-sm font-medium">
                      {formatBRL(tx.amountInCents)}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-block px-2 py-1 rounded-full text-xs font-medium ${STATUS_BADGE_CLASSES[tx.status] || 'bg-gray-100 text-gray-800'}`}
                      >
                        {STATUS_LABELS[tx.status] || tx.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-700">
                      {tx.cardBrand} **** {tx.cardLastFour}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600 font-mono">
                      {truncate(tx.customerId, 8)}
                    </td>
                    <td className="px-4 py-3 text-sm text-right text-gray-600">
                      {formatDate(tx.createdAt)}
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
                disabled={currentPage === 0 || loading}
                className="px-4 py-2 text-sm font-medium rounded-md border border-gray-300 disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50"
              >
                Anterior
              </button>
              <span className="text-sm text-gray-600">
                Página {currentPage + 1} de {totalPages}
              </span>
              <button
                onClick={handleNextPage}
                disabled={currentPage >= totalPages - 1 || loading}
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
