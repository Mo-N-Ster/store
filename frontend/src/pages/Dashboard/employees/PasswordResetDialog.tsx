import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { User } from '../../../types';
import { employeeService } from '../../../services/employeeService';
import { Button, Modal } from '../../../design-system';
import { PasswordInput } from '../../../components/UI/PasswordInput';

export function PasswordResetDialog({ user, onClose, notify, temporaryPassword }: { user: User; onClose: () => void; notify: (message: string) => void; temporaryPassword?: string }) {
  const { t } = useTranslation();
  const [mode, setMode] = useState<'manual' | 'automatic'>('automatic');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [error, setError] = useState('');
  const [temporary, setTemporary] = useState(temporaryPassword || '');
  const [remaining, setRemaining] = useState(60);
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const targetId = useRef(user.id);
  const clearSecrets = () => { setNewPassword(''); setConfirmation(''); setTemporary(''); };
  const close = () => { clearSecrets(); setError(''); onClose(); };
  useEffect(() => {
    if (targetId.current === user.id) return;
    targetId.current = user.id;
    clearSecrets(); setError(''); setMode('automatic'); setRemaining(60);
  }, [user.id]);
  useEffect(() => {
    if (!temporary) return;
    const timer = window.setInterval(() => setRemaining((value) => {
      if (value <= 1) { window.clearInterval(timer); setTemporary(''); return 0; }
      return value - 1;
    }), 1000);
    return () => window.clearInterval(timer);
  }, [temporary]);
  const run = async (operation: () => Promise<void>) => {
    if (lock.current) return;
    lock.current = true; setBusy(true); setError('');
    try { await operation(); }
    catch (caught: any) {
      const code = String(caught?.message || '');
      setError(code.includes('FORBIDDEN') ? t('passwordResetForbidden') : code.includes('WEAK_PASSWORD') ? t('passwordMinimum') : t('operationFailed'));
    } finally { lock.current = false; setBusy(false); }
  };
  const generate = () => run(async () => { const value = await employeeService.generateUserPassword(user.id); setTemporary(value); setRemaining(60); });
  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (newPassword !== confirmation) { setError(t('passwordMismatch')); return; }
    void run(async () => { await employeeService.setUserPassword({ id: user.id, newPassword }); clearSecrets(); notify(t('passwordResetDone')); onClose(); });
  };
  return <Modal title={`${t('passwordDefinition')} — ${user.username}`} description={t('passwordDefinitionHint')} closeLabel={t('close')} onClose={close} dismissible={!busy}>
    <fieldset className="password-mode" disabled={busy || Boolean(temporary)}>
      <legend>{t('passwordMode')}</legend>
      <label><input type="radio" name="password-mode" value="automatic" checked={mode === 'automatic'} onChange={() => { clearSecrets(); setMode('automatic'); setError(''); }} />{t('passwordAutomatic')}</label>
      <label><input type="radio" name="password-mode" value="manual" checked={mode === 'manual'} onChange={() => { clearSecrets(); setMode('manual'); setError(''); }} />{t('passwordManual')}</label>
    </fieldset>
    {error && <p className="ds-field__error" role="alert">{error}</p>}
    {temporary ? <div className="temporary-password"><p>{t('copyPasswordBeforeClosing')}</p><strong>{temporary}</strong><Button variant="secondary" onClick={() => navigator.clipboard.writeText(temporary)}>{t('copy')}</Button><small>{t('expiresInSeconds', { count: remaining })}</small><progress value={60 - remaining} max="60" /></div>
      : mode === 'automatic' ? <div className="temporary-password"><p>{t('temporaryPasswordGenerateWarning')}</p><Button loading={busy} loadingLabel={t('working')} onClick={() => void generate()}>{t('generateTemporaryPassword')}</Button></div>
        : <form className="ops-form" onSubmit={submit} autoComplete="off"><label className="ds-field"><span className="ds-field__label">{t('newPassword')}</span><PasswordInput minLength={8} value={newPassword} onChange={(event) => setNewPassword(event.target.value)} autoComplete="new-password" required /></label><label className="ds-field"><span className="ds-field__label">{t('confirmPassword')}</span><PasswordInput minLength={8} value={confirmation} onChange={(event) => setConfirmation(event.target.value)} autoComplete="new-password" required /></label><div className="ops-form__actions"><Button type="button" variant="secondary" onClick={close}>{t('cancel')}</Button><Button type="submit" loading={busy} loadingLabel={t('working')}>{t('save')}</Button></div></form>}
  </Modal>;
}
