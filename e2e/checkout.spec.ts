import { test, expect, Page } from '@playwright/test';

const MOCK_USER = {
  id: 'user-123',
  email: 'user@test.com',
  fullName: 'Test User',
  role: 'CUSTOMER',
  merchantId: null,
  emailVerified: true,
  twoFactorEnabled: false,
  createdAt: '2024-01-01T00:00:00Z',
};

async function mockAuthenticatedSession(page: Page) {
  await page.route('**/api/v1/auth/refresh', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          accessToken: 'eyJhbGciOiJIUzI1NiJ9.mock',
          tokenType: 'Bearer',
          expiresIn: 900,
          user: MOCK_USER,
        },
        meta: { timestamp: new Date().toISOString(), requestId: 'mock-refresh' },
        errors: [],
      }),
    });
  });

  await page.route('**/api/v1/auth/logout', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
  });
}

test.describe('Checkout Flow', () => {
  test.beforeEach(async ({ page }) => {
    await mockAuthenticatedSession(page);
  });

  test('renders checkout page for authenticated user', async ({ page }) => {
    await page.goto('/checkout');
    await expect(page).toHaveURL(/\/checkout/);
  });
});
