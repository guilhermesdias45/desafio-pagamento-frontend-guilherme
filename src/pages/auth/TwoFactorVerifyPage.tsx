import { useState, type FormEvent } from 'react';
import type {
  IApiClient,
  IAuthContext,
  TwoFactorVerifyRequest,
  TwoFactorRecoveryRequest,
  LoginSuccessResponse,
  AuthUser,
} from '@/types/auth';

interface TwoFactorVerifyPageProps {
  apiClient?: IApiClient;
  authContext?: IAuthContext;
  navigate?: (path: string) => void;
  twoFactorToken?: string;
  email?: string;
}

export function TwoFactorVerifyPage({
  apiClient,
  authContext,
  navigate,
  twoFactorToken: tokenProp,
  email: emailProp,
}: TwoFactorVerifyPageProps) {
  const [totpCode, setTotpCode] = useState('');
  const [recoveryCode, setRecoveryCode] = useState('');
  const [useRecovery, setUseRecovery] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const redirect = navigate || ((path: string) => { window.location.href = path; });

  const queryToken = new URLSearchParams(window.location.search).get('token');
  const queryEmail = new URLSearchParams(window.location.search).get('email');
  const twoFactorToken = tokenProp || queryToken || '';
  const email = emailProp || queryEmail || '';

  const handleTOTPSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);

    if (totpCode.length !== 6 || !/^\d{6}$/.test(totpCode)) {
      setError('Código deve ter 6 dígitos');
      return;
    }

    setSubmitting(true);

    try {
      const body: TwoFactorVerifyRequest = { twoFactorToken, totpCode };
      const response = await apiClient.post<TwoFactorVerifyRequest, LoginSuccessResponse>(
        '/api/v1/auth/2fa/verify',
        body
      );

      if (response.errors && response.errors.length > 0) {
        const err = response.errors[0];
        if (err.code === 'INVALID_TOTP_CODE') {
          setError('Código inválido ou expirado');
        } else {
          setError(err.message || 'Erro na verificação');
        }
        return;
      }

      if (response.data) {
        authContext.login(response.data.accessToken, {
          userId: '',
          email,
          role: 'CUSTOMER',
          merchantId: null,
        } as AuthUser);
        redirect('/');
      }
    } catch {
      setError('Erro de conexão. Tente novamente.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleRecoverySubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!recoveryCode.trim()) {
      setError('Código de recuperação é obrigatório');
      return;
    }

    setSubmitting(true);

    try {
      const body: TwoFactorRecoveryRequest = { email, recoveryCode: recoveryCode.trim() };
      const response = await apiClient.post<TwoFactorRecoveryRequest, LoginSuccessResponse>(
        '/api/v1/auth/2fa/recovery',
        body
      );

      if (response.errors && response.errors.length > 0) {
        const err = response.errors[0];
        if (err.code === 'RECOVERY_CODE_INVALID') {
          setError('Código de recuperação inválido');
        } else if (err.code === 'RECOVERY_CODE_EXHAUSTED') {
          setError('Todos os códigos de recuperação foram usados. Reconfigure o 2FA.');
        } else {
          setError(err.message || 'Erro na verificação');
        }
        return;
      }

      if (response.data) {
        authContext.login(response.data.accessToken, {
          userId: '',
          email,
          role: 'CUSTOMER',
          merchantId: null,
        } as AuthUser);
        redirect('/');
      }
    } catch {
      setError('Erro de conexão. Tente novamente.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <h1>Verificação em Duas Etapas</h1>

      {error && <div style={{ color: 'red', marginBottom: 8 }}>{error}</div>}

      {!useRecovery ? (
        <form onSubmit={handleTOTPSubmit}>
          <div>
            <label htmlFor="totpCode">Código TOTP (6 dígitos)</label>
            <input
              id="totpCode"
              type="text"
              inputMode="numeric"
              maxLength={6}
              value={totpCode}
              onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, ''))}
              placeholder="000000"
              required
            />
          </div>

          <button type="submit" disabled={submitting || totpCode.length !== 6}>
            {submitting ? 'Verificando...' : 'Verificar'}
          </button>

          <button type="button" onClick={() => { setUseRecovery(true); setError(null); }} style={{ background: 'none', border: 'none', color: '#5B8DEE', cursor: 'pointer', textDecoration: 'underline', marginTop: 8 }}>
            Usar código de recuperação
          </button>
        </form>
      ) : (
        <form onSubmit={handleRecoverySubmit}>
          <div>
            <label htmlFor="recoveryCode">Código de Recuperação</label>
            <input
              id="recoveryCode"
              type="text"
              value={recoveryCode}
              onChange={(e) => setRecoveryCode(e.target.value)}
              placeholder="XXXX-XXXX-XXXX-XXXX"
              required
            />
          </div>

          <button type="submit" disabled={submitting}>
            {submitting ? 'Verificando...' : 'Verificar'}
          </button>

          <button type="button" onClick={() => { setUseRecovery(false); setError(null); }} style={{ background: 'none', border: 'none', color: '#5B8DEE', cursor: 'pointer', textDecoration: 'underline', marginTop: 8 }}>
            Usar código TOTP
          </button>
        </form>
      )}
    </div>
  );
}
