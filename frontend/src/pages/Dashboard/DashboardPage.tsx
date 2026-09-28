import type { User } from '../../types';
import type { DashboardSection } from '../../navigation/navigation';
import { Overview } from './widgets/Overview';
import { ProductList } from './products/ProductList';
import { EmployeeList } from './employees/EmployeeList';
import { SalesHistory } from './sales/SalesHistory';
import { ChartsPage } from './charts/ChartsPage';
import { MailboxPage } from './mailbox/MailboxPage';
import { SettingsPage } from './settings/SettingsPage';
import { HelpPage } from './help/HelpPage';
import type { EffectivePermission } from '../../security/permissions';
export function DashboardPage({
  user,
  notify,
  section,
  onSectionChange,
  permissions,
  onOpenPresence,
  onOpenTeam,
}: {
  user: User;
  notify: (x: string) => void;
  section: DashboardSection;
  onSectionChange: (section: DashboardSection) => void;
  permissions: EffectivePermission[];
  onOpenPresence: (employeeId?: number) => void;
  onOpenTeam: () => void;
}) {
  return (
      <section className="workspace store-shell__legacy-page">
        {section === 'home' ? (
          <Overview onNavigate={onSectionChange} permissions={permissions} onOpenPresence={onOpenPresence} />
        ) : section === 'products' ? (
          <ProductList notify={notify} userId={user.id} permissions={permissions} />
        ) : section === 'employees' ? (
          <EmployeeList notify={notify} permissions={permissions} />
        ) : section === 'sales' ? (
          <SalesHistory userId={user.id} />
        ) : section === 'charts' ? (
          <ChartsPage notify={notify} permissions={permissions} />
        ) : section === 'mailbox' ? (
          <MailboxPage user={user} notify={notify} variant="management" />
        ) : section === 'settings' ? (
          <SettingsPage user={user} notify={notify} permissions={permissions} onOpenTeam={onOpenTeam} />
        ) : (
          <HelpPage />
        )}
      </section>
  );
}
