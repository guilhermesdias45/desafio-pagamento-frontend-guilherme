import { ApiClient } from '@/lib/api-client';
import type {
  TransactionSummary,
  TransactionDetail,
  RefundRequest,
  RefundResponse,
  PaginatedResponse,
  TransactionFilters,
} from '@/types/merchant';
import type { ApiResponse } from '@/types/auth';

export class MerchantApiService {
  constructor(
    private apiClient: ApiClient,
    private merchantId: string,
    private userId: string,
  ) {}

  async listTransactions(filters?: Partial<TransactionFilters>): Promise<PaginatedResponse<TransactionSummary>> {
    return this.apiClient.get<PaginatedResponse<TransactionSummary>>('/api/v1/transactions', {
      params: {
        page: filters?.page ?? 0,
        size: filters?.size ?? 20,
        sort: filters?.sort ?? 'createdAt,desc',
        ...(filters?.status ? { status: filters.status } : {}),
      },
      merchantId: this.merchantId,
    });
  }

  async getTransactionDetail(transactionId: string): Promise<TransactionDetail> {
    return this.apiClient.get<TransactionDetail>(`/api/v1/transactions/${transactionId}`, {
      merchantId: this.merchantId,
    });
  }

  async submitRefund(
    transactionId: string,
    request: Omit<RefundRequest, 'idempotencyKey'> & { idempotencyKey: string },
  ): Promise<ApiResponse<RefundResponse>> {
    return this.apiClient.post<Omit<RefundRequest, 'idempotencyKey'>, RefundResponse>(
      `/api/v1/transactions/${transactionId}/refund`,
      {
        amountInCents: request.amountInCents,
        reason: request.reason,
        requestedBy: request.requestedBy,
      },
      {
        merchantId: this.merchantId,
        idempotencyKey: request.idempotencyKey,
      },
    );
  }
}
