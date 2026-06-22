import React from 'react';

export interface LayoutProps {
  children: React.ReactNode;
}

export function AuthLayout({ children }: LayoutProps) {
  return (
    <div className="min-h-screen bg-cream flex items-center justify-center p-4">
      <div className="bg-white rounded-lg shadow-lg p-8 w-full max-w-md">
        {children}
      </div>
    </div>
  );
}
