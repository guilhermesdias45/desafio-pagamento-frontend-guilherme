import type { ApiClient } from '@/lib/api-client';
import type { CreateOrderRequest, CreateOrderResponse, OrderDetail, OrderSummary } from '@/types/order';

export interface PaginatedOrders {
  content: OrderSummary[];
  totalPages: number;
  totalElements: number;
  number: number;
}

export function createOrder(
  apiClient: ApiClient,
  data: CreateOrderRequest,
  merchantId: string,
) {
  const idempotencyKey = crypto.randomUUID();
  return apiClient.post<CreateOrderRequest, CreateOrderResponse>(
    '/api/v1/orders',
    { ...data, idempotencyKey },
    { merchantId, idempotencyKey },
  );
}

export function getOrder(apiClient: ApiClient, orderId: string) {
  return apiClient.get<OrderDetail>(`/api/v1/orders/${orderId}`);
}

export function listOrders(
  apiClient: ApiClient,
  params?: { status?: string; page?: number; size?: number },
) {
  return apiClient.get<PaginatedOrders>('/api/v1/orders', {
    params: params as Record<string, string | number | undefined>,
  });
}

export function cancelOrder(apiClient: ApiClient, orderId: string) {
  return apiClient.delete<void>(`/api/v1/orders/${orderId}`);
}
