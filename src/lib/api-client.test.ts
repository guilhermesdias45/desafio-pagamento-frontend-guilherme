import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ApiClient, ApiError } from './api-client';

const mockToken = 'test-jwt-token';
let client: ApiClient;

function mockFetch(response: Partial<Response>, body?: unknown) {
  return vi.mocked(global.fetch).mockResolvedValueOnce({
    ok: response.ok ?? true,
    status: response.status ?? 200,
    headers: new Headers(response.headers ?? {}),
    json: async () => body,
    ...response,
  } as Response);
}

describe('ApiClient', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    client = new ApiClient(() => mockToken);
    global.fetch = vi.fn();
  });

  describe('Authorization header', () => {
    it('injects Authorization Bearer header', async () => {
      mockFetch({ ok: true, status: 200 }, { data: null, errors: [] });
      await client.get('/api/v1/users/me');
      expect(fetch).toHaveBeenCalledWith(
        '/api/v1/users/me',
        expect.objectContaining({
          headers: expect.objectContaining({
            Authorization: 'Bearer test-jwt-token',
          }),
        }),
      );
    });

    it('does not add Authorization when token is null', async () => {
      client = new ApiClient(() => null);
      mockFetch({ ok: true, status: 200 }, { data: null, errors: [] });
      await client.get('/api/v1/public/health');
      const call = vi.mocked(fetch).mock.calls[0][1] as RequestInit;
      const headers = call.headers as Record<string, string>;
      expect(headers?.Authorization).toBeUndefined();
    });
  });

  describe('Content-Type header', () => {
    it('auto-sets Content-Type: application/json for POST with body', async () => {
      mockFetch({ ok: true, status: 200 }, { data: null, errors: [] });
      await client.post('/api/v1/auth/login', { email: 'test@test.com' });
      expect(fetch).toHaveBeenCalledWith(
        '/api/v1/auth/login',
        expect.objectContaining({
          headers: expect.objectContaining({
            'Content-Type': 'application/json',
          }),
        }),
      );
    });
  });

  describe('Idempotency-Key header', () => {
    it('auto-generates Idempotency-Key for POST requests', async () => {
      mockFetch({ ok: true, status: 200 }, { data: null, errors: [] });
      await client.post('/api/v1/orders', { amount: 100 });
      const call = vi.mocked(fetch).mock.calls[0][1] as RequestInit;
      const headers = call.headers as Record<string, string>;
      expect(headers['Idempotency-Key']).toBeDefined();
      expect(headers['Idempotency-Key']).toMatch(
        /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
      );
    });
  });

  describe('X-Merchant-Id header', () => {
    it('adds X-Merchant-Id when provided in options', async () => {
      mockFetch({ ok: true, status: 200 }, { data: null, errors: [] });
      await client.get('/api/v1/transactions', { merchantId: 'merchant_123' });
      const call = vi.mocked(fetch).mock.calls[0][1] as RequestInit;
      const headers = call.headers as Record<string, string>;
      expect(headers['X-Merchant-Id']).toBe('merchant_123');
    });
  });

  describe('Error handling', () => {
    it('throws ApiError on 4xx response', async () => {
      const errorBody = {
        data: null,
        errors: [
          { code: 'INVALID_CREDENTIALS', message: 'Email ou senha inválidos', retryable: false },
        ],
      };
      mockFetch({ ok: false, status: 401 }, errorBody);
      await expect(client.post('/api/v1/auth/login', {})).rejects.toThrow(ApiError);
    });

    it('ApiError has status field', async () => {
      const errorBody = {
        data: null,
        errors: [{ code: 'NOT_FOUND', message: 'Not found', retryable: false }],
      };
      mockFetch({ ok: false, status: 404 }, errorBody);
      try {
        await client.get('/api/v1/users/me');
      } catch (e) {
        expect(e).toBeInstanceOf(ApiError);
        expect((e as ApiError).status).toBe(404);
        expect((e as ApiError).errors).toHaveLength(1);
        expect((e as ApiError).errors[0].code).toBe('NOT_FOUND');
      }
    });

    it('ApiError parses errors array from response', async () => {
      const errorBody = {
        data: null,
        errors: [
          { code: 'VALIDATION_ERROR', message: 'Campo obrigatório', retryable: false },
          { code: 'VALIDATION_ERROR', message: 'Email inválido', retryable: false },
        ],
      };
      mockFetch({ ok: false, status: 422 }, errorBody);
      try {
        await client.post('/api/v1/users', {});
      } catch (e) {
        expect((e as ApiError).errors).toHaveLength(2);
      }
    });

    it('throws ApiError on network failure with NETWORK_ERROR code', async () => {
      vi.mocked(global.fetch).mockRejectedValueOnce(new TypeError('Failed to fetch'));
      await expect(client.get('/api/v1/users/me')).rejects.toThrow(ApiError);
      try {
        await client.get('/api/v1/users/me');
      } catch (e) {
        expect((e as ApiError).status).toBe(0);
        expect((e as ApiError).errors[0].code).toBe('NETWORK_ERROR');
        expect((e as ApiError).errors[0].retryable).toBe(true);
      }
    });
  });

  describe('401 refresh and retry', () => {
    it('retries original request once after successful refresh on 401', async () => {
      let refreshCalled = false;
      client = new ApiClient(() => {
        if (!refreshCalled) return 'expired-token';
        return 'new-token';
      });

      vi.mocked(global.fetch)
        .mockResolvedValueOnce({
          ok: false,
          status: 401,
          headers: new Headers(),
          json: async () => ({
            data: null,
            errors: [{ code: 'TOKEN_EXPIRED', message: 'Token expirado', retryable: false }],
          }),
        } as Response)
        .mockResolvedValueOnce({
          ok: true,
          status: 200,
          headers: new Headers(),
          json: async () => ({
            data: { accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 3600, user: { id: '1' } },
            errors: [],
          }),
        } as Response)
        .mockResolvedValueOnce({
          ok: true,
          status: 200,
          headers: new Headers(),
          json: async () => ({ data: { id: '1', email: 'test@test.com' }, errors: [] }),
        } as Response);

      refreshCalled = true;

      const result = await client.get('/api/v1/users/me', {});
      expect(result).toEqual({ id: '1', email: 'test@test.com' });
      expect(fetch).toHaveBeenCalledTimes(3);
    });

    it('throws ApiError when refresh also fails with 401', async () => {
      vi.mocked(global.fetch)
        .mockResolvedValueOnce({
          ok: false,
          status: 401,
          headers: new Headers(),
          json: async () => ({
            data: null,
            errors: [{ code: 'TOKEN_EXPIRED', message: 'Token expirado', retryable: false }],
          }),
        } as Response)
        .mockResolvedValueOnce({
          ok: false,
          status: 401,
          headers: new Headers(),
          json: async () => ({
            data: null,
            errors: [{ code: 'TOKEN_EXPIRED', message: 'Refresh failed', retryable: false }],
          }),
        } as Response);

      await expect(client.get('/api/v1/users/me')).rejects.toThrow(ApiError);
      expect(fetch).toHaveBeenCalledTimes(2);
    });
  });

  describe('HTTP methods', () => {
    it('get sends GET', async () => {
      mockFetch({ ok: true, status: 200 }, { data: null, errors: [] });
      await client.get('/api/v1/test');
      expect(fetch).toHaveBeenCalledWith('/api/v1/test', expect.objectContaining({ method: 'GET' }));
    });

    it('post sends POST', async () => {
      mockFetch({ ok: true, status: 200 }, { data: null, errors: [] });
      await client.post('/api/v1/test', { key: 'value' });
      expect(fetch).toHaveBeenCalledWith('/api/v1/test', expect.objectContaining({ method: 'POST' }));
    });

    it('put sends PUT', async () => {
      mockFetch({ ok: true, status: 200 }, { data: null, errors: [] });
      await client.put('/api/v1/test/1', { key: 'value' });
      expect(fetch).toHaveBeenCalledWith('/api/v1/test/1', expect.objectContaining({ method: 'PUT' }));
    });

    it('patch sends PATCH', async () => {
      mockFetch({ ok: true, status: 200 }, { data: null, errors: [] });
      await client.patch('/api/v1/test/1', { key: 'value' });
      expect(fetch).toHaveBeenCalledWith('/api/v1/test/1', expect.objectContaining({ method: 'PATCH' }));
    });

    it('delete sends DELETE', async () => {
      mockFetch({ ok: true, status: 200 }, { data: null, errors: [] });
      await client.delete('/api/v1/test/1');
      expect(fetch).toHaveBeenCalledWith('/api/v1/test/1', expect.objectContaining({ method: 'DELETE' }));
    });
  });

  describe('query params', () => {
    it('appends query params to URL', async () => {
      mockFetch({ ok: true, status: 200 }, { data: [], errors: [] });
      await client.get('/api/v1/orders', { params: { page: 1, size: 10 } });
      expect(fetch).toHaveBeenCalledWith('/api/v1/orders?page=1&size=10', expect.anything());
    });

    it('skips undefined params', async () => {
      mockFetch({ ok: true, status: 200 }, { data: [], errors: [] });
      await client.get('/api/v1/orders', { params: { page: 1, size: undefined } });
      expect(fetch).toHaveBeenCalledWith('/api/v1/orders?page=1', expect.anything());
    });
  });
});
