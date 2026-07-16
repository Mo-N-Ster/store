import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { User } from '../../types';
import { attendanceService } from '../../services/attendanceService';
import { employeeService } from '../../services/employeeService';

export function EmployeePresence({ notify }: { notify: (message: string) => void }) {
  const { t } = useTranslation();
  const [employees, setEmployees] = useState<User[]>([]);
  const [statuses, setStatuses] = useState<Record<number, boolean>>({});
  const [selected, setSelected] = useState<User | null>(null);
  const [busy, setBusy] = useState(false);
  const load = async () => {
    const [users, rows] = await Promise.all([employeeService.list(), attendanceService.statuses()]);
    setEmployees(users);
    setStatuses(Object.fromEntries(rows.map((row: any) => [row.id, Boolean(row.present)])));
  };
  useEffect(() => {
    void load();
  }, []);
  const change = async (present: boolean) => {
    if (!selected) return;
    setBusy(true);
    try {
      await attendanceService.toggle(selected.id, present);
      setStatuses((current) => ({ ...current, [selected.id]: present }));
      notify(present ? t('serviceStarted') : t('serviceEnded'));
      setSelected(null);
    } catch {
      notify(t('operationFailed'));
    } finally {
      setBusy(false);
    }
  };
  const selectedPresent = selected ? Boolean(statuses[selected.id]) : false;
  return (
    <div className="presence" aria-label={t('teamPresence')}>
      <div className="avatars">
        {employees
          .filter((user) => user.active)
          .map((user) => (
            <button
              key={user.id}
              className={statuses[user.id] ? 'present' : 'absent'}
              title={`${user.username} — ${statuses[user.id] ? t('present') : t('absent')}`}
              onClick={() => setSelected(user)}
            >
              {user.initials}
            </button>
          ))}
      </div>
      {selected && (
        <div className="presence-popover">
          <b>{selected.username}</b>
          <span>
            {selected.firstName || selected.first_name} {selected.lastName || selected.last_name}
          </span>
          <div>
            <button disabled={busy || selectedPresent} onClick={() => change(true)}>
              {t('present')}
            </button>
            <button
              disabled={busy || !selectedPresent}
              className="ghost"
              onClick={() => change(false)}
            >
              {t('absent')}
            </button>
          </div>
          <button className="popover-close" onClick={() => setSelected(null)}>
            ×
          </button>
        </div>
      )}
    </div>
  );
}
