import { useState } from 'react';
import type { User } from '../../types';
import { Sidebar, type DashboardSection } from '../../components/Layout/Sidebar';
import { Overview } from './widgets/Overview';
import { ProductList } from './products/ProductList';
import { EmployeeList } from './employees/EmployeeList';
import { SalesHistory } from './sales/SalesHistory';
import { ChartsPage } from './charts/ChartsPage';
import { MailboxPage } from './mailbox/MailboxPage';
import { SettingsPage } from './settings/SettingsPage';
export function DashboardPage({ user, notify }: { user: User; notify: (x: string) => void }) {
  const [section, setSection] = useState<DashboardSection>('home');
  return (
    <main className="dashboard">
      <Sidebar active={section} onChange={setSection} />
      <section className="workspace">
        {section === 'home' ? (
          <Overview />
        ) : section === 'products' ? (
          <ProductList notify={notify} userId={user.id} />
        ) : section === 'employees' ? (
          <EmployeeList notify={notify} />
        ) : section === 'sales' ? (
          <SalesHistory userId={user.id} />
        ) : section === 'charts' ? (
          <ChartsPage notify={notify} />
        ) : section === 'mailbox' ? (
          <MailboxPage user={user} notify={notify} />
        ) : (
          <SettingsPage user={user} notify={notify} />
        )}
      </section>
    </main>
  );
}
