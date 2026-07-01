import React from 'react';
import { DesignTokens } from '@/lib/design-tokens';

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
}

export function Input({ label, error, className = '', id, ...props }: InputProps) {
  const inputId = id || label.toLowerCase().replace(/\s+/g, '-');

  return (
    <div className="flex flex-col gap-1">
      <label
        htmlFor={inputId}
        className="text-sm font-medium"
        style={{ color: DesignTokens.colors.text.secondary }}
      >
        {label}
      </label>
      <input
        id={inputId}
        className={`px-3 py-2 border rounded-md transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-offset-2 ${className}`.trim()}
        style={{
          borderColor: error ? DesignTokens.colors.status.error.value : DesignTokens.colors.border.default,
        }}
        {...props}
      />
      {error && (
        <span
          className="text-sm font-medium"
          style={{ color: DesignTokens.colors.status.error.value }}
        >
          {error}
        </span>
      )}
    </div>
  );
}
