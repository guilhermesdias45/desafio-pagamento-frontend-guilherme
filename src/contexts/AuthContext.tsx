import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { decodeJwt } from '@/lib/jwt';

interface User {
  id: string;
  email: string;
  fullName: string;
  role: 'CUSTOMER' | 'MERCHANT_OWNER';
  merchantId?: string;
  emailVerified: boolean;
  twoFactorEnabled: boolean;
  createdAt: string;
}

interface AuthContextValue {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string, totpCode?: string) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  setSession: (accessToken: string, userData: User) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

interface AuthProviderProps {
  children: ReactNode;
}

const PUBLIC_ROUTES = ['/login', '/register', '/confirm-email', '/2fa/verify', '/2fa/setup'];

export function AuthProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const isAuthenticated = !!user && !!token;

  useEffect(() => {
    refresh();
  }, []);

  async function refresh() {
    try {
      const response = await fetch('/api/v1/auth/refresh', {
        method: 'POST',
        credentials: 'include',
      });

      if (!response.ok) {
        setIsLoading(false);
        if (!PUBLIC_ROUTES.includes(window.location.pathname)) {
          window.location.href = '/login';
        }
        return;
      }

      const json = await response.json();
      const accessToken = json.accessToken ?? json.data?.accessToken;

      if (!accessToken) {
        setIsLoading(false);
        if (!PUBLIC_ROUTES.includes(window.location.pathname)) {
          window.location.href = '/login';
        }
        return;
      }

      setToken(accessToken);

      const claims = decodeJwt(accessToken);
      if (claims) {
        setUser({
          id: claims.sub,
          email: claims.email,
          fullName: claims.email,
          role: claims.role === 'STAFF' ? 'CUSTOMER' : claims.role,
          merchantId: claims.merchantId ?? undefined,
          emailVerified: true,
          twoFactorEnabled: false,
          createdAt: new Date().toISOString(),
        });
      }

      setIsLoading(false);
    } catch {
      setIsLoading(false);
      if (!PUBLIC_ROUTES.includes(window.location.pathname)) {
        window.location.href = '/login';
      }
    }
  }

  async function login(email: string, password: string, totpCode?: string) {
    const response = await fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, password, totpCode }),
    });

    if (!response.ok) {
      const errorData = await response.json();
      throw new Error(errorData.errors?.[0]?.message || 'Login failed');
    }

    const json = await response.json();
    const accessToken = json.accessToken ?? json.data?.accessToken;

    if (accessToken) {
      setToken(accessToken);
      const claims = decodeJwt(accessToken);
      if (claims) {
        setUser({
          id: claims.sub,
          email: claims.email,
          fullName: claims.email,
          role: claims.role === 'STAFF' ? 'CUSTOMER' : claims.role,
          merchantId: claims.merchantId ?? undefined,
          emailVerified: true,
          twoFactorEnabled: false,
          createdAt: new Date().toISOString(),
        });
      }
    }
  }

  async function setSession(accessToken: string, userData: User) {
    setToken(accessToken);
    setUser(userData);
  }

  async function logout() {
    try {
      await fetch('/api/v1/auth/logout', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });
    } finally {
      setToken(null);
      setUser(null);
    }
  }

  const value: AuthContextValue = {
    user,
    token,
    isAuthenticated,
    isLoading,
    login,
    logout,
    refresh,
    setSession,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
