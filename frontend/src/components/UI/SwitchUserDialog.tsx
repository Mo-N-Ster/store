import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { authService } from '../../services/authService';
import type { User } from '../../types';
import { PasswordInput } from './PasswordInput';
import { ModalBackdrop } from './ModalBackdrop';

export function SwitchUserDialog({ onSuccess, onClose }: { onSuccess: (user: User) => void; onClose: () => void }) {
  const { t } = useTranslation();
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const lock = useRef(false);
  const passwordRef = useRef<HTMLInputElement>(null);
  useEffect(() => { if (error && !submitting) passwordRef.current?.focus(); }, [error, submitting]);
  const close = () => { if (lock.current) return; setPassword(''); onClose(); };
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (lock.current) return;
    lock.current = true;
    setSubmitting(true);
    setError('');
    try {
      const user = await authService.switchUser({ identifier, password });
      setPassword('');
      onSuccess(user);
    } catch (reason: any) {
      setPassword('');
      setError(reason?.message?.includes('CASH_SESSION_OPEN') ? t('switchCashBlocked') : t('invalidCredentials'));
    } finally { lock.current = false; setSubmitting(false); }
  };
  return (
    <ModalBackdrop onClose={close} dismissible={!submitting}>
      <form className="form-modal" aria-labelledby="switch-user-title" onSubmit={submit} onMouseDown={(event) => event.stopPropagation()}>
        <h2 id="switch-user-title">{t('switchUser')}</h2>
        <p>{t('switchUserHint')}</p>
        <label>{t('loginIdentityLabel')}<input disabled={submitting} autoFocus autoComplete="username" autoCapitalize="none" spellCheck={false} value={identifier} onChange={(event) => { setIdentifier(event.target.value); setError(''); }} required /></label>
        <label>{t('password')}<PasswordInput ref={passwordRef} disabled={submitting} autoComplete="current-password" value={password} onChange={(event) => { setPassword(event.target.value); setError(''); }} required /></label>
        {error && <p className="error" role="alert">{error}</p>}
        <button disabled={submitting}>{submitting ? t('authenticationInProgress') : t('switchUser')}</button>
        <button type="button" disabled={submitting} className="ghost" onClick={close}>{t('cancel')}</button>
      </form>
    </ModalBackdrop>
  );
}
