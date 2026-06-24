import { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { ApiClient } from '@/lib/api-client';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorMessage } from '@/components/ui/ErrorMessage';
import { CardForm } from '@/pages/checkout/CardForm';
import type { OrderDetail } from '@/types/order';
import type { PaymentResultData } from '@/types/checkout';

export function CheckoutPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const orderId = searchParams.get('orderId') || '';

  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!orderId) {
      setError('Pedido não encontrado');
      setLoading(false);
      return;
    }

    fetchOrder();
  }, [orderId]);

  const fetchOrder = async () => {
    setLoading(true);
    setError(null);

    try {
      const apiClient = new ApiClient(() => null);
      const result = await apiClient.get<OrderDetail>(`/api/v1/orders/${orderId}`);
      setOrder(result);
    } catch (err: unknown) {
      const apiErr = err as { status?: number; errors?: Array<{ code: string; message: string }> };
      if (apiErr.errors && apiErr.errors.length > 0) {
        setError(apiErr.errors[0].message || 'Erro ao carregar pedido');
      } else {
        setError('Erro de conexão. Verifique sua internet.');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrder();
  }, [orderId]);

  const handlePaymentComplete = (result: PaymentResultData) => {
    if (result.status === 'APPROVED') {
      navigate(`/checkout/result?transactionId=${result.transactionId}&status=APPROVED&orderId=${orderId}`, { replace: true });
    } else {
      navigate(`/checkout/result?transactionId=${result.transactionId}&status=FAILURE&orderId=${orderId}`, { replace: true });
    }
  };

  const handleError = (error: string) => {
    setError(error);
  };

  if (loading) {
    return (
      <div className="flex justify-center py-12" role="status">
        <Spinner size="lg" />
      </div>
    );
  }

  if (error && !order) {
    return (
      <div className="max-w-2xl mx-auto p-6">
        <ErrorMessage title="Erro ao carregar pedido" message={error} onRetry={fetchOrder} />
        <button onClick={fetchOrder} className="mt-4 px-4 py-2 bg-blue-500 text-white rounded">
          Tentar novamente
        </button>
      </div>
    );
  }

  if (!order) {
    return null;
  }

  return (
    <div className="max-w-3xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-6">Pagamento</h1>

      <div className="bg-white border border-gray-200 rounded-md p-6 mb-6">
        <h2 className="text-lg font-semibold mb-4">Resumo do Pedido</h2>

        <div className="grid grid-cols-2 gap-4 mb-4">
          <div>
            <span className="text-sm text-gray-500">ID do Pedido</span>
            <p className="font-mono text-sm mt-1">{order.orderId}</p>
          </div>
          <div>
            <span className="text-sm text-gray-500">Status</span>
            <p className="mt-1">
              <span className={`inline-block px-2 py-1 rounded-full text-xs font-medium
                ${order.status === 'PAID' ? 'bg-green-100 text-green-800' :
                  order.status === 'PENDING' ? 'bg-yellow-100 text-yellow-800' :
                  order.status === 'PROCESSING' ? 'bg-blue-100 text-blue-800' :
                  order.status === 'CANCELLED' ? 'bg-red-100 text-red-800' :
                  order.status === 'REFUNDED' ? 'bg-orange-100 text-orange-800' :
                  'bg-gray-100 text-gray-800'}
              `}>
                {order.status === 'PAID' ? 'Pago' :
                 order.status === 'PENDING' ? 'Pendente' :
                 order.status === 'PROCESSING' ? 'Processando' :
                 order.status === 'CANCELLED' ? 'Cancelado' :
                 order.status === 'REFUNDED' ? 'Estornado' :
                 order.status}
              </span>
            </p>
          </div>
        </div>

        <div className="mb-4">
          <span className="text-sm text-gray-500">Total</span>
          <p className="text-xl font-bold mt-1">
            {new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(order.totalInCents / 100)}
          </p>
        </div>

        {order.items.length > 0 && (
          <div>
            <h3 className="text-sm font-medium text-gray-700 mb-2">Itens</h3>
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
                      <td className="px-4 py-3 text-sm text-right">
                        {new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(item.unitPriceInCents / 100)}
                      </td>
                      <td className="px-4 py-3 text-sm text-right font-medium">
                        {new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(item.subtotalInCents / 100)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>

      {order.status === 'PENDING' ? (
        <CardForm
          orderId={orderId}
          amountInCents={order.totalInCents}
          customerId={order.customerId}
          merchantId={order.merchantId}
          authToken={null}
          onPaymentComplete={handlePaymentComplete}
          onError={handleError}
        />
      ) : (
        <div className="bg-yellow-50 border border-yellow-200 rounded-md p-4">
          <p className="text-sm text-yellow-800">
            Este pedido não está mais pendente. Status atual: {order.status === 'PAID' ? 'Pago' : order.status === 'CANCELLED' ? 'Cancelado' : order.status}
          </p>
        </div>
      )}
    </div>
  );
}
