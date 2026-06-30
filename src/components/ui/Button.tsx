import React from 'react';
import { DesignTokens } from '@/lib/design-tokens';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  loading?: boolean;
  fullWidth?: boolean;
  children: React.ReactNode;
}

const variantClasses: Record<ButtonVariant, string> = {
  primary: `bg-[${DesignTokens.colors.primary[500]}] text-white hover:bg-[${DesignTokens.colors.primary[600]}] focus:ring-2 focus:ring-[${DesignTokens.colors.primary[400]}] focus:ring-offset-2`,
  secondary: `bg-[${DesignTokens.colors.background.gray[200]}] text-[${DesignTokens.colors.text.primary}] hover:bg-[${DesignTokens.colors.background.gray[300]}] focus:ring-2 focus:ring-[${DesignTokens.colors.background.gray[400]}] focus:ring-offset-2`,
  danger: `bg-[${DesignTokens.colors.status.error.value}] text-white hover:bg-[${DesignTokens.colors.status.error.dark}] focus:ring-2 focus:ring-[${DesignTokens.colors.status.error.value}] focus:ring-offset-2`,
  ghost: `bg-transparent text-[${DesignTokens.colors.primary[500]}] hover:bg-[${DesignTokens.colors.background.gray[50]}] focus:ring-2 focus:ring-[${DesignTokens.colors.primary[400]}] focus:ring-offset-2`,
};

const baseClasses = 'px-4 py-2 rounded-md font-medium transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none';

export function Button({
  variant = 'primary',
  loading = false,
  fullWidth = false,
  children,
  className = '',
  disabled,
  ...props
}: ButtonProps) {
  const widthClass = fullWidth ? 'w-full' : '';
  const variantClass = variantClasses[variant];

  return (
    <button
      className={`${baseClasses} ${variantClass} ${widthClass} ${className}`.trim()}
      disabled={disabled || loading}
      {...props}
    >
      {loading ? (
        <span className="flex items-center justify-center gap-2">
          <svg
            className="animate-spin h-5 w-5"
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
          >
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
            <path
              className="opacity-75"
              fill="currentColor"
              d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
            ></path>
          </svg>
          {children}
        </span>
      ) : (
        children
      )}
    </button>
  );
}
