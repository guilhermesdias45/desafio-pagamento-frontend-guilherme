import React from 'react';
import { DesignTokens } from '@/lib/design-tokens';

export type CardVariant = 'default' | 'elevated' | 'outlined';

export interface CardProps {
  children: React.ReactNode;
  variant?: CardVariant;
  className?: string;
}

const variantClasses: Record<CardVariant, string> = {
  default: 'bg-white border',
  elevated: 'bg-white shadow-md',
  outlined: 'bg-white border-2 border-dashed',
};

export function Card({ children, variant = 'default', className = '' }: CardProps) {
  const variantClass = variantClasses[variant];

  return (
    <div
      className={`rounded-lg p-6 transition-all duration-200 ${variantClass} ${className}`.trim()}
      style={variant === 'outlined' ? { borderColor: DesignTokens.colors.border.dark } : {}}
    >
      {children}
    </div>
  );
}
