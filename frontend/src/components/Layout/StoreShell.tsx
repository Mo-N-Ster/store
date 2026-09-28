import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import {
  BarChart3,
  Boxes,
  ChevronLeft,
  CircleHelp,
  Home,
  LogOut,
  Menu,
  MessageSquare,
  MonitorCog,
  Package,
  PanelLeftClose,
  PanelLeftOpen,
  Settings,
  UserRoundCog,
  ShoppingCart,
  SunMoon,
  Truck,
  Users,
  UserCheck,
  WifiOff,
  type LucideIcon,
} from 'lucide-react';
import { AppShell, Badge, Drawer, IconButton, ShellHeader, ShellSidebar } from '../../design-system';
import type { User } from '../../types';
import type { AppDestination, NavigationItem } from '../../navigation/navigation';

const icons: Record<AppDestination, LucideIcon> = {
  home: Home,
  pos: ShoppingCart,
  products: Package,
  stock: Boxes,
  purchases: Truck,
  team: Users,
  presence: UserCheck,
  reports: BarChart3,
  salesHistory: BarChart3,
  messages: MessageSquare,
  administration: Settings,
  help: CircleHelp,
};

type Props = {
  user: User;
  destination: AppDestination;
  items: NavigationItem[];
  pageTitle: string;
  children: ReactNode;
  online: boolean;
  theme: string;
  setTheme: (value: string) => void;
  language: string;
  setLanguage: (value: string) => void;
  canGoBack: boolean;
  onBack: () => void;
  onNavigate: (destination: AppDestination) => void;
  onSwitchUser: () => void;
  onLogout: () => void;
  chatOpen: boolean;
  onOpenChat: () => void;
  onCloseChat: () => void;
  chatPanel: ReactNode;
};

function Navigation({ items, active, compact, onSelect, onToggleCompact }: { items: NavigationItem[]; active: AppDestination; compact: boolean; onSelect: (destination: AppDestination) => void; onToggleCompact?: () => void }) {
  const { t } = useTranslation();
  const groups = ['operations', 'management', 'system'] as const;
  return (
    <div className="store-nav__content">
      <div className="store-nav__brand">
        <img src="./store-logo.png" alt="" />
        {!compact && <strong>STORE</strong>}
        {onToggleCompact && <IconButton className="store-shell__collapse" icon={compact ? PanelLeftOpen : PanelLeftClose} label={compact ? t('expandNavigation') : t('collapseNavigation')} onClick={onToggleCompact} />}
      </div>
      {groups.map((group) => {
        const groupItems = items.filter((item) => item.group === group);
        if (!groupItems.length) return null;
        return (
          <section className="store-nav__group" key={group} aria-labelledby={`nav-${group}`}>
            {!compact && <h2 id={`nav-${group}`}>{t(`navGroup${group[0].toUpperCase()}${group.slice(1)}`)}</h2>}
            {groupItems.map((item) => {
              const Icon = icons[item.id];
              return (
                <button
                  type="button"
                  className="store-nav__item"
                  aria-current={active === item.id ? 'page' : undefined}
                  aria-label={compact ? t(item.labelKey) : undefined}
                  title={compact ? t(item.labelKey) : undefined}
                  onClick={() => onSelect(item.id)}
                  key={item.id}
                >
                  <Icon aria-hidden="true" />
                  {!compact && <span>{t(item.labelKey)}</span>}
                </button>
              );
            })}
          </section>
        );
      })}
      <div className="store-nav__local"><span className="store-nav__local-dot" />{!compact && t('localFirst')}</div>
    </div>
  );
}

export function StoreShell(props: Props) {
  const { t } = useTranslation();
  const [compact, setCompact] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    requestAnimationFrame(() => document.getElementById('store-main')?.focus({ preventScroll: true }));
  }, [props.destination]);

  useEffect(() => {
    if (!userMenuOpen) return;
    const close = (event: MouseEvent) => {
      if (!userMenuRef.current?.contains(event.target as Node)) setUserMenuOpen(false);
    };
    const escape = (event: KeyboardEvent) => event.key === 'Escape' && setUserMenuOpen(false);
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', escape);
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', escape); };
  }, [userMenuOpen]);

  const choose = (destination: AppDestination) => {
    props.onNavigate(destination);
    setDrawerOpen(false);
  };
  const navigation = <Navigation items={props.items} active={props.destination} compact={compact} onSelect={choose} onToggleCompact={() => setCompact((value) => !value)} />;

  const header = (
    <ShellHeader className="store-shell__header">
      <IconButton className="store-shell__menu" icon={Menu} label={t('openNavigation')} onClick={() => setDrawerOpen(true)} />
      {props.canGoBack && <IconButton icon={ChevronLeft} label={t('back')} onClick={props.onBack} />}
      <div className="store-shell__title"><h1>{props.pageTitle}</h1></div>
      <div className="store-shell__status">
        {!props.online && <Badge variant="neutral"><WifiOff size={16} />{t('localOperationsAvailable')}</Badge>}
      </div>
      <IconButton icon={MessageSquare} label={t('openChat')} onClick={props.onOpenChat} aria-expanded={props.chatOpen} />
      <div className="store-shell__user" ref={userMenuRef}>
        <button type="button" className="store-shell__avatar" aria-expanded={userMenuOpen} aria-haspopup="menu" onClick={() => setUserMenuOpen((open) => !open)}>
          <span>{props.user.initials}</span><span className="store-shell__user-label">{props.user.first_name || props.user.firstName || props.user.username}</span>
        </button>
        {userMenuOpen && (
          <div className="store-shell__user-menu" role="menu">
            <div className="store-shell__identity"><strong>{props.user.username}</strong><span>{t(props.user.role)}</span></div>
            <button role="menuitem" onClick={() => { props.onSwitchUser(); setUserMenuOpen(false); }}><UserRoundCog />{t('switchUser')}</button>
            <button role="menuitem" onClick={() => props.setLanguage(props.language.startsWith('fr') ? 'en' : 'fr')}><MonitorCog />{t('language')}: {props.language.toUpperCase()}</button>
            <button role="menuitem" onClick={() => props.setTheme(props.theme === 'light' ? 'dark' : 'light')}><SunMoon />{t('theme')}</button>
            <button role="menuitem" onClick={() => { choose('help'); setUserMenuOpen(false); }}><CircleHelp />{t('navHelp')}</button>
            <button role="menuitem" className="store-shell__logout" onClick={props.onLogout}><LogOut />{t('logout')}</button>
          </div>
        )}
      </div>
    </ShellHeader>
  );

  return (
    <>
      <a className="store-skip-link" href="#store-main">{t('skipToMain')}</a>
      <AppShell className={compact ? 'store-shell store-shell--compact' : 'store-shell'} header={header} sidebar={
        <ShellSidebar label={t('mainNavigation')} className="store-shell__sidebar">
          {navigation}
        </ShellSidebar>
      }>
        <div id="store-main" className="store-shell__main" tabIndex={-1}>{props.children}</div>
      </AppShell>
      {drawerOpen && <Drawer side="left" title={t('mainNavigation')} closeLabel={t('close')} onClose={() => setDrawerOpen(false)}><Navigation items={props.items} active={props.destination} compact={false} onSelect={choose} /></Drawer>}
      {props.chatOpen && <Drawer className="quick-chat-drawer" side="right" title={t('chat')} description={t('quickChatHint')} closeLabel={t('close')} onClose={props.onCloseChat}>{props.chatPanel}</Drawer>}
    </>
  );
}
