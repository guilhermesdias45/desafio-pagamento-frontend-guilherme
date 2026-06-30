import { test, expect, Page } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const SCREENSHOT_DIR = path.resolve(__dirname, '..', 'docs', 'screenshots');

function b64(str: string): string {
  return Buffer.from(str).toString('base64').replace(/=+$/, '');
}

function createMockJwt(claims: Record<string, unknown>): string {
  const header = b64(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const payload = b64(JSON.stringify(claims));
  const signature = b64('fake-signature');
  return `${header}.${payload}.${signature}`;
}

const CUSTOMER_JWT = createMockJwt({
  sub: '550e8400-e29b-41d4-a716-446655440001',
  email: 'maria@exemplo.com.br',
  role: 'CUSTOMER',
  merchantId: null,
  iat: 1700000000,
  exp: 9999999999,
});

const MERCHANT_JWT = createMockJwt({
  sub: '550e8400-e29b-41d4-a716-446655440002',
  email: 'loja@exemplo.com.br',
  role: 'MERCHANT_OWNER',
  merchantId: '550e8400-e29b-41d4-a716-446655440002',
  iat: 1700000000,
  exp: 9999999999,
});

const NOW = new Date('2026-06-30T10:00:00Z').toISOString();

const MOCK_ORDER = {
  orderId: 'ord-a1b2c3d4',
  customerId: '550e8400-e29b-41d4-a716-446655440001',
  merchantId: '550e8400-e29b-41d4-a716-446655440002',
  status: 'PENDING',
  totalInCents: 149990,
  items: [
    { productId: 'PROD-001', description: 'Fone Bluetooth Pro', quantity: 1, unitPriceInCents: 99990, subtotalInCents: 99990 },
    { productId: 'PROD-002', description: 'Carregador USB-C 65W', quantity: 1, unitPriceInCents: 50000, subtotalInCents: 50000 },
  ],
  transactionId: null,
  createdAt: NOW,
  updatedAt: NOW,
  expiresAt: new Date('2026-06-30T10:30:00Z').toISOString(),
};

const MOCK_ORDER_PAID = {
  ...MOCK_ORDER,
  orderId: 'ord-paid-001',
  status: 'PAID',
  transactionId: 'tx-abc123',
};

const MOCK_ORDERS_LIST = {
  content: [
    { orderId: 'ord-a1b2c3d4', status: 'PENDING', totalInCents: 149990, createdAt: NOW },
    { orderId: 'ord-paid-001', status: 'PAID', totalInCents: 89990, createdAt: new Date('2026-06-29T14:00:00Z').toISOString() },
    { orderId: 'ord-canc-002', status: 'CANCELLED', totalInCents: 50000, createdAt: new Date('2026-06-28T09:00:00Z').toISOString() },
    { orderId: 'ord-ref-003', status: 'REFUNDED', totalInCents: 25000, createdAt: new Date('2026-06-27T11:00:00Z').toISOString() },
  ],
  totalPages: 1,
  totalElements: 4,
  number: 0,
};

const MOCK_TRANSACTIONS = {
  content: [
    { transactionId: 'tx-abc123', amountInCents: 149990, status: 'APPROVED', cardBrand: 'Visa', cardLastFour: '1234', customerId: 'user-001', createdAt: NOW },
    { transactionId: 'tx-def456', amountInCents: 89990, status: 'APPROVED', cardBrand: 'Mastercard', cardLastFour: '5678', customerId: 'user-002', createdAt: new Date('2026-06-29T14:00:00Z').toISOString() },
    { transactionId: 'tx-ghi789', amountInCents: 50000, status: 'REFUNDED', cardBrand: 'Visa', cardLastFour: '9012', customerId: 'user-003', createdAt: new Date('2026-06-28T09:00:00Z').toISOString() },
    { transactionId: 'tx-jkl012', amountInCents: 25000, status: 'PARTIALLY_REFUNDED', cardBrand: 'Elo', cardLastFour: '3456', customerId: 'user-004', createdAt: new Date('2026-06-27T11:00:00Z').toISOString() },
  ],
  totalPages: 1,
  totalElements: 4,
  number: 0,
};

const MOCK_TRANSACTION_DETAIL = {
  transactionId: 'tx-abc123',
  mpPaymentId: 'MP-987654321',
  amountInCents: 149990,
  currency: 'BRL',
  status: 'APPROVED',
  cardBrand: 'Visa',
  cardLastFour: '1234',
  orderId: 'ord-a1b2c3d4',
  customerId: '550e8400-e29b-41d4-a716-446655440001',
  merchantId: '550e8400-e29b-41d4-a716-446655440002',
  createdAt: NOW,
  updatedAt: NOW,
  refunds: [
    { refundId: 'ref-001', amountInCents: 50000, reason: 'Cancelamento solicitado pelo cliente' },
  ],
};

const MOCK_2FA_SETUP = {
  data: {
    secret: 'JBSWY3DPEHPK3PXP',
    qrCodeUrl: 'https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=otpauth://totp/AcabouOMony:user@test.com?secret=JBSWY3DPEHPK3PXP&issuer=AcabouOMony',
    otpAuthUrl: 'otpauth://totp/AcabouOMony:user@test.com?secret=JBSWY3DPEHPK3PXP&issuer=AcabouOMony',
    recoveryCodes: ['1234-5678-9012-3456', '2345-6789-0123-4567', '3456-7890-1234-5678', '4567-8901-2345-6789', '5678-9012-3456-7890'],
  },
  meta: { timestamp: NOW, requestId: 'mock-2fa-setup' },
  errors: [],
};

async function mockAllApi(page: Page, handler: (route: any) => void) {
  await page.route('**/api/v1/**', handler);
}

async function guestSetup(page: Page) {
  await mockAllApi(page, async (route) => {
    const url = route.request().url();
    if (url.includes('/api/v1/auth/refresh')) {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ errors: [{ code: 'TOKEN_EXPIRED', message: 'Token expirado', retryable: false }] }),
      });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({}) });
    }
  });
}

