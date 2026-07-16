import { useEffect, useState } from 'react';
import type { User } from '../../types';
import { dashboardService } from '../../services/dashboardService';
import { useOnlineStatus } from '../../hooks/useOnlineStatus';
import { useTranslation } from 'react-i18next';
import { EmployeePresence } from '../common/EmployeePresence';
type Props = {
  user: User;
  title: string;
  onMode: () => void;
  onLogout: () => void;
  theme: string;
  setTheme: (v: string) => void;
  lang: string;
  setLang: (v: string) => void;
  notify: (message: string) => void;
};
export function Header({
  user,
  title,
  onMode,
  onLogout,
  theme,
  setTheme,
  lang,
  setLang,
  notify,
}: Props) {
  const { t } = useTranslation();
  const [clock, setClock] = useState(new Date());
  const [alerts, setAlerts] = useState(0);
  const online = useOnlineStatus();
  useEffect(() => {
    const id = setInterval(() => setClock(new Date()), 1000);
    return () => clearInterval(id);
  }, []);
  useEffect(() => {
    if (user.role !== 'employee')
      dashboardService
        .notifications()
        .then((rows: any[]) =>
          setAlerts(rows.filter((row) => !row.is_read && !row.resolved_at).length),
        );
  }, [user.role]);
  return (
    <header>
      <h2>{title}</h2>
      <time>{clock.toLocaleString()}</time>
      <EmployeePresence notify={notify} />
      <span className="avatar">{user.initials}</span>
      {user.role !== 'employee' && (
        <span className="alert-badge" title={t('stockAlerts')}>
          ⚠ {alerts}
        </span>
      )}
      <span className={online ? 'online-status online' : 'online-status'}>
        {online ? t('online') : t('offline')}
      </span>
      {user.role !== 'employee' && (
        <button className="ghost" onClick={onMode}>
          ⇄
        </button>
      )}
      <button className="ghost" onClick={() => setLang(lang === 'fr' ? 'en' : 'fr')}>
        {lang.toUpperCase()}
      </button>
      <button className="ghost" onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}>
        {theme === 'light' ? '☾' : '☀'}
      </button>
      <button className="danger ghost" onClick={onLogout}>
        ⏻
      </button>
    </header>
  );
}
