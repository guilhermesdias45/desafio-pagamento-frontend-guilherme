import type { MercadoPagoCardTokenRequest, MercadoPagoCardTokenResponse } from '@/types/checkout';

export interface MockMercadoPagoInstance {
  cardToken: (data: MercadoPagoCardTokenRequest) => Promise<MercadoPagoCardTokenResponse>;
}

export function createMockMercadoPagoInstance(
  overrides?: Partial<MockMercadoPagoInstance>,
): MockMercadoPagoInstance {
  return {
    cardToken: () =>
      Promise.resolve({
        id: 'tok_test_123',
        publicKey: 'TEST-123',
        status: 'active',
        cardholder: { name: 'Test User' },
      }),
    ...overrides,
  };
}
