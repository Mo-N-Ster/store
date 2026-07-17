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
  onMailbox: () => void;
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
  onMailbox,
  theme,
  setTheme,
  lang,
  setLang,
  notify,
}: Props) {
  const { t } = useTranslation();
  const [clock, setClock] = useState(new Date());
  const [alerts, setAlerts] = useState<any[]>([]);
  const [alertsOpen, setAlertsOpen] = useState(false);
  const online = useOnlineStatus();
  useEffect(() => {
    const id = setInterval(() => setClock(new Date()), 1000);
    return () => clearInterval(id);
  }, []);
  useEffect(() => {
    if (user.role === 'employee') return;
    const load = () =>
      dashboardService
        .notifications()
        .then((rows: any[]) => setAlerts(rows.filter((row) => !row.resolved_at)));
    void load();
    const timer = window.setInterval(load, 30000);
    return () => window.clearInterval(timer);
  }, [user.role]);
  return (
    <header>
      {user.role !== 'employee' && (
        <button className="ghost mode-switch" onClick={onMode} title={t('switchWorkspace')}>
          ⇄
        </button>
      )}
      <h2>{title}</h2>
      <time>{clock.toLocaleString()}</time>
      <EmployeePresence notify={notify} />
      <span className="avatar">{user.initials}</span>
      <button className="ghost" onClick={onMailbox} title={t('openMailbox')}>
        ✉
      </button>
      {user.role !== 'employee' && (
        <div className="alerts-control">
          <button
            className={`alert-badge ${alerts.length ? 'critical' : ''}`}
            title={t('stockAlerts')}
            aria-expanded={alertsOpen}
            onClick={() => setAlertsOpen((open) => !open)}
          >
            ⚠ {alerts.length}
          </button>
          {alertsOpen && (
            <section className="alerts-popover critical-popover">
              <b>{t('stockAlerts')}</b>
              {alerts.length ? (
                alerts.map((alert) => (
                  <p key={alert.id}>
                    {alert.productName
                      ? t('stockAlertMessage', {
                          product: alert.productName,
                          stock: alert.currentStock,
                          threshold: alert.threshold,
                        })
                      : alert.message}
                  </p>
                ))
              ) : (
                <p>{t('noStockAlerts')}</p>
              )}
            </section>
          )}
        </div>
      )}
      <span className={online ? 'online-status online' : 'online-status'}>
        {online ? t('online') : t('offline')}
      </span>
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
