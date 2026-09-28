import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { User } from '../../types';
import { AuthCard } from '../../components/UI/AuthCard';
import { authService } from '../../services/authService';
import { ForgotPasswordDialog } from './ForgotPasswordDialog';
import { PasswordInput } from '../../components/UI/PasswordInput';
export { Setup } from './SetupWizard';
type View = 'mode' | 'pos' | 'dashboard';
export function Login({ onLogin, notice = '' }: { onLogin: (u: User) => void; notice?: string }) {
  const { t } = useTranslation();
  const [error, setError] = useState('');
  const [role, setRole] = useState('employee');
  const [forgotPassword, setForgotPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const passwordRef = useRef<HTMLInputElement>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const [message, setMessage] = useState(notice);
  const [formVersion, setFormVersion] = useState(0);
  const submit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setError('');
    setMessage('');
    const f = new FormData(e.currentTarget);
    try {
      onLogin(await authService.login(Object.fromEntries(f)));
    } catch {
      setError(t('invalidCredentials'));
      passwordRef.current?.select();
    } finally {
      setSubmitting(false);
    }
  };
  return (
    <AuthCard title="STORE">
      <form key={formVersion} ref={formRef} onSubmit={submit}>
        <input
          name="identifier"
          placeholder={t('loginIdentityLabel')}
          aria-label={t('loginIdentityLabel')}
          autoCapitalize="none"
          spellCheck={false}
          required
          autoComplete="username"
          onChange={() => setError('')}
        />
        <PasswordInput
          ref={passwordRef}
          name="password"
          placeholder={t('password')}
          required
          autoComplete="current-password"
          onChange={() => setError('')}
        />
        <select name="role" value={role} onChange={(event) => setRole(event.target.value)}>
          <option value="employee">{t('employee')}</option>
          <option value="manager">{t('manager')}</option>
        </select>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        {message && (
          <p className="success" role="status">
            {message}
          </p>
        )}
        <button disabled={submitting}>
          {submitting ? t('authenticationInProgress') : t('login')}
        </button>
        {role === 'manager' && (
          <button type="button" className="ghost" onClick={() => setForgotPassword(true)}>
            {t('forgotPassword')}
          </button>
        )}
      </form>
      {forgotPassword && (
        <ForgotPasswordDialog
          onClose={() => setForgotPassword(false)}
          onRecovered={() => {
            formRef.current?.reset();
            setFormVersion((version) => version + 1);
            setRole('manager');
            setError('');
            setMessage(t('passwordUpdated'));
            setForgotPassword(false);
          }}
        />
      )}
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
          <b>{t('cashier')}</b>
        </button>
        <button onClick={() => choose('dashboard')}>
          <b>{t('dashboard')}</b>
        </button>
      </div>
    </main>
  );
}
