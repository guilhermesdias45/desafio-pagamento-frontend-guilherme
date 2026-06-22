import type { ApiResponse } from '@/types/auth';

export interface MockApiClient {
  post: <TReq, TRes>(url: string, body?: TReq, options?: RequestInit) => Promise<ApiResponse<TRes>>;
  get: <TRes>(url: string, options?: RequestInit) => Promise<ApiResponse<TRes>>;
  patch: <TReq, TRes>(url: string, body?: TReq) => Promise<ApiResponse<TRes>>;
}

export function createMockApiClient(): MockApiClient {
  return {
    post: (() => Promise.resolve({ data: null, meta: { timestamp: '', requestId: '' }, errors: [] })) as MockApiClient['post'],
    get: (() => Promise.resolve({ data: null, meta: { timestamp: '', requestId: '' }, errors: [] })) as MockApiClient['get'],
    patch: (() => Promise.resolve({ data: null, meta: { timestamp: '', requestId: '' }, errors: [] })) as MockApiClient['patch'],
  };
}
