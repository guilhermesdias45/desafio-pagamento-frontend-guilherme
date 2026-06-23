export interface MockOrderItem {
  id: string;
  name: string;
  quantity: number;
  unitPriceInCents: number;
}

export interface MockOrder {
  id: string;
  status: string;
  amountInCents: number;
  currency: string;
  items: MockOrderItem[];
  merchantId: string;
  customerId: string;
  createdAt: string;
}

export interface MockOrderService {
  getOrderById: (orderId: string) => Promise<MockOrder>;
}

export function createMockOrderService(overrides?: Partial<MockOrderService>): MockOrderService {
  return {
    getOrderById: () =>
      Promise.resolve({
        id: 'ord_123',
        status: 'PENDING',
        amountInCents: 123456,
        currency: 'BRL',
        items: [
          { id: 'item_1', name: 'Produto 1', quantity: 2, unitPriceInCents: 50000 },
          { id: 'item_2', name: 'Produto 2', quantity: 1, unitPriceInCents: 23456 },
        ],
        merchantId: 'merchant_1',
        customerId: 'cust_1',
        createdAt: new Date().toISOString(),
      }),
    ...overrides,
  };
}
