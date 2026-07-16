import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { authService } from '../../services/authService';

export function ForgotPasswordDialog({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  const [identifier, setIdentifier] = useState('');
  const [recovery, setRecovery] = useState<{ id: number; question: string } | null>(null);
  const [error, setError] = useState('');

  const findQuestion = async (event: React.FormEvent) => {
    event.preventDefault();
    setError('');
    try {
      setRecovery(await authService.forgotPasswordQuestion(identifier));
    } catch {
      setError(t('recoveryUnavailable'));
    }
  };

  const resetPassword = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const values = Object.fromEntries(new FormData(event.currentTarget));
    if (values.newPassword !== values.confirmPassword) {
      setError(t('passwordMismatch'));
      return;
    }
    try {
      await authService.recoverPassword({
        id: recovery!.id,
        answer: values.answer,
        newPassword: values.newPassword,
      });
      alert(t('passwordUpdated'));
      onClose();
    } catch {
      setError(t('invalidSecurityAnswer'));
    }
  };

  return (
    <div className="modal" onMouseDown={onClose}>
      <section className="form-modal" onMouseDown={(event) => event.stopPropagation()}>
        <h2>{t('forgotPassword')}</h2>
        {!recovery ? (
          <form onSubmit={findQuestion}>
            <label>
              {t('username')}
              <input
                value={identifier}
                onChange={(event) => setIdentifier(event.target.value)}
                required
              />
            </label>
            <button>{t('continue')}</button>
          </form>
        ) : (
          <form onSubmit={resetPassword}>
            <label>
              {t('securityQuestion')}
              <input value={recovery.question} readOnly />
            </label>
            <label>
              {t('securityAnswer')}
              <input name="answer" required autoComplete="off" />
            </label>
            <label>
              {t('newPassword')}
              <input name="newPassword" type="password" minLength={8} required />
            </label>
            <label>
              {t('confirmPassword')}
              <input name="confirmPassword" type="password" minLength={8} required />
            </label>
            <button>{t('save')}</button>
          </form>
        )}
        {error && <p className="error">{error}</p>}
        <button className="ghost" onClick={onClose}>
          {t('close')}
        </button>
      </section>
    </div>
  );
}
