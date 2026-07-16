import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { User } from '../../types';
import { AuthCard } from '../../components/UI/AuthCard';
import { authService } from '../../services/authService';
type View = 'mode' | 'pos' | 'dashboard';
export function Setup({ onDone }: { onDone: (u: User) => void }) {
  const { t } = useTranslation();
  const submit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    try {
      onDone(await authService.setupAdmin(Object.fromEntries(f)));
    } catch {
      alert('Vérifiez les champs (mot de passe: 8 caractères minimum).');
    }
  };
  return (
    <AuthCard title={t('setup')}>
      <form onSubmit={submit}>
        <input name="firstName" placeholder={t('firstName')} required />
        <input name="lastName" placeholder={t('lastName')} required />
        <input name="username" placeholder={t('username')} required />
        <input name="email" type="email" placeholder={t('email')} required />
        <input name="password" type="password" minLength={8} placeholder={t('password')} required />
        <input name="securityQuestion" placeholder={t('securityQuestion')} required />
        <input name="securityAnswer" placeholder={t('securityAnswer')} required />
        <button>{t('save')}</button>
      </form>
    </AuthCard>
  );
}
export function Login({ onLogin }: { onLogin: (u: User) => void }) {
  const { t } = useTranslation();
  const [error, setError] = useState('');
  const submit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    try {
      onLogin(await authService.login(Object.fromEntries(f)));
    } catch {
      setError('Identifiants invalides ou rôle non autorisé.');
    }
  };
  return (
    <AuthCard title="STORE">
      <form onSubmit={submit}>
        <input name="identifier" placeholder={t('username')} required />
        <input name="password" type="password" placeholder={t('password')} required />
        <select name="role">
          <option value="employee">{t('employee')}</option>
          <option value="admin">{t('admin')}</option>
        </select>
        {error && <p className="error">{error}</p>}
        <button>{t('login')}</button>
      </form>
    </AuthCard>
  );
}
export function Mode({ user, choose }: { user: User; choose: (v: View) => void }) {
  const { t } = useTranslation();
  return (
    <main className="mode">
      <h1>
        {t('welcome')}, {user.first_name || user.firstName}
      </h1>
      <p>{t('chooseMode')}</p>
      <div>
        <button onClick={() => choose('pos')}>
          🛒<b>{t('cashier')}</b>
        </button>
        <button onClick={() => choose('dashboard')}>
          📊<b>{t('dashboard')}</b>
        </button>
      </div>
    </main>
  );
}
