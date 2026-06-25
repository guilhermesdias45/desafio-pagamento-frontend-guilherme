import { useState, type FormEvent } from 'react';
import type { IApiClient, IAuthContext, UserRole, RegisterRequest, RegisterResponse, ApiError } from '@/types/auth';

interface RegisterPageProps {
  apiClient?: IApiClient;
  authContext?: IAuthContext;
  navigate?: (path: string) => void;
}

function formatCNPJ(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 14);
  if (digits.length <= 2) return digits;
  if (digits.length <= 5) return `${digits.slice(0, 2)}.${digits.slice(2)}`;
  if (digits.length <= 8) return `${digits.slice(0, 2)}.${digits.slice(2, 5)}.${digits.slice(5)}`;
  if (digits.length <= 12) return `${digits.slice(0, 2)}.${digits.slice(2, 5)}.${digits.slice(5, 8)}/${digits.slice(8)}`;
  return `${digits.slice(0, 2)}.${digits.slice(2, 5)}.${digits.slice(5, 8)}/${digits.slice(8, 12)}-${digits.slice(12)}`;
}

function validateCNPJ(cnpj: string): boolean {
  const digits = cnpj.replace(/\D/g, '');
  if (digits.length !== 14) return false;
  if (/^(\d)\1{13}$/.test(digits)) return false;

  const calcCheck = (base: string, weights: number[]): number => {
    let sum = 0;
    for (let i = 0; i < base.length; i++) {
      sum += parseInt(base[i]) * weights[i];
    }
    const remainder = sum % 11;
    return remainder < 2 ? 0 : 11 - remainder;
  };

  const w1 = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
  const w2 = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];

  const d1 = calcCheck(digits.slice(0, 12), w1);
  if (d1 !== parseInt(digits[12])) return false;

  const d2 = calcCheck(digits.slice(0, 13), w2);
  if (d2 !== parseInt(digits[13])) return false;

  return true;
}

function getErrorMessage(code: string): string | null {
  switch (code) {
    case 'EMAIL_ALREADY_EXISTS':
      return 'Este email já está cadastrado';
    case 'INVALID_CNPJ':
      return 'CNPJ inválido';
    case 'INVALID_EMAIL_FORMAT':
      return 'Formato de email inválido';
    case 'INVALID_ROLE':
      return 'Tipo de usuário inválido';
    case 'CNPJ_ALREADY_REGISTERED':
      return 'Este CNPJ já está cadastrado';
    default:
      return null;
  }
}

export function RegisterPage({ apiClient, navigate }: RegisterPageProps) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [role, setRole] = useState<UserRole>('CUSTOMER');
  const [companyName, setCompanyName] = useState('');
  const [cnpj, setCnpj] = useState('');
  const [serverError, setServerError] = useState<string | null>(null);
  const [weakPasswordErrors, setWeakPasswordErrors] = useState<string[] | null>(null);
  const [missingMerchantFields, setMissingMerchantFields] = useState<string[] | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const redirect = navigate || ((path: string) => { window.location.href = path; });

  const handleCnpjChange = (value: string) => {
    setCnpj(formatCNPJ(value));
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setServerError(null);
    setWeakPasswordErrors(null);
    setMissingMerchantFields(null);

    if (role === 'MERCHANT_OWNER') {
      const missing: string[] = [];
      if (!companyName.trim()) missing.push('companyName');
      if (cnpj.replace(/\D/g, '').length !== 14) missing.push('cnpj');
      if (missing.length > 0) {
        setMissingMerchantFields(missing);
        return;
      }
      if (!validateCNPJ(cnpj)) {
        setServerError('CNPJ inválido');
        return;
      }
    }

    setSubmitting(true);

    try {
      const body: RegisterRequest = {
        email,
        password,
        fullName,
        role,
      };
      if (role === 'MERCHANT_OWNER') {
        body.companyName = companyName.trim();
        body.cnpj = cnpj.replace(/\D/g, '');
      }

      const response = await apiClient.post<RegisterRequest, RegisterResponse>('/api/v1/auth/register', body);

      if (response.errors && response.errors.length > 0) {
        const error = response.errors[0];
        const msg = getErrorMessage(error.code);
        if (msg) {
          setServerError(msg);
        }
        if (error.code === 'WEAK_PASSWORD') {
          setWeakPasswordErrors([
            'Pelo menos 8 caracteres',
            'Pelo menos 1 letra maiúscula',
            'Pelo menos 1 número',
            'Pelo menos 1 caractere especial',
          ]);
        }
        if (error.code === 'MISSING_MERCHANT_DATA') {
          setMissingMerchantFields(['companyName', 'cnpj']);
        }
        if (!msg && error.code !== 'WEAK_PASSWORD' && error.code !== 'MISSING_MERCHANT_DATA') {
          setServerError(error.message || 'Erro ao cadastrar');
        }
        return;
      }

      redirect(`/confirm-email?email=${encodeURIComponent(email)}`);
    } catch {
      setServerError('Erro de conexão. Tente novamente.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <h1>Criar Conta</h1>

      {serverError && (
        <div style={{ color: 'red', marginBottom: 8 }}>{serverError}</div>
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
            placeholder="Mínimo 8 caracteres"
          />
          {weakPasswordErrors && (
            <ul style={{ color: 'red', fontSize: 12 }}>
              {weakPasswordErrors.map((err, i) => (
                <li key={i}>{err}</li>
              ))}
            </ul>
          )}
        </div>

        <div>
          <label htmlFor="fullName">Nome Completo</label>
          <input
            id="fullName"
            type="text"
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            required
            placeholder="Seu nome completo"
          />
        </div>

        <div>
          <label htmlFor="role">Tipo de Conta</label>
          <select
            id="role"
            value={role}
            onChange={(e) => setRole(e.target.value as UserRole)}
          >
            <option value="CUSTOMER">Cliente</option>
            <option value="MERCHANT_OWNER">Proprietário de Loja</option>
          </select>
        </div>

        {role === 'MERCHANT_OWNER' && (
          <>
            <div>
              <label htmlFor="companyName">Nome da Empresa</label>
              <input
                id="companyName"
                type="text"
                value={companyName}
                onChange={(e) => setCompanyName(e.target.value)}
                placeholder="Razão social"
                data-missing={missingMerchantFields?.includes('companyName') ? 'true' : undefined}
              />
              {missingMerchantFields?.includes('companyName') && (
                <span style={{ color: 'red' }}>Campo obrigatório</span>
              )}
            </div>

            <div>
              <label htmlFor="cnpj">CNPJ</label>
              <input
                id="cnpj"
                type="text"
                value={cnpj}
                onChange={(e) => handleCnpjChange(e.target.value)}
                placeholder="XX.XXX.XXX/XXXX-XX"
                maxLength={18}
                data-missing={missingMerchantFields?.includes('cnpj') ? 'true' : undefined}
              />
              {missingMerchantFields?.includes('cnpj') && (
                <span style={{ color: 'red' }}>Campo obrigatório</span>
              )}
            </div>
          </>
        )}

        <button type="submit" disabled={submitting}>
          {submitting ? 'Cadastrando...' : 'Cadastrar'}
        </button>
      </form>
    </div>
  );
}
