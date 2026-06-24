import { useState, type FormEvent, type ChangeEvent } from 'react';
import type {
  CardBrand,
  CardFormData,
  CardFormErrors,
  PaymentResultData,
  MercadoPagoCardTokenRequest,
  MercadoPagoCardTokenResponse,
  MercadoPagoInstance,
} from '@/types/checkout';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

export interface CardFormProps {
  orderId: string;
  amountInCents: number;
  customerId: string;
  merchantId?: string;
  authToken?: string;
  mercadoPagoInstance?: MercadoPagoInstance;
  onPaymentComplete: (result: PaymentResultData) => void;
  onError: (error: string) => void;
  postTransaction?: (url: string, body: unknown) => Promise<{
    data: unknown | null;
    errors?: Array<{ code: string; message: string; retryable: boolean }>;
  }>;
}

function detectBrand(cardNumber: string): CardBrand {
  const digits = cardNumber.replace(/\D/g, '');
  if (/^(4011|4312|4389)/.test(digits)) return 'elo';
  if (/^4/.test(digits)) return 'visa';
  if (/^5[1-5]/.test(digits)) return 'mastercard';
  if (/^3[47]/.test(digits)) return 'amex';
  if (/^6062/.test(digits)) return 'hipercard';
  return 'unknown';
}

const brandLabels: Record<CardBrand, string> = {
  visa: 'Visa',
  mastercard: 'Mastercard',
  elo: 'Elo',
  amex: 'Amex',
  hipercard: 'Hipercard',
  unknown: 'Desconhecida',
};

function luhnCheck(digits: string): boolean {
  let sum = 0;
  let alternate = false;
  for (let i = digits.length - 1; i >= 0; i--) {
    let n = parseInt(digits[i], 10);
    if (alternate) {
      n *= 2;
      if (n > 9) n -= 9;
    }
    sum += n;
    alternate = !alternate;
  }
  return sum % 10 === 0;
}

function formatCurrency(cents: number): string {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(cents / 100);
}

function maskCardNumber(value: string, brand: CardBrand): string {
  const digits = value.replace(/\D/g, '').slice(0, 16);
  if (brand === 'amex') {
    const groups: string[] = [];
    if (digits.length > 0) groups.push(digits.slice(0, 4));
    if (digits.length > 4) groups.push(digits.slice(4, 10));
    if (digits.length > 10) groups.push(digits.slice(10, 15));
    return groups.join(' ');
  }
  const groups: string[] = [];
  for (let i = 0; i < digits.length; i += 4) {
    groups.push(digits.slice(i, i + 4));
  }
  return groups.join(' ');
}

function maskExpiry(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 4);
  if (digits.length <= 2) return digits;
  return digits.slice(0, 2) + '/' + digits.slice(2);
}

function validateForm(data: CardFormData): CardFormErrors {
  const errors: CardFormErrors = {};

  const digits = data.cardNumber.replace(/\D/g, '');
  if (digits.length < 13 || !luhnCheck(digits)) {
    errors.cardNumber = 'Número de cartão inválido';
  }

  if (!data.expiryMonth && !data.expiryYear) {
    errors.expiry = 'Data de validade obrigatória';
  } else {
    const month = parseInt(data.expiryMonth, 10);
    const year = parseInt(data.expiryYear, 10);
    if (month < 1 || month > 12) {
      errors.expiry = 'Mês inválido';
    } else {
      const now = new Date();
      const currentYear = now.getFullYear() % 100;
      const currentMonth = now.getMonth() + 1;
      if (year < currentYear || (year === currentYear && month < currentMonth)) {
        errors.expiry = 'Cartão vencido';
      }
    }
  }

  if (!data.cvv) {
    errors.cvv = 'CVV obrigatório';
  }

  if (data.cardholderName.length < 3) {
    errors.cardholderName = 'Nome do titular deve ter ao menos 3 caracteres';
  }

  return errors;
}

