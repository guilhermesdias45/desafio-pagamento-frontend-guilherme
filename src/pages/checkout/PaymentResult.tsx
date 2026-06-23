import type { PaymentResultData } from '@/types/checkout';
import { Button } from '@/components/ui/Button';

export interface PaymentResultProps {
  result: PaymentResultData;
  onRetry: () => void;
  onViewOrder: (orderId: string) => void;
}

function formatCurrency(cents: number): string {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(cents / 100);
}

function formatProcessingTime(ms: number): string {
  return `Processado em ${ms.toLocaleString('pt-BR')} ms`;
}

type ErrorConfig = {
  title: string;
  message: string;
  iconClass: string;
  showRetry: boolean;
};

function getErrorConfig(errorCode?: string, retryable?: boolean): ErrorConfig | null {
  if (!errorCode) return null;

  switch (errorCode) {
    case 'CARD_DECLINED':
      return {
        title: 'Cartão recusado',
        message: 'Seu cartão foi recusado. Verifique os dados ou tente outro cartão.',
        iconClass: 'text-red-500',
        showRetry: true,
      };
    case 'SUSPECTED_FRAUD':
      return {
        title: 'Transação suspeita',
        message: 'Transação suspeita. Entre em contato com o suporte.',
        iconClass: 'text-red-500',
        showRetry: false,
      };
    case 'MP_GATEWAY_TIMEOUT':
      return {
        title: 'Tempo limite excedido',
        message: 'O gateway de pagamento não respondeu a tempo. Tente novamente.',
        iconClass: 'text-orange-500',
        showRetry: true,
      };
    case 'INVALID_CARD_TOKEN':
      return {
        title: 'Erro no processamento',
        message: 'Ocorreu um erro ao processar seu cartão. Tente novamente.',
        iconClass: 'text-red-500',
        showRetry: true,
      };
    default:
      return {
        title: 'Pagamento não aprovado',
        message: '',
        iconClass: 'text-red-500',
        showRetry: retryable === true,
      };
  }
}

function CheckIcon({ className }: { className: string }) {
  return (
    <svg className={`h-16 w-16 ${className}`} xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}

function XIcon({ className }: { className: string }) {
  return (
    <svg className={`h-16 w-16 ${className}`} xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}

function WarningIcon({ className }: { className: string }) {
  return (
    <svg className={`h-16 w-16 ${className}`} xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
    </svg>
  );
}

export function PaymentResult({ result, onRetry, onViewOrder }: PaymentResultProps) {
  const { status, amountInCents, transactionId, orderId, processingTimeMs, errorCode, errorMessage, retryable } = result;

  if (status === 'APPROVED') {
    return (
      <div className="max-w-lg mx-auto text-center">
        <div className="bg-white rounded-lg shadow-md p-8 space-y-4">
          <CheckIcon className="text-green-500 mx-auto" />
          <h1 className="text-2xl font-bold text-green-600">Pagamento aprovado!</h1>
          <p className="text-3xl font-bold text-gray-800">{formatCurrency(amountInCents)}</p>
          <p className="text-sm text-gray-500">ID da transação: {transactionId}</p>
          <p className="text-sm text-gray-500">{formatProcessingTime(processingTimeMs)}</p>
          <div className="pt-4">
            <Button onClick={() => onViewOrder(orderId)}>Ver pedido</Button>
          </div>
        </div>
      </div>
    );
  }

  const errorConfig = getErrorConfig(errorCode, retryable);
  if (!errorConfig) return null;

  const IconComponent = errorConfig.iconClass === 'text-orange-500' ? WarningIcon : XIcon;
  const displayMessage = errorConfig.message || errorMessage || '';

  return (
    <div className="max-w-lg mx-auto text-center">
      <div className="bg-white rounded-lg shadow-md p-8 space-y-4">
        <IconComponent className={`${errorConfig.iconClass} mx-auto`} />
        <h1 className="text-2xl font-bold" style={{ color: errorConfig.iconClass.includes('orange') ? '#f97316' : '#dc2626' }}>
          {errorConfig.title}
        </h1>
        {displayMessage && (
          <p className="text-gray-600">{displayMessage}</p>
        )}
        <p className="text-sm text-gray-500">{formatProcessingTime(processingTimeMs)}</p>
        {errorConfig.showRetry && (
          <div className="pt-4">
            <Button onClick={onRetry}>
              {errorCode === 'CARD_DECLINED'
                ? 'Tentar novamente com outro cartão'
                : 'Tentar novamente'}
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
