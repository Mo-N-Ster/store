import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { authService } from '../../services/authService';

export function ManagerAuthModal({
  adminId,
  onSuccess,
  onClose,
}: {
  adminId: number;
  onSuccess: () => void;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (await authService.verifyAdmin({ id: adminId, password })) onSuccess();
    else setError(t('managerPasswordIncorrect'));
  };
  return (
    <div className="modal" onMouseDown={onClose}>
      <form className="form-modal" onSubmit={submit} onMouseDown={(e) => e.stopPropagation()}>
        <h2>{t('managerAccess')}</h2>
        <p>{t('confirmDashboardIdentity')}</p>
        <label>
          {t('password')}
          <input
            autoFocus
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        {error && <p className="error">{error}</p>}
        <button>{t('login')}</button>
        <button type="button" className="ghost" onClick={onClose}>
          {t('close')}
        </button>
      </form>
    </div>
  );
}
