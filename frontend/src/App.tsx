import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { User } from './types';
import { useTheme } from './hooks/useTheme';
import { useToast } from './hooks/useToast';
import { useOnlineStatus } from './hooks/useOnlineStatus';
import { Login, Setup } from './pages/auth/AuthPages';
import { CashierPage } from './pages/Cashier/CashierPage';
import { DashboardPage } from './pages/Dashboard/DashboardPage';
import { MailboxPage } from './pages/Dashboard/mailbox/MailboxPage';
import { HelpPage } from './pages/Dashboard/help/HelpPage';
import { StockPage } from './pages/Dashboard/stock/StockPage';
import { PurchasesPage } from './pages/Dashboard/purchases/PurchasesPage';
import { TeamPage } from './pages/Dashboard/employees/TeamPage';
import { PresencePage } from './pages/Dashboard/employees/PresencePage';
import type { DashboardSection } from './navigation/navigation';
import { StoreShell } from './components/Layout/StoreShell';
import { authService } from './services/authService';
import { SwitchUserDialog } from './components/UI/SwitchUserDialog';
import { saleService } from './services/saleService';
import { defaultDestination, destinationIsAvailable, navigationFor, type AppDestination } from './navigation/navigation';
import { can, type EffectivePermission, type SessionView } from './security/permissions';

const dashboardSections: Partial<Record<AppDestination, DashboardSection>> = {
  home: 'home', products: 'products', reports: 'charts',
  salesHistory: 'sales', messages: 'mailbox', administration: 'settings',
};

