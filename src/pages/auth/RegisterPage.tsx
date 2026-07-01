import { useState, type FormEvent } from 'react';
import type { IApiClient, UserRole, RegisterRequest, RegisterResponse } from '@/types/auth';
import { Card, Button } from '@/components/ui';
import { Input } from '@/components/ui/Input';

interface RegisterPageProps {
  apiClient?: IApiClient;
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

const roleStyles = `
  w-full px-3 py-2 border rounded-md transition-all duration-200
  focus:outline-none focus:ring-2 focus:ring-offset-2
  text-base bg-white
`;

export function RegisterPage({ apiClient: _apiClient, navigate }: RegisterPageProps) {
  const apiClient = _apiClient!;
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
    <Card className="w-full max-w-md mx-auto">
      <h1 className="text-2xl font-bold mb-6 text-center">Criar Conta</h1>

      {serverError && (
        <div
          className="bg-red-50 border border-red-200 rounded-md p-3 mb-4"
          role="alert"
        >
          <p className="text-sm text-red-700 font-medium">{serverError}</p>
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
          placeholder="Mínimo 8 caracteres"
          required
          disabled={submitting}
          error={weakPasswordErrors ? 'Requisitos de senha' : undefined}
        />
        {weakPasswordErrors && (
          <ul className="text-xs text-red-600 space-y-0.5 -mt-3">
            {weakPasswordErrors.map((err, i) => (
              <li key={i} className="flex items-center gap-1">
                <span>•</span> {err}
              </li>
            ))}
          </ul>
        )}

        <Input
          label="Nome Completo"
          type="text"
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
          placeholder="Seu nome completo"
          required
          disabled={submitting}
        />

        <div>
          <label htmlFor="role" className="block text-sm font-medium mb-1">
            Tipo de Conta
          </label>
          <select
            id="role"
            value={role}
            onChange={(e) => setRole(e.target.value as UserRole)}
            className={roleStyles}
            disabled={submitting}
          >
            <option value="CUSTOMER">Cliente</option>
            <option value="MERCHANT_OWNER">Proprietário de Loja</option>
          </select>
        </div>

        {role === 'MERCHANT_OWNER' && (
          <>
            <Input
              label="Nome da Empresa"
              type="text"
              value={companyName}
              onChange={(e) => setCompanyName(e.target.value)}
              placeholder="Razão social"
              required
              disabled={submitting}
              error={missingMerchantFields?.includes('companyName') ? 'Campo obrigatório' : undefined}
            />

            <Input
              label="CNPJ"
              type="text"
              value={cnpj}
              onChange={(e) => handleCnpjChange(e.target.value)}
              placeholder="XX.XXX.XXX/XXXX-XX"
              required
              disabled={submitting}
              error={missingMerchantFields?.includes('cnpj') ? 'Campo obrigatório' : undefined}
            />
          </>
        )}

        <Button
          type="submit"
          fullWidth
          loading={submitting}
          disabled={submitting}
          className="text-base font-semibold"
        >
          {submitting ? 'Cadastrando...' : 'Cadastrar'}
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-gray-600">
        Já tem conta?{' '}
        <a href="/login" className="text-blue-600 hover:underline font-medium">
          Entrar
        </a>
      </p>
    </Card>
  );
}
