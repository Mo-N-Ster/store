import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { User } from '../../../types';
import { storeApi } from '../../../services/api';
export function EmployeeList({ notify }: { notify: (x: string) => void }) {
  const { t } = useTranslation();
  const [rows, setRows] = useState<User[]>([]);
  const [edit, setEdit] = useState<any>(null);
  const load = () => storeApi.users().then(setRows);
  useEffect(() => {
    void load();
  }, []);
  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = Object.fromEntries(new FormData(event.currentTarget));
    const result = await storeApi.saveUser({ ...edit, ...form, active: true });
    setEdit(null);
    await load();
    notify(
      result.temporaryPassword
        ? `Mot de passe temporaire: ${result.temporaryPassword}`
        : 'Employé enregistré',
    );
  };
  return (
    <>
      <div className="titlebar">
        <div>
          <span className="eyebrow">Votre équipe</span>
          <h1>{t('employees')}</h1>
        </div>
        <button onClick={() => setEdit({ role: 'employee' })}>+ {t('add')}</button>
      </div>
      <div className="table-shell">
        <table>
          <tbody>
            {rows.map((member) => (
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
                <td>{member.active ? 'Actif' : 'Inactif'}</td>
                <td>
                  <button onClick={() => setEdit(member)}>✎</button>
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
              ['username', t('username')],
              ['email', t('email')],
              ['phone', 'Téléphone'],
              ['hireDate', 'Date d’embauche'],
            ].map(([name, label]) => (
              <label key={name}>
                {label}
                <input
                  name={name}
                  type={name === 'email' ? 'email' : name === 'hireDate' ? 'date' : 'text'}
                  defaultValue={edit[name] ?? ''}
                  required={['firstName', 'lastName', 'username', 'email'].includes(name)}
                />
              </label>
            ))}
            <label>
              {t('role')}
              <select name="role" defaultValue={edit.role}>
                <option value="employee">{t('employee')}</option>
                <option value="admin">{t('admin')}</option>
              </select>
            </label>
            <button>{t('save')}</button>
            <button type="button" className="ghost" onClick={() => setEdit(null)}>
              {t('close')}
            </button>
          </form>
        </div>
      )}
    </>
  );
}
