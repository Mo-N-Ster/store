import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { User } from '../../../types';
import { employeeService } from '../../../services/employeeService';
export function PasswordResetDialog({
  user,
  onClose,
  notify,
}: {
  user: User;
  onClose: () => void;
  notify: (message: string) => void;
}) {
  const { t } = useTranslation();
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [temporary, setTemporary] = useState('');
  const [remaining, setRemaining] = useState(60);
  useEffect(() => {
    if (user.role === 'admin')
      employeeService
        .securityQuestion(user.id)
        .then(setQuestion)
        .catch((error: any) => notify(error.message));
    else employeeService.resetPassword(user.id).then(setTemporary);
  }, [user.id, user.role]);
  useEffect(() => {
    if (!temporary) return;
    const timer = window.setInterval(
      () =>
        setRemaining((value) => {
          if (value <= 1) {
            window.clearInterval(timer);
            onClose();
            return 0;
          }
          return value - 1;
        }),
      1000,
    );
    return () => window.clearInterval(timer);
  }, [temporary, onClose]);
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    try {
      await employeeService.resetManagerPassword({ id: user.id, answer, newPassword });
      notify(t('newPassword') + ' ✓');
      onClose();
    } catch (error: any) {
      notify(error.message);
    }
  };
  return (
    <div className="modal" onMouseDown={onClose}>
      <section className="form-modal" onMouseDown={(e) => e.stopPropagation()}>
        <h2>
          {t('newPassword')} — {user.username}
        </h2>
        {user.role === 'admin' ? (
          <form onSubmit={submit}>
            <label>
              {t('securityQuestion')}
              <input value={question} readOnly />
            </label>
            <label>
              {t('securityAnswer')}
              <input value={answer} onChange={(e) => setAnswer(e.target.value)} required />
            </label>
            <label>
              {t('newPassword')}
              <input
                type="password"
                minLength={8}
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                required
              />
            </label>
            <button>{t('save')}</button>
          </form>
        ) : (
          <div className="temporary-password">
            <p>Copiez ce mot de passe avant la fermeture :</p>
            <strong>{temporary || '…'}</strong>
            <button onClick={() => navigator.clipboard.writeText(temporary)}>Copier</button>
            <small>{remaining} s</small>
            <progress value={60 - remaining} max="60" />
          </div>
        )}
        <button className="ghost" onClick={onClose}>
          {t('close')}
        </button>
      </section>
    </div>
  );
}