export default function App() {
  const { t, i18n } = useTranslation();
  const [user, setUser] = useState<User | null>(null);
  const [needsSetup, setNeedsSetup] = useState<boolean | null>(null);
  const [startupFailed, setStartupFailed] = useState(false);
  const [sessionFailed, setSessionFailed] = useState(false);
  const [destination, setDestination] = useState<AppDestination>('pos');
  const [permissions, setPermissions] = useState<EffectivePermission[] | null>(null);
  const [switchUserOpen, setSwitchUserOpen] = useState(false);
  const [chatOpen, setChatOpen] = useState(false);
  const [presenceEmployeeId, setPresenceEmployeeId] = useState<number | undefined>();
  const [loginNotice, setLoginNotice] = useState('');
  const [sessionSafety, setSessionSafety] = useState({ cartNonEmpty: false, checkoutCritical: false });
  const [clearCartRequest, setClearCartRequest] = useState(0);
  const history = useRef<AppDestination[]>([]);
  const { theme, setTheme } = useTheme();
  const { toast, notify } = useToast();
  const online = useOnlineStatus();
  const items = useMemo(() => navigationFor(permissions, user?.role), [permissions, user?.role]);
  const titleKeys: Record<AppDestination, string> = {
    home: 'navHome', pos: 'navPos', products: 'navProducts', stock: 'navStock', purchases: 'navPurchases', team: 'navTeam', presence: 'navPresence',
    reports: 'navReports', salesHistory: 'histories', messages: 'navMessages',
    administration: 'navAdministration', help: 'navHelp',
  };

  useEffect(() => {
    authService.needsSetup().then(setNeedsSetup).catch(() => setStartupFailed(true));
  }, []);

  const applySession = (view: SessionView) => {
    setPermissions(view.effectivePermissions);
    setDestination((current) => destinationIsAvailable(view.effectivePermissions, current) ? current : defaultDestination(view.effectivePermissions));
  };
  const refreshSession = async () => {
    setSessionFailed(false);
    try { applySession(await authService.session() as SessionView); }
    catch { setSessionFailed(true); }
  };
  useEffect(() => {
    if (!user) return;
    let active = true;
    const refresh = () => { void authService.session().then((view) => { if (!active) return; setPermissions((current) => JSON.stringify(current) === JSON.stringify(view.effectivePermissions) ? current : view.effectivePermissions); setDestination((current) => destinationIsAvailable(view.effectivePermissions, current) ? current : defaultDestination(view.effectivePermissions)); }).catch((error) => { if (active && String(error?.message).includes('AUTH_REQUIRED')) { setUser(null); setPermissions(null); setChatOpen(false); } }); };
    const timer = window.setInterval(refresh, 10000);
    window.addEventListener('focus', refresh);
    window.addEventListener('store:permissions-updated', refresh);
    return () => { active = false; clearInterval(timer); window.removeEventListener('focus', refresh); window.removeEventListener('store:permissions-updated', refresh); };
  }, [user]);

  const navigate = (next: AppDestination) => {
    if (user?.role === 'employee' && next === 'messages') return;
    setPresenceEmployeeId(undefined);
    if (next === destination || !destinationIsAvailable(permissions, next)) return;
    history.current.push(destination);
    setDestination(next);
  };
  const goBack = () => {
    let previous = history.current.pop();
    while (previous && !destinationIsAvailable(permissions, previous)) previous = history.current.pop();
    setDestination(previous ?? defaultDestination(permissions));
  };
  const prepareIdentityChange = async () => {
    if (sessionSafety.checkoutCritical) { notify(t('checkoutIdentityBlocked')); return false; }
    if (await saleService.currentCashSession()) { notify(t('switchCashBlocked')); return false; }
    if (sessionSafety.cartNonEmpty) {
      if (!window.confirm(t('abandonCartConfirm'))) return false;
      setClearCartRequest((value) => value + 1);
    }
    return true;
  };
  const requestSwitch = async () => {
    try { if (await prepareIdentityChange()) setSwitchUserOpen(true); }
    catch { notify(t('operationFailed')); }
  };
  const requestLogout = async () => {
    if (!await prepareIdentityChange()) return;
    try {
      await authService.logout();
      setUser(null); setPermissions(null); setChatOpen(false); history.current = [];
    } catch (error: any) {
      notify(error?.message?.includes('CASH_SESSION_OPEN') ? t('logoutCashBlocked') : t('operationFailed'));
    }
  };

  if (startupFailed) return <main className="fatal-error"><img className="logo" src="./store-logo.png" alt="STORE" /><h1>{t('startupFailed')}</h1><p>{t('startupFailedMessage')}</p><button onClick={() => window.location.reload()}>{t('retry')}</button></main>;
  if (needsSetup === null) return <div className="splash"><img className="logo" src="./store-logo.png" alt="STORE" /><span>{t('preparingStore')}</span></div>;
  if (needsSetup) return <Setup onDone={(value) => { setNeedsSetup(false); setUser(value); setPermissions(null); history.current = []; void refreshSession(); }} />;
  if (!user) return <Login notice={loginNotice} onLogin={(value) => { setLoginNotice(''); setUser(value); setPermissions(null); history.current = []; void refreshSession(); }} />;
  if (!permissions) return <main className="splash" aria-busy={!sessionFailed}><p role="status">{t(sessionFailed ? 'operationFailed' : 'loading')}</p>{sessionFailed && <button type="button" onClick={() => void refreshSession()}>{t('retry')}</button>}</main>;

  const section = dashboardSections[destination];
  const content = destination === 'pos' ? <CashierPage user={user} notify={notify} clearCartRequest={clearCartRequest} onSessionSafetyChange={setSessionSafety} />
    : destination === 'stock' && permissions ? <section className="store-shell__page"><StockPage userId={user.id} notify={notify} permissions={permissions} /></section>
    : destination === 'purchases' ? <section className="store-shell__page"><PurchasesPage userId={user.id} notify={notify} /></section>
    : destination === 'team' && permissions ? <section className="store-shell__page"><TeamPage notify={notify} permissions={permissions} initialEmployeeId={presenceEmployeeId} /></section>
    : destination === 'presence' && permissions ? <section className="store-shell__page"><PresencePage notify={notify} permissions={permissions} /></section>
    : destination === 'messages' && !can(permissions, 'EMPLOYEES', 'READ') ? <section className="store-shell__page"><MailboxPage user={user} notify={notify} variant="chat" /></section>
      : destination === 'help' ? <section className="store-shell__page"><HelpPage /></section>
        : section && permissions ? <DashboardPage onOpenTeam={() => navigate('team')} onOpenPresence={(id) => { if (!can(permissions, 'PRESENCE', 'READ')) return; navigate(id === undefined ? 'presence' : 'team'); setPresenceEmployeeId(id); }} user={user} permissions={permissions} notify={notify} section={section} onSectionChange={(nextSection) => {
          const next = (nextSection === 'help' ? 'help' : Object.entries(dashboardSections).find(([, value]) => value === nextSection)?.[0]) as AppDestination | undefined;
          if (next) navigate(next);
        }} /> : null;

  return (
    <>
      {toast && <div className="toast" role="status">{toast}</div>}
      <StoreShell user={user} destination={destination} items={items} pageTitle={t(titleKeys[destination])}
        online={online} theme={theme} setTheme={setTheme} language={i18n.language}
        setLanguage={(language) => { void i18n.changeLanguage(language); localStorage.setItem('lang', language); }}
        canGoBack={history.current.length > 0}
        onBack={goBack} onNavigate={navigate} onSwitchUser={() => void requestSwitch()}
        chatOpen={chatOpen} onOpenChat={() => setChatOpen(true)} onCloseChat={() => setChatOpen(false)}
        chatPanel={<MailboxPage user={user} notify={notify} variant="chat" />}
        onLogout={() => void requestLogout()}>
        <div key={`${user.id}:${JSON.stringify(permissions)}`} className="store-session-workspace">{content}</div>
      </StoreShell>
      {switchUserOpen && <SwitchUserDialog onClose={() => setSwitchUserOpen(false)} onSuccess={(nextUser) => {
        setSwitchUserOpen(false);
        setUser(nextUser);
        setDestination('pos');
        setPermissions(null);
        setChatOpen(false);
        history.current = [];
        void refreshSession();
      }} />}
    </>
  );
}
