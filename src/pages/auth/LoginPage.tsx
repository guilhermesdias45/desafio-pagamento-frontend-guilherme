import { useState, type FormEvent } from 'react';
import type {
  IApiClient,
  IAuthContext,
  LoginResponse,
  LoginRequiresTwoFactorResponse,
  AuthUser,
} from '@/types/auth';
import { decodeJwt } from '@/lib/jwt';
import { Card, Button } from '@/components/ui';
import { Input } from '@/components/ui/Input';

interface LoginPageProps {
  apiClient?: IApiClient;
  authContext?: IAuthContext;
  navigate?: (path: string) => void;
}

export function LoginPage({ apiClient: _apiClient, authContext: _authContext, navigate }: LoginPageProps) {
  const apiClient = _apiClient!;
  const authContext = _authContext!;
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

      const claims = decodeJwt(data.accessToken);
      authContext.login(data.accessToken, {
        userId: claims?.sub ?? '',
        email: claims?.email ?? email,
        role: claims?.role ?? 'CUSTOMER',
        merchantId: claims?.merchantId ?? null,
      } as AuthUser);
      redirect('/');
    } catch {
      setServerError('Erro de conexão. Tente novamente.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card className="w-full max-w-md mx-auto">
      <h1 className="text-2xl font-bold mb-6 text-center">Entrar</h1>

      {serverError && (
        <div
          className="bg-red-50 border border-red-200 rounded-md p-3 mb-4"
          role="alert"
        >
          <p className="text-sm text-red-700 font-medium">{serverError}</p>
          {serverError.includes('Confirme seu email') && (
            <p className="text-sm mt-2">
              <a
                href={`/confirm-email?email=${encodeURIComponent(email)}`}
                className="text-blue-600 hover:underline font-medium"
              >
                Confirmar email
              </a>
            </p>
          )}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <Input
          label="Email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="seu@email.com"
          required
          disabled={submitting}
        />

        <Input
          label="Senha"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="Sua senha"
          required
          disabled={submitting}
        />

        <Button
          type="submit"
          fullWidth
          loading={submitting}
          disabled={submitting || cooldownUntil !== null}
          className="text-base font-semibold"
        >
          {submitting ? 'Entrando...' : 'Entrar'}
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-gray-600">
        Não tem conta?{' '}
        <a href="/register" className="text-blue-600 hover:underline font-medium">
          Cadastre-se
        </a>
      </p>
    </Card>
  );
}
