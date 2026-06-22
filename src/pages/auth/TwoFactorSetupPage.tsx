import { useState, useEffect, type FormEvent } from 'react';
import type {
  IApiClient,
  IAuthContext,
  TwoFactorSetupResponse,
} from '@/types/auth';

interface TwoFactorSetupPageProps {
  apiClient: IApiClient;
  authContext: IAuthContext;
  navigate?: (path: string) => void;
}

export function TwoFactorSetupPage({ apiClient, authContext, navigate }: TwoFactorSetupPageProps) {
  const [setup, setSetup] = useState<TwoFactorSetupResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [confirmCode, setConfirmCode] = useState('');
  const [confirmError, setConfirmError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const [copied, setCopied] = useState(false);

  const redirect = navigate || ((path: string) => { window.location.href = path; });

  useEffect(() => {
    let cancelled = false;

    async function loadSetup() {
      try {
        const response = await apiClient.post<void, TwoFactorSetupResponse>('/api/v1/auth/2fa/setup');
        if (cancelled) return;

        if (response.errors && response.errors.length > 0) {
          const err = response.errors[0];
          if (err.code === 'TWO_FACTOR_ALREADY_ENABLED') {
            redirect('/security');
            return;
          }
          setError(err.message || 'Erro ao carregar configuração');
          return;
        }

        if (response.data) {
          setSetup(response.data);
        }
      } catch {
        if (!cancelled) setError('Erro de conexão');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    loadSetup();
    return () => { cancelled = true; };
  }, []);

  const handleCopySecret = async () => {
    if (setup?.secret) {
      try {
        await navigator.clipboard.writeText(setup.secret);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      } catch {
        // fallback: select text
      }
    }
  };

  const handleConfirm = async (e: FormEvent) => {
    e.preventDefault();
    setConfirmError(null);

    if (!confirmCode.trim()) {
      setConfirmError('Código é obrigatório');
      return;
    }

    setConfirming(true);

    try {
      const response = await apiClient.post<{ code: string }, { message: string }>(
        '/api/v1/auth/2fa/confirm',
        { code: confirmCode }
      );

      if (response.errors && response.errors.length > 0) {
        const err = response.errors[0];
        if (err.code === 'INVALID_TOTP_CODE') {
          setConfirmError('Código inválido. Tente novamente.');
        } else if (err.code === 'TWO_FACTOR_ALREADY_ENABLED') {
          redirect('/security');
          return;
        } else {
          setConfirmError(err.message || 'Erro ao confirmar');
        }
        return;
      }

      setConfirmed(true);
      setTimeout(() => redirect('/security'), 2000);
    } catch {
      setConfirmError('Erro de conexão. Tente novamente.');
    } finally {
      setConfirming(false);
    }
  };

  if (loading) {
    return <div>Carregando...</div>;
  }

  if (error) {
    return <div style={{ color: 'red' }}>{error}</div>;
  }

  if (confirmed) {
    return <div>2FA ativado com sucesso! Redirecionando...</div>;
  }

  if (!setup) {
    return <div>Nenhum dado de configuração disponível.</div>;
  }

  return (
    <div>
      <h1>Configurar Autenticação em Duas Etapas</h1>

      {setup.qrCodeUrl && (
        <div>
          <p>Escaneie o QR code com seu aplicativo autenticador:</p>
          <img src={setup.qrCodeUrl} alt="QR Code 2FA" style={{ width: 200, height: 200 }} />
        </div>
      )}

      <div>
        <p>Ou insira manualmente a chave secreta:</p>
        <code style={{ fontSize: 14, background: '#f0f0f0', padding: '4px 8px', borderRadius: 4 }}>
          {setup.secret}
        </code>
        <button type="button" onClick={handleCopySecret} style={{ marginLeft: 8 }}>
          {copied ? 'Copiado!' : 'Copiar'}
        </button>
      </div>

      <div>
        <p style={{ fontWeight: 'bold', color: '#d32f2f' }}>
          Salve estes códigos em um local seguro. Eles não serão exibidos novamente.
        </p>
        <ol>
          {setup.recoveryCodes.map((code, index) => (
            <li key={index}>
              <code>{code}</code>
            </li>
          ))}
        </ol>
      </div>

      <hr />

      <h2>Confirmar Configuração</h2>
      <p>Digite o primeiro código TOTP gerado pelo seu aplicativo autenticador:</p>

      {confirmError && <div style={{ color: 'red', marginBottom: 8 }}>{confirmError}</div>}

      <form onSubmit={handleConfirm}>
        <div>
          <label htmlFor="confirmCode">Código TOTP</label>
          <input
            id="confirmCode"
            type="text"
            inputMode="numeric"
            maxLength={6}
            value={confirmCode}
            onChange={(e) => setConfirmCode(e.target.value.replace(/\D/g, ''))}
            placeholder="000000"
            required
          />
        </div>

        <button type="submit" disabled={confirming || confirmCode.length !== 6}>
          {confirming ? 'Confirmando...' : 'Confirmar'}
        </button>
      </form>
    </div>
  );
}
