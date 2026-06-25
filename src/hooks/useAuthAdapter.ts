import React, { useMemo, Children, isValidElement, type ReactElement } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import { ApiClient } from '@/lib/api-client';
import type { IApiClient, IAuthContext, AuthUser } from '@/types/auth';

export function useAuthAdapter(): { apiClient: IApiClient; authContext: IAuthContext } {
  const auth = useAuth();

  const apiClient: IApiClient = useMemo(() => {
    const client = new ApiClient(() => auth.token);
    return {
      post: (url, body, options) => client.post(url, body, options),
      get: (url, options) => client.get(url, options) as never,
      patch: (url, body) => client.patch(url, body),
    };
  }, [auth.token]);

  const authContext: IAuthContext = useMemo(() => ({
    user: auth.user
      ? {
          userId: auth.user.id,
          email: auth.user.email,
          role: auth.user.role,
          merchantId: auth.user.merchantId ?? null,
        }
      : null,
    accessToken: auth.token,
    isAuthenticated: auth.isAuthenticated,
    isMerchant: auth.user?.role === 'MERCHANT_OWNER',
    login: (accessToken: string, authUser: AuthUser) => {
      auth.setSession(accessToken, {
        id: authUser.userId,
        email: authUser.email,
        fullName: auth.user?.fullName || authUser.email,
        role: authUser.role,
        merchantId: authUser.merchantId ?? undefined,
        emailVerified: false,
        twoFactorEnabled: false,
        createdAt: new Date().toISOString(),
      });
    },
    logout: auth.logout,
    refreshToken: async () => auth.token,
    loading: auth.isLoading,
  }), [auth]);

  return { apiClient, authContext };
}

export function AuthDepsWrapper({ children }: { children: React.ReactNode }) {
  const { apiClient, authContext } = useAuthAdapter();
  const child = Children.only(children);
  if (isValidElement(child)) {
    return React.cloneElement(child as ReactElement<Record<string, unknown>>, { apiClient, authContext });
  }
  return React.createElement(React.Fragment, null, child);
}
