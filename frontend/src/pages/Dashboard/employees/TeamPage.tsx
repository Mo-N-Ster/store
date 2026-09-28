import { Clock3, Users } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Tabs } from '../../../design-system';
import { EmployeeList } from './EmployeeList';
import { PresencePage } from './PresencePage';
import { can, type EffectivePermission } from '../../../security/permissions';

export function TeamPage({
  notify,
  permissions,
  initialEmployeeId,
}: {
  notify: (message: string) => void;
  permissions: EffectivePermission[];
  initialEmployeeId?: number;
}) {
  const { t } = useTranslation();
  const mayReadMembers = can(permissions, 'EMPLOYEES', 'READ');
  const [tab, setTab] = useState(initialEmployeeId || !mayReadMembers ? 'presence' : 'employees');
  return (
    <section className="ops-page" aria-labelledby="team-title">
      <header className="ops-page__header">
        <div>
          <span className="eyebrow">{t('dailyOperations')}</span>
          <h1 id="team-title">{t('navTeam')}</h1>
          <p>{t('teamPageHint')}</p>
        </div>
      </header>
      <Tabs
        ariaLabel={t('teamSections')}
        active={tab}
        onChange={setTab}
        tabs={[
          ...(mayReadMembers ? [{
            id: 'employees',
            label: (
              <>
                <Users size={16} /> {t('employees')}
              </>
            ),
          panel: <EmployeeList notify={notify} permissions={permissions} />,
          }] : []),
          ...(can(permissions, 'PRESENCE', 'READ') ? [{
            id: 'presence',
            label: (
              <>
                <Clock3 size={16} /> {t('presenceAndTime')}
              </>
            ),
            panel: (
              <PresencePage notify={notify} permissions={permissions} initialEmployeeId={initialEmployeeId} />
            ),
          }] : []),
        ]}
      />
    </section>
  );
}
