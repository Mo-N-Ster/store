import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { User } from '../../types';
import { attendanceService } from '../../services/attendanceService';
import { employeeService } from '../../services/employeeService';

export function EmployeePresence() {
  const { t } = useTranslation();
  const [employees, setEmployees] = useState<User[]>([]);
  const [statuses, setStatuses] = useState<Record<number, boolean>>({});
  const [selected, setSelected] = useState<User | null>(null);
  const presenceRef = useRef<HTMLDivElement>(null);
  const load = async () => {
    const [users, rows] = await Promise.all([employeeService.list(), attendanceService.statuses()]);
    setEmployees(users);
    setStatuses(Object.fromEntries(rows.map((row: any) => [row.id, Boolean(row.present)])));
  };
  useEffect(() => {
    void load();
  }, []);
  useEffect(() => {
    if (!selected) return;
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (!presenceRef.current?.contains(event.target as Node)) setSelected(null);
    };
    document.addEventListener('mousedown', closeOnOutsideClick);
    return () => document.removeEventListener('mousedown', closeOnOutsideClick);
  }, [selected]);
  return (
    <div ref={presenceRef} className="presence" aria-label={t('teamPresence')}>
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
          <span>{statuses[selected.id] ? t('present') : t('absent')}</span>
          <button className="popover-close" onClick={() => setSelected(null)}>
            ×
          </button>
        </div>
      )}
    </div>
  );
}
