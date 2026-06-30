import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AuthProvider } from '../contexts/AuthContext';
import { ProtectedRoute } from './ProtectedRoute';
import { MemoryRouter } from 'react-router-dom';
import { createMockJwt } from '@/test/jwt-helper';

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
};

function createMockFetch(body: unknown) {
  return vi.mocked(global.fetch).mockResolvedValueOnce({
    ok: true,
    status: 200,
    headers: new Headers(),
    json: async () => body,
  } as Response);
}

function renderProtected(initialRoute = '/orders') {
  return render(
    <MemoryRouter initialEntries={[initialRoute]}>
      <AuthProvider>
        <ProtectedRoute>
          <div>Protected Content</div>
        </ProtectedRoute>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('ProtectedRoute', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    global.fetch = vi.fn();
  });

  it('renders children when authenticated', async () => {
    createMockFetch({ data: mockAuthResponse, errors: [] });
    renderProtected();
    const content = await screen.findByText('Protected Content');
    expect(content).toBeInTheDocument();
  });

  it('renders spinner while loading', () => {
    createMockFetch({ data: mockAuthResponse, errors: [] });
    renderProtected();
    const svg = document.querySelector('svg.animate-spin');
    expect(svg).toBeInTheDocument();
  });
});
