import React from 'react';
import { Button } from './Button';
import { DesignTokens } from '@/lib/design-tokens';

export interface ErrorMessageProps {
  title: string;
  message: string;
  onRetry?: () => void;
}

export function ErrorMessage({ title, message, onRetry }: ErrorMessageProps) {
  return (
    <div
      className="rounded-md p-4 shadow-md"
      style={{
        backgroundColor: DesignTokens.colors.status.error.light,
        borderColor: DesignTokens.colors.status.error.value,
        borderWidth: '1px',
      }}
    >
      <div className="flex items-start gap-3">
        <svg
          className="h-6 w-6 flex-shrink-0"
          style={{ color: DesignTokens.colors.status.error.value }}
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
          />
        </svg>
        <div className="flex-1">
          <h3
            className="text-sm font-semibold"
            style={{ color: DesignTokens.colors.status.error.dark }}
          >
            {title}
          </h3>
          <p
            className="mt-1 text-sm"
            style={{ color: DesignTokens.colors.status.error.value }}
          >
            {message}
          </p>
          {onRetry && (
            <div className="mt-3">
              <Button
                variant="danger"
                onClick={onRetry}
                className="text-sm px-3 py-1.5"
                style={{
                  backgroundColor: DesignTokens.colors.status.error.value,
                }}
              >
                Tentar novamente
              </Button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
