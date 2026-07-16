import { useEffect, useState } from 'react';
import type { User } from '../../types';
import { dashboardService } from '../../services/dashboardService';
import { useOnlineStatus } from '../../hooks/useOnlineStatus';
type Props = {
  user: User;
  title: string;
  onMode: () => void;
  onLogout: () => void;
  theme: string;
  setTheme: (v: string) => void;
  lang: string;
  setLang: (v: string) => void;
};
export function Header({ user, title, onMode, onLogout, theme, setTheme, lang, setLang }: Props) {
  const [clock, setClock] = useState(new Date());
  const [alerts, setAlerts] = useState(0);
  const online = useOnlineStatus();
  useEffect(() => {
    const id = setInterval(() => setClock(new Date()), 1000);
    return () => clearInterval(id);
  }, []);
  useEffect(() => {
    if (user.role === 'admin')
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
      <span className="avatar">{user.initials}</span>
      {user.role === 'admin' && (
        <span className="alert-badge" title="Alertes de stock">
          ⚠ {alerts}
        </span>
      )}
      <span className={online ? 'online-status online' : 'online-status'}>
        {online ? 'En ligne' : 'Hors ligne'}
      </span>
      {user.role === 'admin' && (
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
