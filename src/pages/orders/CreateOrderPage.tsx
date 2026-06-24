import React, { useState, useMemo, useCallback, useRef } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import { ApiClient } from '@/lib/api-client';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Spinner } from '@/components/ui/Spinner';
import type { CreateOrderRequest, CreateOrderResponse } from '@/types/order';

interface Props {
  apiClient?: ApiClient;
  navigate?: (path: string) => void;
}

const formatBRL = (cents: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);

interface ItemRow {
  id: string;
  productId: string;
  description: string;
  quantity: number;
  unitPriceInCents: number;
}

export function CreateOrderPage({ apiClient: externalClient, navigate }: Props) {
  const itemIdCounter = useRef(0);
  const { user, token } = useAuth();
  const apiClient = useMemo(() => externalClient ?? new ApiClient(() => token), [externalClient, token]);
  const goTo = useCallback((path: string) => {
    if (navigate) navigate(path);
    else window.location.href = path;
  }, [navigate]);

  const [merchantId, setMerchantId] = useState('');
  const [items, setItems] = useState<ItemRow[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [inlineErrors, setInlineErrors] = useState<Record<string, string>>({});

  const addItem = () => {
    const id = `item_${++itemIdCounter.current}`;
    setItems((prev) => [
      ...prev,
      { id, productId: '', description: '', quantity: 1, unitPriceInCents: 0 },
    ]);
    setError(null);
  };

  const removeItem = (id: string) => {
    setItems((prev) => prev.filter((i) => i.id !== id));
  };

  const updateItem = (id: string, field: keyof ItemRow, value: string | number) => {
    setItems((prev) => prev.map((i) => (i.id === id ? { ...i, [field]: value } : i)));
  };

  const subtotals = useMemo(
    () => items.map((i) => ({ id: i.id, subtotal: i.quantity * i.unitPriceInCents })),
    [items],
  );

  const total = useMemo(
    () => subtotals.reduce((sum, s) => sum + s.subtotal, 0),
    [subtotals],
  );

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setInlineErrors({});

    if (!merchantId.trim()) {
      setError('Selecione um merchant');
      return;
    }

    if (items.length === 0) {
      setError('Adicione pelo menos um item ao pedido');
      return;
    }

    const newInlineErrors: Record<string, string> = {};
    for (const item of items) {
      if (item.unitPriceInCents < 1) {
        newInlineErrors[item.id] = `Preço inválido para o item ${item.description || item.productId || 'sem descrição'}`;
      }
      if (item.quantity < 1 || item.quantity > 999) {
        newInlineErrors[item.id] = `Quantidade inválida para o item ${item.description || item.productId || 'sem descrição'}`;
      }
    }

    if (Object.keys(newInlineErrors).length > 0) {
      setInlineErrors(newInlineErrors);
      return;
    }

    setSubmitting(true);

    try {
      const idempotencyKey = crypto.randomUUID();
      const response = await apiClient.post<CreateOrderRequest, CreateOrderResponse>(
        '/api/v1/orders',
        {
          customerId: user?.id ?? '',
          merchantId,
          items: items.map(({ id, ...rest }) => rest),
          idempotencyKey,
        },
        { merchantId, idempotencyKey },
      );

      if (response?.data?.orderId) {
        goTo(`/orders/${response.data.orderId}`);
      }
    } catch (err: unknown) {
      const apiErr = err as { status?: number; errors?: Array<{ code: string; message: string }> };
      if (apiErr.errors && apiErr.errors.length > 0) {
        const code = apiErr.errors[0].code;
        const message = apiErr.errors[0].message;

        if (code === 'DUPLICATE_ORDER' && apiErr.status === 409) {
          if (message) {
            const orderId = message.includes(': ') ? message.split(': ').pop()?.trim() : message;
            if (orderId) {
              goTo(`/orders/${orderId}`);
              return;
            }
          }
          setError('Pedido já processado');
        } else if (code === 'EMPTY_ORDER') {
          setError('Adicione pelo menos um item ao pedido');
        } else if (code === 'MERCHANT_NOT_FOUND') {
          setError('Merchant não encontrado. Verifique o CNPJ ou selecione outro.');
        } else if (code === 'TOTAL_EXCEEDS_LIMIT') {
          setError('Valor total do pedido excede o limite de R$ 9.999,99');
        } else if (code === 'INVALID_ITEM_PRICE' || code === 'INVALID_QUANTITY') {
          setError(message || 'Erro de validação nos itens');
        } else if (code === 'DUPLICATE_ORDER' && apiErr.status === 200) {
          if (message) setError('Pedido já processado');
        } else {
          setError(message || 'Erro ao criar pedido');
        }
      } else {
        setError('Erro de conexão. Verifique sua internet.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-6">Criar Pedido</h1>

      <form onSubmit={handleSubmit} className="space-y-6">
        <div>
          <Input
            label="ID do Merchant"
            placeholder="Digite o ID do merchant"
            value={merchantId}
            onChange={(e) => setMerchantId(e.target.value)}
            disabled={submitting}
          />
        </div>

        <div>
          <div className="flex items-center justify-between mb-2">
            <h2 className="text-lg font-semibold">Itens</h2>
            <Button type="button" variant="secondary" onClick={addItem} disabled={submitting}>
              Adicionar item
            </Button>
          </div>

          {items.length === 0 && (
            <p className="text-gray-500 text-sm py-4">Nenhum item adicionado. Clique em "Adicionar item" para começar.</p>
          )}

          <div className="space-y-3">
            {items.map((item) => (
              <div key={item.id} className="border border-gray-200 rounded-md p-3 space-y-2">
                <div className="grid grid-cols-2 gap-2">
                  <Input
                    label="Produto"
                    placeholder="ID do produto"
                    value={item.productId}
                    onChange={(e) => updateItem(item.id, 'productId', e.target.value)}
                    disabled={submitting}
                  />
                  <Input
                    label="Descrição"
                    placeholder="Descrição do item"
                    value={item.description}
                    onChange={(e) => updateItem(item.id, 'description', e.target.value)}
                    disabled={submitting}
                  />
                </div>
                <div className="grid grid-cols-3 gap-2">
                  <div className="flex flex-col gap-1">
                    <label className="text-sm font-medium text-gray-700">Quantidade</label>
                    <input
                      type="number"
                      min={1}
                      max={999}
                      value={item.quantity || ''}
                      onChange={(e) => updateItem(item.id, 'quantity', Math.max(1, parseInt(e.target.value) || 1))}
                      className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
                      disabled={submitting}
                    />
                  </div>
                  <div className="flex flex-col gap-1">
                    <label className="text-sm font-medium text-gray-700">Preço unitário (centavos)</label>
                    <input
                      type="text"
                      inputMode="numeric"
                      value={item.unitPriceInCents || ''}
                      onChange={(e) => updateItem(item.id, 'unitPriceInCents', parseInt(e.target.value) || 0)}
                      className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
                      disabled={submitting}
                    />
                  </div>
                  <div className="flex flex-col gap-1 justify-end">
                    <span className="text-sm font-medium text-gray-700">Subtotal</span>
                    <span className="text-lg font-semibold text-gray-900">
                      {formatBRL(item.quantity * item.unitPriceInCents)}
                    </span>
                  </div>
                </div>
                {inlineErrors[item.id] && (
                  <p className="text-sm text-red-600">{inlineErrors[item.id]}</p>
                )}
                <Button type="button" variant="danger" onClick={() => removeItem(item.id)} disabled={submitting}>
                  Remover
                </Button>
              </div>
            ))}
          </div>

          {items.length >= 2 && (
            <div className="mt-4 p-3 bg-gray-50 rounded-md text-right">
              <span className="text-lg font-bold text-gray-900">Total: {formatBRL(total)}</span>
            </div>
          )}
        </div>

        {error && (
          <div className="bg-red-50 border border-red-200 rounded-md p-3">
            <p className="text-sm text-red-700">{error}</p>
          </div>
        )}

        <Button type="submit" fullWidth loading={submitting} disabled={submitting}>
          Criar Pedido
        </Button>
      </form>
    </div>
  );
}
