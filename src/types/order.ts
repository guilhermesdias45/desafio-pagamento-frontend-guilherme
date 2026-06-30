export type OrderStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'PAID'
  | 'CANCELLED'
  | 'REFUNDED'
  | 'PARTIALLY_REFUNDED';

export interface OrderItem {
  productId: string;
  description: string;
  quantity: number;
  unitPriceInCents: number;
}

export interface OrderItemDetail extends OrderItem {
  subtotalInCents: number;
}

export interface CreateOrderRequest {
  merchantId: string;
  items: OrderItem[];
}

export interface CreateOrderResponse {
  orderId: string;
  status: OrderStatus;
  totalInCents: number;
  items: OrderItemDetail[];
  expiresAt: string;
  createdAt: string;
}

export interface OrderDetail {
  orderId: string;
  customerId: string;
  merchantId: string;
  status: OrderStatus;
  totalInCents: number;
  items: OrderItemDetail[];
  transactionId?: string;
  createdAt: string;
  updatedAt: string;
  expiresAt?: string;
}

export interface OrderSummary {
  orderId: string;
  status: OrderStatus;
  totalInCents: number;
  createdAt: string;
}

export interface OrderFormItem {
  id: string;
  productId: string;
  description: string;
  quantity: number;
  unitPriceInCents: number;
}

export interface OrderFilters {
  status?: OrderStatus;
}
