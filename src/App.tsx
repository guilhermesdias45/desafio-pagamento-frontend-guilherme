import { BrowserRouter, Routes, Route } from 'react-router-dom';
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
import { RefundModal } from './pages/merchant/RefundModal';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<GuestRoute><AuthLayout /></GuestRoute>}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/confirm-email" element={<ConfirmEmailPage />} />
          <Route path="/2fa/verify" element={<TwoFactorVerifyPage />} />
          <Route path="/2fa/setup" element={<TwoFactorSetupPage />} />
        </Route>

        <Route element={<ProtectedRoute requiredRole="CUSTOMER"><AppLayout /></ProtectedRoute>}>
          <Route path="/" element={<CreateOrderPage />} />
          <Route path="/orders" element={<OrderHistoryPage />} />
          <Route path="/orders/:orderId" element={<OrderDetailPage />} />
          <Route path="/checkout" element={<CheckoutPage />} />
          <Route path="/checkout/result" element={<PaymentResult />} />
        </Route>

        <Route element={<ProtectedRoute requiredRole="MERCHANT_OWNER"><AppLayout /></ProtectedRoute>}>
          <Route path="/merchant/transactions" element={<TransactionsListPage />} />
          <Route path="/merchant/transactions/:transactionId" element={<TransactionDetailPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