async function customerSetup(page: Page) {
  await mockAllApi(page, async (route) => {
    const url = route.request().url();
    if (url.includes('/api/v1/auth/refresh')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ accessToken: CUSTOMER_JWT }) });
    } else if (url.includes('/api/v1/orders') && !url.match(/\/orders\/ord-/)) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_ORDERS_LIST) });
    } else if (url.match(/\/api\/v1\/orders\/ord-/)) {
      const orderId = url.match(/\/orders\/(ord-[^/?]+)/)?.[1];
      const data = orderId === 'ord-paid-001' ? MOCK_ORDER_PAID : MOCK_ORDER;
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) });
    } else if (url.includes('/api/v1/auth/2fa/setup')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_2FA_SETUP) });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({}) });
    }
  });
}

async function merchantSetup(page: Page) {
  await mockAllApi(page, async (route) => {
    const url = route.request().url();
    if (url.includes('/api/v1/auth/refresh')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ accessToken: MERCHANT_JWT }) });
    } else if (url.includes('/api/v1/transactions') && !url.match(/\/transactions\/tx-/)) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_TRANSACTIONS) });
    } else if (url.match(/\/api\/v1\/transactions\/tx-/)) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_TRANSACTION_DETAIL) });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({}) });
    }
  });
}

async function takeScreenshot(page: Page, name: string) {
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: path.join(SCREENSHOT_DIR, `${name}.png`), fullPage: true });
}

test.describe.serial('Screenshots - Guest Pages', () => {
  test.beforeEach(async ({ page }) => {
    await guestSetup(page);
  });

  test('01 - Login Page', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('h1')).toContainText('Entrar');
    await takeScreenshot(page, '01-login');
  });

  test('02 - Register Page', async ({ page }) => {
    await page.goto('/register');
    await expect(page.locator('h1')).toContainText('Criar Conta');
    await takeScreenshot(page, '02-register');
  });

  test('03 - Confirm Email Page', async ({ page }) => {
    await page.goto('/confirm-email');
    await expect(page.locator('h1')).toContainText('Confirmar Email');
    await takeScreenshot(page, '03-confirm-email');
  });

  test('04 - 2FA Verify Page', async ({ page }) => {
    await page.goto('/2fa/verify');
    await expect(page.locator('h1')).toContainText('Verificação em Duas Etapas');
    await takeScreenshot(page, '04-2fa-verify');
  });

  test('05 - 2FA Setup (Guest)', async ({ page }) => {
    await page.goto('/2fa/setup');
    await page.waitForTimeout(2000);
    await takeScreenshot(page, '05-2fa-setup-guest');
  });
});

test.describe.serial('Screenshots - Customer Pages', () => {
  test.beforeEach(async ({ page }) => {
    await customerSetup(page);
  });

  test('06 - Create Order (Home)', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('main h1')).toContainText('Criar Pedido');
    await takeScreenshot(page, '06-create-order');
  });

  test('07 - Order History', async ({ page }) => {
    await page.goto('/orders');
    await expect(page.locator('main h1')).toContainText('Histórico de Pedidos');
    await takeScreenshot(page, '07-order-history');
  });

  test('08 - Order Detail (PENDING)', async ({ page }) => {
    await page.goto('/orders/ord-a1b2c3d4');
    await expect(page.locator('main h1')).toContainText('Detalhe do Pedido');
    await takeScreenshot(page, '08-order-detail');
  });

  test('09 - Checkout Page', async ({ page }) => {
    await page.goto('/checkout?orderId=ord-a1b2c3d4');
    await expect(page.locator('main h1')).toContainText('Pagamento');
    await takeScreenshot(page, '09-checkout');
  });

  test('10 - Payment Result (Approved)', async ({ page }) => {
    await page.goto('/orders');
    await page.waitForTimeout(1000);
    await page.evaluate((data) => {
      window.history.pushState(data, '', '/checkout/result');
      window.dispatchEvent(new PopStateEvent('popstate', { state: data }));
    }, {
      result: {
        status: 'APPROVED',
        amountInCents: 149990,
        transactionId: 'tx-abc123',
        orderId: 'ord-a1b2c3d4',
        processingTimeMs: 1234,
        errorCode: null,
        errorMessage: null,
        retryable: false,
      },
    });
    await page.waitForTimeout(1000);
    await takeScreenshot(page, '10-payment-result-approved');
  });

  test('11 - Security (2FA Setup Auth)', async ({ page }) => {
    await page.goto('/security');
    await page.waitForTimeout(2000);
    await takeScreenshot(page, '11-security-2fa');
  });
});

test.describe.serial('Screenshots - Merchant Pages', () => {
  test.beforeEach(async ({ page }) => {
    await merchantSetup(page);
  });

  test('12 - Transactions List', async ({ page }) => {
    await page.goto('/merchant/transactions');
    await expect(page.locator('main h1')).toContainText('Transações');
    await takeScreenshot(page, '12-merchant-transactions');
  });

  test('13 - Transaction Detail', async ({ page }) => {
    await page.goto('/merchant/transactions/tx-abc123');
    await expect(page.locator('main h1')).toContainText('Detalhes da Transação');
    await takeScreenshot(page, '13-merchant-transaction-detail');
  });
});
