import type { AuthUser } from '@/types/auth';

export interface MockAuthContext {
  user: AuthUser | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isMerchant: boolean;
  login: (accessToken: string, user: AuthUser) => void;
  logout: () => Promise<void>;
  refreshToken: () => Promise<string | null>;
  loading: boolean;
}

export function createMockAuthContext(overrides?: Partial<MockAuthContext>): MockAuthContext {
  return {
    user: null,
    accessToken: null,
    isAuthenticated: false,
    isMerchant: false,
    login: () => {},
    logout: () => Promise.resolve(),
    refreshToken: () => Promise.resolve(null),
    loading: false,
    ...overrides,
  };
}
