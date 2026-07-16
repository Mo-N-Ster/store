import { useState } from 'react';
import type { User } from '../../types';
import { attendanceService } from '../../services/attendanceService';

export function EmployeePresence({
  employees,
  notify,
}: {
  employees: User[];
  notify: (message: string) => void;
}) {
  const [selected, setSelected] = useState<User | null>(null);
  const change = async (present: boolean) => {
    if (!selected) return;
    try {
      await attendanceService.toggle(selected.id, present);
      notify(present ? 'Service démarré.' : 'Service terminé.');
      setSelected(null);
    } catch (error: any) {
      notify(error.message);
    }
  };
  return (
    <div className="presence">
      <div className="avatars">
        {employees
          .filter((user) => user.active)
          .map((user) => (
            <button key={user.id} title={user.username} onClick={() => setSelected(user)}>
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
            <button onClick={() => change(true)}>Présent</button>
            <button className="ghost" onClick={() => change(false)}>
              Absent
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
