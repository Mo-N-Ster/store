import { useTranslation } from 'react-i18next';
export type DashboardSection =
  'home' | 'products' | 'employees' | 'sales' | 'charts' | 'mailbox' | 'settings';
const items: [DashboardSection, string, string?][] = [
  ['home', '⌂'],
  ['products', '▣', 'stocks'],
  ['employees', '●', 'users'],
  ['sales', '€', 'histories'],
  ['charts', '↗'],
  ['mailbox', '✉'],
  ['settings', '⚙'],
];
export function Sidebar({
  active,
  onChange,
}: {
  active: DashboardSection;
  onChange: (value: DashboardSection) => void;
}) {
  const { t } = useTranslation();
  return (
    <nav>
      <div className="sidebar-brand">
        <span>S</span>
        <b>STORE</b>
      </div>
      {items.map(([key, icon, label]) => (
        <button className={active === key ? 'active' : ''} onClick={() => onChange(key)} key={key}>
          <span>{icon}</span>
          {t(label || key)}
        </button>
      ))}
    </nav>
  );
}
