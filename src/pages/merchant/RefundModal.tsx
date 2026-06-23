import { useState, useMemo } from 'react';
import { ApiClient } from '@/lib/api-client';
import { Button } from '@/components/ui/Button';
import type { TransactionDetail, RefundFormData, RefundFormErrors, RefundType, RefundReason } from '@/types/merchant';
import { REFUND_REASON_LABELS } from '@/types/merchant';

export interface RefundModalProps {
  transaction: TransactionDetail;
  isOpen: boolean;
  onClose: () => void;
  onRefundSuccess: () => void;
  onRefundError?: (code: string) => void;
  apiClient?: ApiClient;
  userId?: string;
}

const formatBRL = (cents: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);

export function RefundModal({
  transaction,
  isOpen,
  onClose,
  onRefundSuccess,
  onRefundError,
  apiClient: externalClient,
  userId: externalUserId,
}: RefundModalProps) {
  const apiClient = useMemo(() => externalClient ?? new ApiClient(() => null), [externalClient]);

  const refundableAmount = transaction.amountInCents - transaction.refunds.reduce((sum, r) => sum + r.amountInCents, 0);

  const [step, setStep] = useState<'form' | 'confirm'>('form');
  const [formData, setFormData] = useState<RefundFormData>({
    refundType: 'TOTAL',
    amountInCents: null,
    reason: null,
  });
  const [formErrors, setFormErrors] = useState<RefundFormErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const resetForm = () => {
    setStep('form');
    setFormData({ refundType: 'TOTAL', amountInCents: null, reason: null });
    setFormErrors({});
    setSubmitting(false);
    setServerError(null);
  };

  const handleClose = () => {
    resetForm();
    onClose();
  };

  if (!isOpen) return null;

  const validate = (): boolean => {
    const errors: RefundFormErrors = {};

    if (!formData.reason) {
      errors.reason = 'Selecione um motivo para o estorno';
    }

    if (formData.refundType === 'PARTIAL') {
      if (formData.amountInCents === null || formData.amountInCents === 0) {
        errors.amountInCents = 'Informe o valor do estorno';
      } else if (formData.amountInCents < 1) {
        errors.amountInCents = 'Valor mínimo é R$ 0,01';
      } else if (formData.amountInCents > refundableAmount) {
        errors.amountInCents = `Valor máximo é ${formatBRL(refundableAmount)}`;
      }
    }

    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleContinue = () => {
    if (validate()) {
      setStep('confirm');
    }
  };

  const handleBackToForm = () => {
    setStep('form');
  };

  const handleSubmit = async () => {
    if (!formData.reason) return;

    setSubmitting(true);
    setServerError(null);

    try {
      const body: Record<string, unknown> = {
        reason: formData.reason,
        requestedBy: externalUserId || '',
      };
      if (formData.refundType === 'PARTIAL' && formData.amountInCents) {
        body.amountInCents = formData.amountInCents;
      }

      const idempotencyKey = crypto.randomUUID();

      await (apiClient as unknown as {
        post: (url: string, body?: unknown, options?: Record<string, unknown>) => Promise<unknown>;
      }).post(
        `/api/v1/transactions/${transaction.transactionId}/refund`,
        body,
        { idempotencyKey },
      );

      resetForm();
      onRefundSuccess();
    } catch (err: unknown) {
      const apiErr = err as { status?: number; errors?: Array<{ code: string; message: string }> };
      const errorCode = apiErr?.errors?.[0]?.code;

      if (errorCode === 'AMOUNT_EXCEEDS_ORIGINAL') {
        setServerError('Valor do estorno excede o valor disponível para estorno');
        setStep('form');
      } else if (errorCode === 'MP_GATEWAY_ERROR') {
        setServerError('Erro no gateway de pagamento. Tente novamente.');
      } else if (errorCode === 'ALREADY_FULLY_REFUNDED' || errorCode === 'REFUND_WINDOW_EXPIRED') {
        resetForm();
        onClose();
        if (onRefundError) onRefundError(errorCode);
      } else {
        setServerError(apiErr?.errors?.[0]?.message || 'Erro ao processar estorno');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleRetry = () => {
    setServerError(null);
  };

  const refundTypeOptions: { value: RefundType; label: string }[] = [
    { value: 'TOTAL', label: 'Estorno total' },
    { value: 'PARTIAL', label: 'Estorno parcial' },
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50" onClick={handleClose}>
      <div
        className="bg-white rounded-lg shadow-xl w-full max-w-lg mx-4"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-6 py-4 border-b border-gray-200">
          <h2 className="text-lg font-semibold">
            Estornar transação {transaction.transactionId}
          </h2>
        </div>

        <div className="px-6 py-4">
          {step === 'form' && (
            <>
              <div className="mb-4">
                <p className="text-sm text-gray-500 mb-1">Valor disponível para estorno</p>
                <p className="text-xl font-bold">{formatBRL(refundableAmount)}</p>
              </div>

              <div className="mb-4">
                <p className="text-sm font-medium text-gray-700 mb-2">Tipo de estorno</p>
                {refundTypeOptions.map((opt) => (
                  <label key={opt.value} className="flex items-center gap-2 mb-2 cursor-pointer">
                    <input
                      type="radio"
                      name="refundType"
                      value={opt.value}
                      checked={formData.refundType === opt.value}
                      onChange={() => setFormData({ ...formData, refundType: opt.value, amountInCents: opt.value === 'TOTAL' ? null : formData.amountInCents })}
                      className="accent-primary"
                    />
                    <span className="text-sm">{opt.label}</span>
                  </label>
                ))}
              </div>

              {formData.refundType === 'PARTIAL' && (
                <div className="mb-4">
                  <label htmlFor="refund-amount" className="block text-sm font-medium text-gray-700 mb-1">
                    Valor do estorno (R$)
                  </label>
                  <input
                    id="refund-amount"
                    type="number"
                    step="0.01"
                    min="0.01"
                    max={refundableAmount / 100}
                    value={formData.amountInCents !== null ? (formData.amountInCents / 100).toFixed(2) : ''}
                    onChange={(e) => {
                      const val = e.target.value;
                      const cents = val ? Math.round(parseFloat(val.replace(',', '.')) * 100) : null;
                      setFormData({ ...formData, amountInCents: cents });
                      if (formErrors.amountInCents) {
                        setFormErrors({ ...formErrors, amountInCents: undefined });
                      }
                    }}
                    className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent ${formErrors.amountInCents ? 'border-red-500' : 'border-gray-300'}`}
                    placeholder="0,00"
                  />
                  {formErrors.amountInCents && (
                    <p className="mt-1 text-sm text-red-600" role="alert">{formErrors.amountInCents}</p>
                  )}
                </div>
              )}

              <div className="mb-4">
                <label htmlFor="refund-reason" className="block text-sm font-medium text-gray-700 mb-1">
                  Motivo
                </label>
                <select
                  id="refund-reason"
                  value={formData.reason ?? ''}
                  onChange={(e) => {
                    const val = e.target.value as RefundReason | '';
                    setFormData({ ...formData, reason: val || null });
                    if (formErrors.reason) {
                      setFormErrors({ ...formErrors, reason: undefined });
                    }
                  }}
                  className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent ${formErrors.reason ? 'border-red-500' : 'border-gray-300'}`}
                  aria-label="Motivo do estorno"
                >
                  <option value="">Selecione um motivo</option>
                  {(Object.entries(REFUND_REASON_LABELS) as [RefundReason, string][]).map(([value, label]) => (
                    <option key={value} value={value}>{label}</option>
                  ))}
                </select>
                {formErrors.reason && (
                  <p className="mt-1 text-sm text-red-600" role="alert">{formErrors.reason}</p>
                )}
              </div>

              {serverError && (
                <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-md">
                  <p className="text-sm text-red-700">{serverError}</p>
                  <Button variant="ghost" onClick={handleRetry} className="mt-2 text-sm">
                    Tentar novamente
                  </Button>
                </div>
              )}
            </>
          )}

          {step === 'confirm' && (
            <div>
              <div className="bg-gray-50 rounded-md p-4 mb-4 space-y-2">
                <div className="flex justify-between">
                  <span className="text-sm text-gray-500">Transação</span>
                  <span className="text-sm font-mono font-medium">{transaction.transactionId}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-sm text-gray-500">Tipo</span>
                  <span className="text-sm font-medium">
                    {formData.refundType === 'TOTAL' ? 'Estorno total' : 'Estorno parcial'}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-sm text-gray-500">Valor</span>
                  <span className="text-sm font-medium">
                    {formData.refundType === 'TOTAL'
                      ? formatBRL(refundableAmount)
                      : formData.amountInCents ? formatBRL(formData.amountInCents) : '-'}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-sm text-gray-500">Motivo</span>
                  <span className="text-sm font-medium">
                    {formData.reason ? REFUND_REASON_LABELS[formData.reason] : '-'}
                  </span>
                </div>
              </div>

              <div className="mb-4 p-3 bg-blue-50 border border-blue-200 rounded-md">
                <p className="text-sm text-blue-700">
                  O valor será estornado na fatura do cliente em até 5 dias úteis
                </p>
              </div>

              {serverError && (
                <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-md">
                  <p className="text-sm text-red-700">{serverError}</p>
                </div>
              )}
            </div>
          )}
        </div>

        <div className="px-6 py-4 border-t border-gray-200 flex justify-end gap-3">
          {step === 'confirm' ? (
            <>
              <Button variant="secondary" onClick={handleBackToForm} disabled={submitting}>
                Voltar
              </Button>
              <Button onClick={handleSubmit} loading={submitting} disabled={submitting}>
                {submitting ? 'Estornando...' : 'Confirmar estorno'}
              </Button>
            </>
          ) : (
            <>
              <Button variant="secondary" onClick={handleClose}>
                Cancelar
              </Button>
              <Button onClick={handleContinue}>
                Continuar
              </Button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
