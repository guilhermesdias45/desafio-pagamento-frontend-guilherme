import { useCallback } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import { ProtectedRoute } from './components/ProtectedRoute';
import { GuestRoute } from './components/GuestRoute';
import { AppLayout } from './components/AppLayout';
import { AuthLayout } from './components/AuthLayout';
import { LoginPage } from './pages/auth/LoginPage';
import { RegisterPage } from './pages/auth/RegisterPage';
import { ConfirmEmailPage } from './pages/auth/ConfirmEmailPage';
import { TwoFactorVerifyPage } from './pages/auth/TwoFactorVerifyPage';
import { TwoFactorSetupPage } from './pages/auth/TwoFactorSetupPage';
import { CreateOrderPage } from './pages/orders/CreateOrderPage';
import { OrderHistoryPage } from './pages/orders/OrderHistoryPage';
import { OrderDetailPage } from './pages/orders/OrderDetailPage';
import { CheckoutPage } from './pages/checkout/CheckoutPage';
import { PaymentResult } from './pages/checkout/PaymentResult';
import { TransactionsListPage } from './pages/merchant/TransactionsListPage';
import { TransactionDetailPage } from './pages/merchant/TransactionDetailPage';
import { AuthDepsWrapper } from './hooks/useAuthAdapter';
import type { PaymentResultData } from './types/checkout';

function PaymentResultWrapper() {
  const location = useLocation();
  const navigate = useNavigate();
  const result = (location.state as { result?: PaymentResultData })?.result;

  const onRetry = useCallback(() => {
    if (result?.orderId) {
      navigate(`/checkout?retry=${result.orderId}`, { replace: true });
    } else {
      navigate('/checkout', { replace: true });
    }
  }, [navigate, result]);

  const onViewOrder = useCallback((orderId: string) => {
    navigate(`/orders/${orderId}`, { replace: true });
  }, [navigate]);

  if (!result) {
    return <Navigate to="/checkout" replace />;
  }

  return <PaymentResult result={result} onRetry={onRetry} onViewOrder={onViewOrder} />;
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<GuestRoute><AuthLayout><AuthDepsWrapper><LoginPage /></AuthDepsWrapper></AuthLayout></GuestRoute>} />
          <Route path="/register" element={<GuestRoute><AuthLayout><AuthDepsWrapper><RegisterPage /></AuthDepsWrapper></AuthLayout></GuestRoute>} />
          <Route path="/confirm-email" element={<GuestRoute><AuthLayout><AuthDepsWrapper><ConfirmEmailPage /></AuthDepsWrapper></AuthLayout></GuestRoute>} />
          <Route path="/2fa/verify" element={<GuestRoute><AuthLayout><AuthDepsWrapper><TwoFactorVerifyPage /></AuthDepsWrapper></AuthLayout></GuestRoute>} />
          <Route path="/2fa/setup" element={<GuestRoute><AuthLayout><AuthDepsWrapper><TwoFactorSetupPage /></AuthDepsWrapper></AuthLayout></GuestRoute>} />
          <Route path="/security" element={<ProtectedRoute requiredRole="CUSTOMER"><AppLayout><TwoFactorSetupPage /></AppLayout></ProtectedRoute>} />

          <Route path="/" element={<ProtectedRoute requiredRole="CUSTOMER"><AppLayout><CreateOrderPage /></AppLayout></ProtectedRoute>} />
          <Route path="/orders" element={<ProtectedRoute requiredRole="CUSTOMER"><AppLayout><OrderHistoryPage /></AppLayout></ProtectedRoute>} />
          <Route path="/orders/:orderId" element={<ProtectedRoute requiredRole="CUSTOMER"><AppLayout><OrderDetailPage /></AppLayout></ProtectedRoute>} />
          <Route path="/checkout" element={<ProtectedRoute requiredRole="CUSTOMER"><AppLayout><CheckoutPage /></AppLayout></ProtectedRoute>} />
          <Route path="/checkout/result" element={<ProtectedRoute requiredRole="CUSTOMER"><AppLayout><PaymentResultWrapper /></AppLayout></ProtectedRoute>} />

          <Route path="/merchant/transactions" element={<ProtectedRoute requiredRole="MERCHANT_OWNER"><AppLayout><TransactionsListPage /></AppLayout></ProtectedRoute>} />
          <Route path="/merchant/transactions/:transactionId" element={<ProtectedRoute requiredRole="MERCHANT_OWNER"><AppLayout><TransactionDetailPage /></AppLayout></ProtectedRoute>} />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
