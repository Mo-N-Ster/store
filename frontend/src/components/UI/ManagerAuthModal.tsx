import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { authService } from '../../services/authService';
import type { User } from '../../types';

export function ManagerAuthModal({
  currentUser,
  onSuccess,
  onClose,
}: {
  currentUser: User;
  onSuccess: () => void;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const [password, setPassword] = useState('');
  const [identifier, setIdentifier] = useState('');
  const [error, setError] = useState('');
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    try {
      const authenticated =
        currentUser.role === 'employee'
          ? await authService.login({ identifier, password, role: 'manager' })
          : await authService.verifyAdmin({ id: currentUser.id, password });
      if (authenticated) onSuccess();
      else setError(t('managerPasswordIncorrect'));
    } catch {
      setError(t('managerPasswordIncorrect'));
    }
  };
  return (
    <div className="modal" onMouseDown={onClose}>
      <form className="form-modal" onSubmit={submit} onMouseDown={(e) => e.stopPropagation()}>
        <h2>{t('managerAccess')}</h2>
        <p>{t('confirmDashboardIdentity')}</p>
        {currentUser.role === 'employee' && (
          <label>
            {t('username')}
            <input
              autoFocus
              value={identifier}
              onChange={(event) => setIdentifier(event.target.value)}
              required
            />
          </label>
        )}
        <label>
          {t('password')}
          <input
            autoFocus={currentUser.role !== 'employee'}
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
