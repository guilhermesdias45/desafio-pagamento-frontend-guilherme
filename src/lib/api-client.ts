import type { ApiResponse } from '../types/auth';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly errors: Array<{ code: string; message: string; retryable: boolean }>,
    public readonly requestId?: string,
  ) {
    super(errors[0]?.message || 'API Error');
    this.name = 'ApiError';
  }
}

interface RequestOptions extends RequestInit {
  params?: Record<string, string | number | boolean | undefined>;
  merchantId?: string;
  idempotencyKey?: string;
}

export class ApiClient {
  constructor(private getToken: () => string | null) {}

  private buildUrl(url: string, params?: Record<string, string | number | boolean | undefined>): string {
    if (!params) return url;

    const searchParams = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined) {
        searchParams.append(key, String(value));
      }
    });

    const queryString = searchParams.toString();
    return queryString ? `${url}?${queryString}` : url;
  }

  private generateIdempotencyKey(): string {
    return crypto.randomUUID();
  }

  private async request<T>(url: string, options: RequestOptions = {}): Promise<T> {
    const { params, merchantId, idempotencyKey, ...fetchOptions } = options;
    const token = this.getToken();

    const headers: Record<string, string> = {
      ...(fetchOptions.headers as Record<string, string>),
    };

    // Add Authorization header if token exists
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    // Add Content-Type for requests with body
    if (fetchOptions.body) {
      headers['Content-Type'] = 'application/json';
    }

    // Add Idempotency-Key for POST requests
    if (fetchOptions.method === 'POST' && !idempotencyKey) {
      headers['Idempotency-Key'] = this.generateIdempotencyKey();
    } else if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }

    // Add X-Merchant-Id if provided
    if (merchantId) {
      headers['X-Merchant-Id'] = merchantId;
    }

    const fullUrl = this.buildUrl(url, params);

    try {
      const response = await fetch(fullUrl, {
        ...fetchOptions,
        headers,
      });

      const json = await response.json();

      // Detecta se é formato { data: T, errors: [] } ou JSON puro
      const isWrapped = json && typeof json === 'object' && 'data' in json;

      if (!response.ok) {
        if (isWrapped) {
          const wrapped = json as ApiResponse<T>;
          // Handle 401 - try refresh and retry once
          if (response.status === 401 && url !== '/api/v1/auth/refresh') {
            try {
              await this.refreshToken();
              return this.request<T>(url, options);
            } catch {
              throw new ApiError(response.status, wrapped.errors, wrapped.meta?.requestId);
            }
          }
          throw new ApiError(response.status, wrapped.errors, wrapped.meta?.requestId);
        }

        // JSON puro (user-service style) — tenta extrair de ProblemDetail
        const errCode = json?.errorCode || 'UNKNOWN';
        const errMsg = json?.detail || json?.message || 'Erro desconhecido';
        throw new ApiError(response.status, [{ code: errCode, message: errMsg, retryable: false }]);
      }

      return isWrapped ? (json.data as T) : (json as T);
    } catch (error) {
      if (error instanceof ApiError) {
        throw error;
      }

      // Network error
      throw new ApiError(
        0,
        [
          {
            code: 'NETWORK_ERROR',
            message: 'Falha na conexão com o servidor',
            retryable: true,
          },
        ],
      );
    }
  }

  private async refreshToken(): Promise<void> {
    const response = await fetch('/api/v1/auth/refresh', {
      method: 'POST',
      credentials: 'include',
    });

    if (!response.ok) {
      throw new Error('Refresh failed');
    }
  }

  async get<T>(url: string, options?: RequestOptions): Promise<T> {
    return this.request<T>(url, { ...options, method: 'GET' });
  }

  async post<TReq, TRes>(url: string, body?: TReq, options?: RequestOptions): Promise<ApiResponse<TRes>> {
    const response = await this.request<TRes>(url, {
      ...options,
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    });

    return {
      data: response,
      meta: { timestamp: '', requestId: '' },
      errors: [],
    } as ApiResponse<TRes>;
  }

  async put<TReq, TRes>(url: string, body?: TReq, options?: RequestOptions): Promise<TRes> {
    return this.request<TRes>(url, {
      ...options,
      method: 'PUT',
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  async patch<TReq, TRes>(url: string, body?: TReq, options?: RequestOptions): Promise<ApiResponse<TRes>> {
    const response = await this.request<TRes>(url, {
      ...options,
      method: 'PATCH',
      body: body ? JSON.stringify(body) : undefined,
    });

    return {
      data: response,
      meta: { timestamp: '', requestId: '' },
      errors: [],
    } as ApiResponse<TRes>;
  }

  async delete<T>(url: string, options?: RequestOptions): Promise<T> {
    return this.request<T>(url, { ...options, method: 'DELETE' });
  }
}
