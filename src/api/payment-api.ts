import type {
  TransactionRequest,
  TransactionResponse,
} from '@/types/checkout';

export interface PaymentApi {
  createTransaction(
    request: TransactionRequest,
    options?: { merchantId?: string; idempotencyKey?: string },
  ): Promise<TransactionResponse>;
}

export function createPaymentApi(
  post: <TReq, TRes>(url: string, body?: TReq) => Promise<{ data: TRes | null; errors: Array<{ code: string; message: string; retryable: boolean }> }>,
): PaymentApi {
  return {
    async createTransaction(request, options) {
      const idempotencyKey = options?.idempotencyKey || crypto.randomUUID();
      const response = await post<TransactionRequest, TransactionResponse>(
        '/api/v1/transactions',
        request,
      );

      if (response.errors && response.errors.length > 0) {
        const error = response.errors[0];
        return {
          status: 'FAILURE',
          errorCode: error.code,
          message: error.message,
          retryable: error.retryable,
          processingTimeMs: 0,
        } as TransactionResponse;
      }

      return response.data as TransactionResponse;
    },
  };
}
