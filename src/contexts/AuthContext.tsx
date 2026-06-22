import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';

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

interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: User;
}

interface AuthContextValue {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string, totpCode?: string) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
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

export function AuthProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const isAuthenticated = !!user && !!token;

  useEffect(() => {
    // Try to restore session on mount
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
        return;
      }

      const data: { data: AuthResponse; errors: [] } = await response.json();
      setToken(data.data.accessToken);
      setUser(data.data.user);
      setIsLoading(false);
    } catch (error) {
      setIsLoading(false);
      // If refresh fails and we were authenticated, redirect to login
      if (user) {
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
      throw new Error(errorData.errors[0]?.message || 'Login failed');
    }

    const data: { data: AuthResponse; errors: [] } = await response.json();
    setToken(data.data.accessToken);
    setUser(data.data.user);
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
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
