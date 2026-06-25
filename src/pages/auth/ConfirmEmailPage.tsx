import { useState, useEffect, type FormEvent } from 'react';
import type { IApiClient, ConfirmEmailRequest } from '@/types/auth';

interface ConfirmEmailPageProps {
  apiClient?: IApiClient;
  navigate?: (path: string) => void;
}

export function ConfirmEmailPage({ apiClient, navigate }: ConfirmEmailPageProps) {
  const [email, setEmail] = useState('');
  const [token, setToken] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [autoSubmitDone, setAutoSubmitDone] = useState(false);

  const redirect = navigate || ((path: string) => { window.location.href = path; });

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const queryEmail = params.get('email');
    const queryToken = params.get('token');

    if (queryEmail) {
      setEmail(queryEmail);
    }

    if (queryEmail && queryToken) {
      setToken(queryToken);
      handleConfirm(queryEmail, queryToken);
    }
  }, []);

  const handleConfirm = async (emailVal: string, tokenVal: string) => {
    if (submitting || autoSubmitDone) return;
    setSubmitting(true);
    setAutoSubmitDone(true);

    try {
      const body: ConfirmEmailRequest = { email: emailVal, token: tokenVal };
      const response = await apiClient.post<ConfirmEmailRequest, { message: string }>(
        '/api/v1/auth/confirm-email',
        body
      );

      if (response.errors && response.errors.length > 0) {
        setError('Token inválido ou expirado. Solicite um novo link de confirmação.');
        return;
      }

      setSuccess(true);
      setTimeout(() => redirect('/login'), 1500);
    } catch {
      setError('Erro de conexão. Tente novamente.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleManualSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!token.trim()) {
      setError('Token é obrigatório');
      return;
    }

    handleConfirm(email, token.trim());
  };

  if (success) {
    return <div>Email confirmado! Faça seu login.</div>;
  }

  return (
    <div>
      <h1>Confirmar Email</h1>

      {error && <div style={{ color: 'red', marginBottom: 8 }}>{error}</div>}
      {submitting && <div>Confirmando...</div>}

      <form onSubmit={handleManualSubmit}>
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
          <label htmlFor="token">Token de Confirmação</label>
          <input
            id="token"
            type="text"
            value={token}
            onChange={(e) => setToken(e.target.value)}
            placeholder="Cole o token recebido no email"
          />
        </div>

        <button type="submit" disabled={submitting || !token.trim()}>
          Confirmar
        </button>
      </form>
    </div>
  );
}
