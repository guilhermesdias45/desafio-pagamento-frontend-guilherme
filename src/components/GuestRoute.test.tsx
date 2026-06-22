import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AuthProvider } from '../contexts/AuthContext';
import { GuestRoute } from './GuestRoute';
import { MemoryRouter } from 'react-router-dom';

const mockUser = {
  id: 'user_1',
  email: 'test@acaboumony.com',
  fullName: 'Test User',
  role: 'CUSTOMER' as const,
  emailVerified: true,
  twoFactorEnabled: false,
  createdAt: '2026-01-01T00:00:00.000Z',
};

const mockAuthResponse = {
  accessToken: 'test-jwt',
  tokenType: 'Bearer' as const,
  expiresIn: 3600,
  user: mockUser,
};

function createMockFetch(body: unknown) {
  return vi.mocked(global.fetch).mockResolvedValueOnce({
    ok: true,
    status: 200,
    headers: new Headers(),
    json: async () => body,
  } as Response);
}

function renderGuest(initialRoute = '/login') {
  return render(
    <MemoryRouter initialEntries={[initialRoute]}>
      <AuthProvider>
        <GuestRoute>
          <div>Login Form</div>
        </GuestRoute>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('GuestRoute', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    global.fetch = vi.fn();
  });

  it('renders children when not authenticated', async () => {
    createMockFetch(
      { ok: false, status: 401 },
      { data: null, errors: [{ code: 'TOKEN_EXPIRED', message: 'Token expirado', retryable: false }] } as any,
    );
    renderGuest();
    const content = await screen.findByText('Login Form');
    expect(content).toBeInTheDocument();
  });

  it('renders spinner while loading', () => {
    createMockFetch({ data: mockAuthResponse, errors: [] });
    renderGuest();
    const svg = document.querySelector('svg.animate-spin');
    expect(svg).toBeInTheDocument();
  });
});
