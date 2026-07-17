import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { User } from './types';
import { useTheme } from './hooks/useTheme';
import { useToast } from './hooks/useToast';
import { Header } from './components/Layout/Header';
import { Login, Mode, Setup } from './pages/auth/AuthPages';
import { CashierPage } from './pages/Cashier/CashierPage';
import { DashboardPage } from './pages/Dashboard/DashboardPage';
import { authService } from './services/authService';
import { ManagerAuthModal } from './components/UI/ManagerAuthModal';
type View = 'mode' | 'pos' | 'dashboard';
export default function App() {
  const { t, i18n } = useTranslation();
  const [user, setUser] = useState<User | null>(null);
  const [needsSetup, setNeedsSetup] = useState<boolean | null>(null);
  const [view, setView] = useState<View>('mode');
  const [managerAuth, setManagerAuth] = useState(false);
  const { theme, setTheme } = useTheme();
  const { toast, notify } = useToast();
  useEffect(() => {
    authService.needsSetup().then(setNeedsSetup);
  }, []);
  if (needsSetup === null)
    return (
      <div className="splash">
        <div className="logo">S</div>
        <span>{t('preparingStore')}</span>
      </div>
    );
  if (needsSetup)
    return (
      <Setup
        onDone={(value) => {
          setNeedsSetup(false);
          setUser(value);
          setView('mode');
        }}
      />
    );
  if (!user)
    return (
      <Login
        onLogin={(value) => {
          setUser(value);
          setView(value.role !== 'employee' ? 'mode' : 'pos');
        }}
      />
    );
  const header = (
    <Header
      user={user}
      title={view === 'pos' ? t('cashier') : t('dashboard')}
      onMode={() =>
        view === 'pos' && user.role !== 'employee' ? setManagerAuth(true) : setView('pos')
      }
      onLogout={() => setUser(null)}
      theme={theme}
      setTheme={setTheme}
      lang={i18n.language}
      setLang={(language) => {
        void i18n.changeLanguage(language);
        localStorage.setItem('lang', language);
      }}
      notify={notify}
    />
  );
  return (
    <>
      {toast && <div className="toast">{toast}</div>}
      {view === 'mode' ? (
        <Mode user={user} choose={setView} />
      ) : (
        <>
          {header}
          {view === 'pos' ? (
            <CashierPage user={user} notify={notify} />
          ) : (
            <DashboardPage user={user} notify={notify} />
          )}
        </>
      )}
      {managerAuth && (
        <ManagerAuthModal
          adminId={user.id}
          onClose={() => setManagerAuth(false)}
          onSuccess={() => {
            setManagerAuth(false);
            setView('dashboard');
          }}
        />
      )}
    </>
  );
}
