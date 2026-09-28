import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { authService } from '../../services/authService';
import { PasswordInput } from '../../components/UI/PasswordInput';
import { ModalBackdrop } from '../../components/UI/ModalBackdrop';

export function ForgotPasswordDialog({
  onClose,
  onRecovered,
}: {
  onClose: () => void;
  onRecovered: () => void;
}) {
  const { t } = useTranslation();
  const [identifier, setIdentifier] = useState('');
  const [recovery, setRecovery] = useState<{ id: number; question: string } | null>(null);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const findQuestion = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setError('');
    try {
      setRecovery(await authService.forgotPasswordQuestion(identifier));
    } catch {
      setError(t('recoveryUnavailable'));
    } finally {
      setSubmitting(false);
    }
  };

  const resetPassword = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitting) return;
    const values = Object.fromEntries(new FormData(event.currentTarget));
    if (values.newPassword !== values.confirmPassword) {
      setError(t('passwordMismatch'));
      return;
    }
    try {
      setSubmitting(true);
      setError('');
      await authService.recoverPassword({
        id: recovery!.id,
        answer: values.answer,
        newPassword: values.newPassword,
      });
      onRecovered();
    } catch {
      setError(t('invalidSecurityAnswer'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ModalBackdrop onClose={onClose} dismissible={!submitting}>
      <section className="form-modal" onMouseDown={(event) => event.stopPropagation()}>
        <h2>{t('forgotPassword')}</h2>
        {!recovery ? (
          <form onSubmit={findQuestion}>
            <label>
              {t('loginIdentityLabel')}
              <input
                value={identifier}
                placeholder={t('loginIdentityLabel')}
                autoCapitalize="none"
                spellCheck={false}
                onChange={(event) => setIdentifier(event.target.value)}
                required
              />
            </label>
            <button disabled={submitting}>{submitting ? t('authenticationInProgress') : t('continue')}</button>
          </form>
        ) : (
          <form onSubmit={resetPassword}>
            <label>
              {t('securityQuestion')}
              <input value={recovery.question} readOnly />
            </label>
            <label>
              {t('securityAnswer')}
              <input name="answer" required autoComplete="off" placeholder={t('securityAnswer')} />
            </label>
            <label>
              {t('newPassword')}
              <PasswordInput
                name="newPassword"
                minLength={8}
                required
                placeholder={t('newPassword')}
              />
            </label>
            <label>
              {t('confirmPassword')}
              <PasswordInput
                name="confirmPassword"
                minLength={8}
                required
                placeholder={t('confirmPassword')}
              />
            </label>
            <button disabled={submitting}>{submitting ? t('authenticationInProgress') : t('save')}</button>
            <button
              type="button"
              className="ghost"
              disabled={submitting}
              onClick={() => {
                setRecovery(null);
                setError('');
              }}
            >
              {t('changeAccount')}
            </button>
          </form>
        )}
        {error && <p className="error" role="alert">{error}</p>}
        <button className="ghost" onClick={onClose}>
          {t('close')}
        </button>
      </section>
    </ModalBackdrop>
  );
}
