import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { Spinner } from './ui/Spinner';

export interface GuestRouteProps {
  children: React.ReactNode;
}

export function GuestRoute({ children }: GuestRouteProps) {
  const { isAuthenticated, isLoading, user } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <Spinner size="lg" />
      </div>
    );
  }

  if (isAuthenticated) {
    // Redirect authenticated users to dashboard based on role
    if (user?.role === 'MERCHANT_OWNER') {
      return <Navigate to="/merchant/transactions" replace />;
    }
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
}