export function CardForm({
  orderId,
  amountInCents,
  customerId,
  merchantId,
  authToken,
  mercadoPagoInstance,
  onPaymentComplete,
  onError,
  postTransaction,
}: CardFormProps) {
  const [cardNumber, setCardNumber] = useState('');
  const [cardNumberMasked, setCardNumberMasked] = useState('');
  const [expiry, setExpiry] = useState('');
  const [cvv, setCvv] = useState('');
  const [cardholderName, setCardholderName] = useState('');
  const [installments, setInstallments] = useState(1);
  const [errors, setErrors] = useState<CardFormErrors>({});
  const [submitting, setSubmitting] = useState(false);

  const brand = detectBrand(cardNumber);
  const cvvMaxLength = brand === 'amex' ? 4 : 3;

  const handleCardNumberChange = (e: ChangeEvent<HTMLInputElement>) => {
    const digits = e.target.value.replace(/\D/g, '').slice(0, 16);
    setCardNumber(digits);
    setCardNumberMasked(maskCardNumber(digits, detectBrand(digits)));
  };

  const handleExpiryChange = (e: ChangeEvent<HTMLInputElement>) => {
    setExpiry(maskExpiry(e.target.value));
  };

  const handleCvvChange = (e: ChangeEvent<HTMLInputElement>) => {
    const digits = e.target.value.replace(/\D/g, '').slice(0, cvvMaxLength);
    setCvv(digits);
  };

  const handleExpiryBlur = () => {
    const parts = expiry.split('/');
    if (parts.length === 2) {
      const m = parts[0];
      const y = parts[1];
      if (m && y) {
        const month = parseInt(m, 10);
        const year = parseInt(y, 10);
        if (month < 1 || month > 12) {
          setErrors((prev) => ({ ...prev, expiry: 'Mês inválido' }));
        } else {
          const now = new Date();
          const currentYear = now.getFullYear() % 100;
          const currentMonth = now.getMonth() + 1;
          if (year < currentYear || (year === currentYear && month < currentMonth)) {
            setErrors((prev) => ({ ...prev, expiry: 'Cartão vencido' }));
          } else {
            setErrors((prev) => {
              const next = { ...prev };
              delete next.expiry;
              return next;
            });
          }
        }
      }
    }
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setErrors({});

    const expiryParts = expiry.split('/');
    const formData: CardFormData = {
      cardNumber: cardNumber,
      cardNumberMasked: cardNumberMasked,
      expiryMonth: expiryParts[0] || '',
      expiryYear: expiryParts[1] || '',
      cvv,
      cardholderName,
      installments,
    };

    const validationErrors = validateForm(formData);
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }

    setSubmitting(true);

    try {
      let tokenId: string;

      if (mercadoPagoInstance) {
        const mpData: MercadoPagoCardTokenRequest = {
          cardNumber: cardNumber,
          expirationMonth: formData.expiryMonth,
          expirationYear: formData.expiryYear,
          securityCode: cvv,
          cardholderName: cardholderName,
        };
        const tokenResponse: MercadoPagoCardTokenResponse = await mercadoPagoInstance.cardToken(mpData);
        tokenId = tokenResponse.id;
      } else {
        const MP = (window as any).MercadoPago;
        if (typeof MP === 'function') {
          const mpInstance = new MP('TEST-123', { locale: 'pt-BR' });
          const mpData: MercadoPagoCardTokenRequest = {
            cardNumber: cardNumber,
            expirationMonth: formData.expiryMonth,
            expirationYear: formData.expiryYear,
            securityCode: cvv,
            cardholderName: cardholderName,
          };
          const tokenResponse: MercadoPagoCardTokenResponse = await mpInstance.cardToken(mpData);
          tokenId = tokenResponse.id;
        } else {
          throw new Error('MP_SDK_ERROR');
        }
      }

      const postFn = postTransaction || ((url: string, body: unknown) => {
        const headers: Record<string, string> = {
          'Content-Type': 'application/json',
          'Authorization': authToken ? `Bearer ${authToken}` : '',
          'Idempotency-Key': crypto.randomUUID(),
          'X-Merchant-Id': merchantId || '',
          'X-Forwarded-For': '',
        };
        return fetch(url, {
          method: 'POST',
          headers,
          body: JSON.stringify(body),
        }).then((res) => res.json());
      });

      const response = await postFn('/api/v1/transactions', {
        amountInCents,
        currency: 'BRL',
        customerId,
        orderId,
        cardToken: tokenId,
        paymentMethodId: 'credit',
        installments,
      });

      if (response.errors && response.errors.length > 0) {
        const error = response.errors[0];
        onPaymentComplete({
          transactionId: '',
          orderId,
          status: 'FAILURE',
          amountInCents,
          processingTimeMs: 0,
          errorCode: error.code,
          errorMessage: error.message,
          retryable: error.retryable,
        });
      } else if (response.data) {
        const data = response.data as any;
        onPaymentComplete({
          transactionId: data.transactionId,
          orderId,
          status: data.status === 'APPROVED' ? 'APPROVED' : 'FAILURE',
          amountInCents,
          processingTimeMs: data.processingTimeMs || 0,
          errorCode: data.errorCode,
          errorMessage: data.message,
          retryable: data.retryable,
        });
      }
    } catch (err: any) {
      if (err.message === 'MP_SDK_ERROR') {
        onError('Não foi possível processar seu cartão. Tente novamente.');
      } else {
        onError('Erro de conexão. Tente novamente.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="max-w-lg mx-auto">
      <div className="bg-white rounded-lg shadow-md p-6 mb-6">
        <h2 className="text-lg font-semibold text-gray-800 mb-3">Total do pedido</h2>
        <p className="text-3xl font-bold text-primary">{formatCurrency(amountInCents)}</p>
      </div>

      <form onSubmit={handleSubmit} className="bg-white rounded-lg shadow-md p-6 space-y-4">
        <div>
          <Input
            label="Número do cartão"
            value={cardNumberMasked}
            onChange={handleCardNumberChange}
            placeholder="0000 0000 0000 0000"
            maxLength={19}
            error={errors.cardNumber}
          />
          {cardNumber && (
            <span className="text-xs text-gray-500 mt-1 inline-block">
              {brandLabels[brand]}
            </span>
          )}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <Input
            label="Validade"
            value={expiry}
            onChange={handleExpiryChange}
            onBlur={handleExpiryBlur}
            placeholder="MM/AA"
            maxLength={5}
            error={errors.expiry}
          />
          <Input
            label="CVV"
            type="password"
            value={cvv}
            onChange={handleCvvChange}
            placeholder="***"
            maxLength={cvvMaxLength}
            error={errors.cvv}
          />
        </div>

        <Input
          label="Nome do titular"
          value={cardholderName}
          onChange={(e) => setCardholderName(e.target.value)}
          placeholder="Nome completo como no cartão"
          error={errors.cardholderName}
        />

        <div className="flex flex-col gap-1">
          <label htmlFor="installments" className="text-sm font-medium text-gray-700">
            Parcelas
          </label>
          <select
            id="installments"
            value={installments}
            onChange={(e) => setInstallments(Number(e.target.value))}
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
          >
            {Array.from({ length: 12 }, (_, i) => i + 1).map((num) => (
              <option key={num} value={num}>
                {num}x de {formatCurrency(Math.round(amountInCents / num))}
              </option>
            ))}
          </select>
        </div>

        <Button
          type="submit"
          fullWidth
          disabled={submitting}
          loading={submitting}
          data-testid={submitting ? 'loading-spinner' : undefined}
        >
          Pagar
        </Button>
      </form>
    </div>
  );
}
