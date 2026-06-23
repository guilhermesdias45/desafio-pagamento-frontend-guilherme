export type CardBrand = 'visa' | 'mastercard' | 'elo' | 'amex' | 'hipercard' | 'unknown';

export const CARD_BRAND_PATTERNS: Record<CardBrand, RegExp> = {
  visa: /^4/,
  mastercard: /^5[1-5]/,
  elo: /^(4011|4312|4389)/,
  amex: /^3[47]/,
  hipercard: /^6062/,
  unknown: /.*/,
};

export interface CardFormData {
  cardNumber: string;
  cardNumberMasked: string;
  expiryMonth: string;
  expiryYear: string;
  cvv: string;
  cardholderName: string;
  installments: number;
}

export type CardFormField = keyof CardFormData;

export interface CardFormErrors {
  cardNumber?: string;
  expiry?: string;
  cvv?: string;
  cardholderName?: string;
  installments?: string;
}

export interface MercadoPagoCardTokenRequest {
  cardNumber: string;
  expirationMonth: string;
  expirationYear: string;
  securityCode: string;
  cardholderName: string;
}

export interface MercadoPagoCardTokenResponse {
  id: string;
  publicKey: string;
  status: 'active' | 'used';
  cardholder: {
    name: string;
  };
}

export interface MercadoPagoInstance {
  cardToken(data: MercadoPagoCardTokenRequest): Promise<MercadoPagoCardTokenResponse>;
}

export interface TransactionRequest {
  amountInCents: number;
  currency: 'BRL';
  customerId: string;
  orderId: string;
  cardToken: string;
  paymentMethodId: 'credit';
  installments?: number;
}

export interface TransactionSuccess {
  transactionId: string;
  mpPaymentId: number;
  orderId: string;
  status: 'APPROVED';
  processingTimeMs: number;
}

export interface TransactionFailure {
  status: 'FAILURE';
  errorCode: string;
  message: string;
  retryable: boolean;
  processingTimeMs: number;
}

export type TransactionResponse = TransactionSuccess | TransactionFailure;

export type PaymentResultStatus = 'APPROVED' | 'FAILURE';

export interface PaymentResultData {
  transactionId: string;
  orderId: string;
  status: PaymentResultStatus;
  amountInCents: number;
  processingTimeMs: number;
  errorCode?: string;
  errorMessage?: string;
  retryable?: boolean;
}

export interface InstallmentOption {
  value: number;
  label: string;
  total: number;
  installmentAmount: number;
}

export interface CardFormProps {
  orderId: string;
  amountInCents: number;
  customerId: string;
  onPaymentComplete: (result: PaymentResultData) => void;
  onError: (error: string) => void;
}

export interface PaymentResultProps {
  result: PaymentResultData;
  onRetry: () => void;
  onViewOrder: (orderId: string) => void;
}
