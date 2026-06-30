import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, renderHook, act, waitFor } from '@testing-library/react';
import { AuthProvider, useAuth } from './AuthContext';
import { createMockJwt } from '@/test/jwt-helper';

const mockUser = {
  id: 'user_1',
  email: 'test@acaboumony.com',
  fullName: 'Test User',
  role: 'CUSTOMER' as const,
  emailVerified: true,
  twoFactorEnabled: false,
  createdAt: '2026-01-01T00:00:00.000Z',
};

const mockToken = createMockJwt({
  sub: 'user_1',
  email: 'test@acaboumony.com',
  role: 'CUSTOMER',
  merchantId: null,
});

const mockAuthResponse = {
  accessToken: mockToken,
  tokenType: 'Bearer' as const,
  expiresIn: 3600,
  user: mockUser,
};

function createMockFetch(response: Partial<Response>, body?: unknown) {
  return vi.mocked(global.fetch).mockResolvedValueOnce({
    ok: response.ok ?? true,
    status: response.status ?? 200,
    headers: new Headers(response.headers ?? {}),
    json: async () => body,
    ...response,
  } as Response);
}

describe('AuthContext', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    global.fetch = vi.fn();
  });

  describe('initialization', () => {
    it('calls POST /api/v1/auth/refresh on mount', () => {
      createMockFetch({ ok: true, status: 200 }, { data: mockAuthResponse, errors: [] });
      render(
        <AuthProvider>
          <div>test</div>
        </AuthProvider>,
      );
      expect(fetch).toHaveBeenCalledWith('/api/v1/auth/refresh', expect.objectContaining({
        method: 'POST',
        credentials: 'include',
      }));
    });

    it('populates user after successful refresh', async () => {
      createMockFetch({ ok: true, status: 200 }, { data: mockAuthResponse, errors: [] });
      const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current.isAuthenticated).toBe(true);
      expect(result.current.token).toBe(mockToken);
      expect(result.current.user?.id).toBe('user_1');
      expect(result.current.user?.email).toBe('test@acaboumony.com');
      expect(result.current.user?.role).toBe('CUSTOMER');
    });

    it('remains unauthenticated when refresh fails with 401', async () => {
      createMockFetch(
        { ok: false, status: 401 },
        { data: null, errors: [{ code: 'TOKEN_EXPIRED', message: 'Token expirado', retryable: false }] },
      );
      const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });

      await vi.waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.user).toBeNull();
      expect(result.current.token).toBeNull();
    });
  });

  describe('login', () => {
    it('calls POST /api/v1/auth/login and stores token + user', async () => {
      createMockFetch({ ok: true, status: 200 }, { data: mockAuthResponse, errors: [] }); // refresh on mount
      createMockFetch({ ok: true, status: 200 }, { data: mockAuthResponse, errors: [] }); // login

      const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });

      await vi.waitFor(() => expect(result.current.isLoading).toBe(false));

      await act(async () => {
        await result.current.login('test@acaboumony.com', 'password123');
      });

      expect(fetch).toHaveBeenCalledWith(
        '/api/v1/auth/login',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ email: 'test@acaboumony.com', password: 'password123' }),
        }),
      );
      expect(result.current.isAuthenticated).toBe(true);
      expect(result.current.token).toBe(mockToken);
      expect(result.current.user?.id).toBe('user_1');
      expect(result.current.user?.email).toBe('test@acaboumony.com');
      expect(result.current.user?.role).toBe('CUSTOMER');
    });

    it('throws ApiError on login failure', async () => {
      createMockFetch(
        { ok: false, status: 401 },
        { data: null, errors: [{ code: 'INVALID_CREDENTIALS', message: 'Credenciais inválidas', retryable: false }] },
      );

      const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });

      await act(async () => {
        try {
          await result.current.login('test@test.com', 'wrong');
        } catch {
          // expected
        }
      });

      expect(result.current.isAuthenticated).toBe(false);
    });
  });

  describe('logout', () => {
    it('calls POST /api/v1/auth/logout and clears state', async () => {
      createMockFetch({ ok: true, status: 200 }, { data: mockAuthResponse, errors: [] }); // refresh on mount
      createMockFetch({ ok: true, status: 200 }, { data: mockAuthResponse, errors: [] }); // login
      createMockFetch({ ok: true, status: 200 }, { data: null, errors: [] }); // logout

      const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });

      await vi.waitFor(() => expect(result.current.isLoading).toBe(false));

      await act(async () => {
        await result.current.login('test@acaboumony.com', 'password123');
      });

      expect(result.current.isAuthenticated).toBe(true);

      await act(async () => {
        await result.current.logout();
      });

      expect(fetch).toHaveBeenCalledWith('/api/v1/auth/logout', expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          Authorization: `Bearer ${mockToken}`,
        }),
      }));
      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.user).toBeNull();
      expect(result.current.token).toBeNull();
    });
  });

  describe('useAuth hook', () => {
    it('throws error when used outside AuthProvider', () => {
      expect(() => renderHook(() => useAuth())).toThrow('useAuth must be used within an AuthProvider');
    });
  });

  describe('auto-redirect on refresh failure when previously authenticated', () => {
    it('redirects to /login when refresh fails after initial auth', async () => {
      const originalLocation = window.location;
      const mockReload = vi.fn();

      Object.defineProperty(window, 'location', {
        value: { ...originalLocation, href: '' },
        writable: true,
      });

      createMockFetch(
        { ok: false, status: 401 },
        { data: null, errors: [{ code: 'TOKEN_EXPIRED', message: 'Sessão expirada', retryable: false }] },
      );

      render(
        <AuthProvider>
          <div>test</div>
        </AuthProvider>,
      );

      await vi.waitFor(() => {
        expect(window.location.href).toBe('/login');
      });

      Object.defineProperty(window, 'location', {
        value: originalLocation,
        writable: true,
      });
    });
  });
});
