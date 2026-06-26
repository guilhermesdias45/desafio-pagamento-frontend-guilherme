import { test, expect, Page } from '@playwright/test';

const API_BASE = 'http://localhost:8080';

async function mockRefresh(page: Page, status = 401) {
  await page.route(`${API_BASE}/api/v1/auth/refresh`, async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify({
        data: null,
        meta: { timestamp: new Date().toISOString(), requestId: 'mock-refresh' },
        errors: status === 401 ? [{ code: 'TOKEN_EXPIRED', message: 'Token expirado', retryable: false }] : [],
      }),
    });
  });
}

async function mockLogin(page: Page, status = 200, overrides: Record<string, unknown> = {}) {
  await page.route(`${API_BASE}/api/v1/auth/login`, async (route) => {
    const body = JSON.stringify({
      data: {
        accessToken: 'eyJhbGciOiJIUzI1NiJ9.mock',
        tokenType: 'Bearer',
        expiresIn: 900,
        requiresTwoFactor: false,
        ...overrides,
      },
      meta: { timestamp: new Date().toISOString(), requestId: 'mock-login' },
      errors: [],
    });
    await route.fulfill({ status, contentType: 'application/json', body });
  });
}

test.describe('Login Page', () => {
  test.beforeEach(async ({ page }) => {
    await mockRefresh(page, 401);
  });

  test('renders login form', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('h1')).toContainText('Entrar');
    await expect(page.locator('#email')).toBeVisible();
    await expect(page.locator('#password')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();
  });

  test('shows error on invalid credentials', async ({ page }) => {
    await mockLogin(page, 401, {});
    await page.goto('/login');
    await page.fill('#email', 'wrong@email.com');
    await page.fill('#password', 'wrongpass');
    await page.getByRole('button', { name: 'Entrar' }).click();
    await expect(page.locator('text=Email ou senha inválidos')).toBeVisible();
  });

  test('redirects to home on successful login', async ({ page }) => {
    await mockLogin(page, 200);
    await page.goto('/login');
    await page.fill('#email', 'user@test.com');
    await page.fill('#password', 'correctpass');
    await page.getByRole('button', { name: 'Entrar' }).click();
    await expect(page).toHaveURL(/\/$/);
  });

  test('redirects to 2FA when required', async ({ page }) => {
    await mockLogin(page, 200, {
      requiresTwoFactor: true,
      twoFactorToken: 'mock-totp-token',
    });
    await page.goto('/login');
    await page.fill('#email', 'user@test.com');
    await page.fill('#password', 'correctpass');
    await page.getByRole('button', { name: 'Entrar' }).click();
    await expect(page).toHaveURL(/\/2fa\/verify/);
  });

  test('shows error on network failure', async ({ page }) => {
    await page.route(`${API_BASE}/api/v1/auth/login`, async (route) => {
      await route.abort('connectionrefused');
    });
    await page.goto('/login');
    await page.fill('#email', 'user@test.com');
    await page.fill('#password', 'test123');
    await page.getByRole('button', { name: 'Entrar' }).click();
    await expect(page.locator('text=Erro de conexão')).toBeVisible();
  });
});
