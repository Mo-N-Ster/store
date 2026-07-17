import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { User } from '../../../types';
import { employeeService } from '../../../services/employeeService';
import { PasswordResetDialog } from './PasswordResetDialog';
import { CriticalDialog } from '../../../components/UI/CriticalDialog';
export function EmployeeList({ notify }: { notify: (x: string) => void }) {
  const { t } = useTranslation();
  const [rows, setRows] = useState<User[]>([]);
  const [edit, setEdit] = useState<any>(null);
  const [resetTarget, setResetTarget] = useState<User | null>(null);
  const [criticalMessage, setCriticalMessage] = useState('');
  const [search, setSearch] = useState('');
  const [createdEmployeePassword, setCreatedEmployeePassword] = useState<{
    user: User;
    password: string;
  } | null>(null);
  const load = () => employeeService.list().then(setRows);
  useEffect(() => {
    void load();
  }, []);
  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = Object.fromEntries(new FormData(event.currentTarget));
    try {
      const result = await employeeService.save({ ...edit, ...form, active: true });
      setEdit(null);
      await load();
      if (form.role === 'employee' && result.temporaryPassword)
        setCreatedEmployeePassword({ user: result.user, password: result.temporaryPassword });
      else
        notify(
          result.temporaryPassword
            ? t('temporaryPasswordMessage', { password: result.temporaryPassword })
            : t('employeeSaved'),
        );
    } catch (error: any) {
      setCriticalMessage(
        error.message?.includes('DUPLICATE_USER') ? t('userAlreadyExists') : t('operationFailed'),
      );
    }
  };
  const toggleActive = async (member: User) => {
    await employeeService.save({
      ...member,
      firstName: member.firstName || member.first_name,
      lastName: member.lastName || member.last_name,
      active: !member.active,
    });
    await load();
    notify(member.active ? t('employeeDisabled') : t('employeeEnabled'));
  };
  return (
    <>
      <div className="titlebar">
        <div>
          <span className="eyebrow">{t('yourTeam')}</span>
          <h1>{t('employees')}</h1>
        </div>
        <button onClick={() => setEdit({ role: 'employee' })}>+ {t('add')}</button>
      </div>
      <div className="filters">
        <input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder={t('searchUsers')}
        />
      </div>
      <div className="table-shell">
        <table>
          <tbody>
            {rows
              .filter((member) =>
                [
                  member.firstName,
                  member.lastName,
                  member.first_name,
                  member.last_name,
                  member.username,
                  member.email,
                  member.role,
                ]
                  .filter(Boolean)
                  .join(' ')
                  .toLowerCase()
                  .includes(search.trim().toLowerCase()),
              )
              .map((member) => (
                <tr key={member.id}>
                  <td>
                    <span className="avatar">{member.initials}</span>
                  </td>
                  <td>
                    <b>
                      {member.firstName} {member.lastName}
                    </b>
                    <small>{member.email}</small>
                  </td>
                  <td>
                    <span className="pill">{t(member.role)}</span>
                  </td>
                  <td>{member.active ? t('active') : t('inactive')}</td>
                  <td>
                    <button onClick={() => setEdit(member)}>✎</button>
                    <button className="ghost" onClick={() => toggleActive(member)}>
                      {member.active ? t('disable') : t('enable')}
                    </button>
                    <button className="ghost" onClick={() => setResetTarget(member)}>
                      {t('newPassword')}
                    </button>
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>
      {edit && (
        <div className="modal">
          <form className="form-modal" onSubmit={save}>
            <h2>{t('employees')}</h2>
            {[
              ['firstName', t('firstName')],
              ['lastName', t('lastName')],
              ['username', t('usernameOnly')],
              ['email', t('email')],
              ['phone', t('phone')],
              ['hireDate', t('hireDate')],
            ].map(([name, label]) => (
              <label key={name}>
                {label}
                <input
                  name={name}
                  type={name === 'email' ? 'email' : name === 'hireDate' ? 'date' : 'text'}
                  defaultValue={edit[name] ?? ''}
                  required={
                    ['firstName', 'lastName', 'username'].includes(name) ||
                    (name === 'email' && edit.role !== 'employee')
                  }
                />
              </label>
            ))}
            <label>
              {t('role')}
              {edit.role === 'owner' ? (
                <>
                  <input value={t('owner')} readOnly />
                  <input name="role" value="owner" type="hidden" />
                </>
              ) : (
                <select
                  name="role"
                  value={edit.role || 'employee'}
                  onChange={(event) => setEdit({ ...edit, role: event.target.value })}
                >
                  <option value="employee">{t('employee')}</option>
                  <option value="manager">{t('manager')}</option>
                </select>
              )}
            </label>
            {edit.role !== 'employee' && (
              <>
                <label>
                  {t('securityQuestion')}
                  <input
                    name="securityQuestion"
                    defaultValue={edit.securityQuestion || ''}
                    required
                  />
                </label>
                <label>
                  {t('securityAnswer')}
                  <input name="securityAnswer" required />
                </label>
              </>
            )}
            <button>{t('save')}</button>
            <button type="button" className="ghost" onClick={() => setEdit(null)}>
              {t('close')}
            </button>
          </form>
        </div>
      )}
      {resetTarget && (
        <PasswordResetDialog
          user={resetTarget}
          notify={notify}
          onClose={() => setResetTarget(null)}
        />
      )}
      {criticalMessage && (
        <CriticalDialog message={criticalMessage} onClose={() => setCriticalMessage('')} />
      )}
      {createdEmployeePassword && (
        <PasswordResetDialog
          user={createdEmployeePassword.user}
          temporaryPassword={createdEmployeePassword.password}
          notify={notify}
          onClose={() => setCreatedEmployeePassword(null)}
        />
      )}
    </>
  );
}
