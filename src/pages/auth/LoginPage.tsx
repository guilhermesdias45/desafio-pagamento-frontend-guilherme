import { useState, type FormEvent } from 'react';
import type {
  IApiClient,
  IAuthContext,
  LoginResponse,
  LoginRequiresTwoFactorResponse,
  AuthUser,
} from '@/types/auth';

interface LoginPageProps {
  apiClient?: IApiClient;
  authContext?: IAuthContext;
  navigate?: (path: string) => void;
}

export function LoginPage({ apiClient, authContext, navigate }: LoginPageProps) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [serverError, setServerError] = useState<string | null>(null);
  const [cooldownUntil, setCooldownUntil] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const redirect = navigate || ((path: string) => { window.location.href = path; });

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setServerError(null);
    setSubmitting(true);

    try {
      const response = await apiClient.post<{ email: string; password: string }, LoginResponse>(
        '/api/v1/auth/login',
        { email, password }
      );

      if (response.errors && response.errors.length > 0) {
        const error = response.errors[0];
        switch (error.code) {
          case 'INVALID_CREDENTIALS':
            setServerError('Email ou senha inválidos');
            break;
          case 'ACCOUNT_LOCKED': {
            const unlockAt = error.message;
            const date = new Date(unlockAt);
            const time = date.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
            setServerError(`Conta bloqueada até ${time}`);
            break;
          }
          case 'ACCOUNT_NOT_CONFIRMED':
            setServerError('Confirme seu email antes de fazer login');
            break;
          case 'ACCOUNT_DISABLED':
            setServerError('Conta desativada. Entre em contato com o suporte.');
            break;
          case 'TOO_MANY_REQUESTS': {
            setServerError('Muitas tentativas. Aguarde alguns minutos.');
            setCooldownUntil(Date.now() + 60000);
            setTimeout(() => setCooldownUntil(null), 60000);
            break;
          }
          default:
            setServerError(error.message || 'Erro ao fazer login');
        }
        return;
      }

      const data = response.data;
      if (!data) {
        setServerError('Erro ao fazer login');
        return;
      }

      if (data.requiresTwoFactor) {
        redirect(`/2fa/verify?token=${encodeURIComponent(data.twoFactorToken)}&email=${encodeURIComponent(email)}`);
        return;
      }

      authContext.login(data.accessToken, {
        userId: '',
        email,
        role: 'CUSTOMER',
        merchantId: null,
      } as AuthUser);
      redirect('/');
    } catch {
      setServerError('Erro de conexão. Tente novamente.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <h1>Entrar</h1>

      {serverError && (
        <div style={{ color: 'red', marginBottom: 8, fontSize: 14 }}>
          {serverError}
            {serverError.includes('Confirme seu email') && (
              <span> <a href={`/confirm-email?email=${encodeURIComponent(email)}`}>Confirmar email</a></span>
            )}
        </div>
      )}

      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            placeholder="seu@email.com"
          />
        </div>

        <div>
          <label htmlFor="password">Senha</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            placeholder="Sua senha"
          />
        </div>

        <button type="submit" disabled={submitting || cooldownUntil !== null}>
          {submitting ? 'Entrando...' : 'Entrar'}
        </button>
      </form>
    </div>
  );
}
