import { KeyRound, Pencil, Plus, Power } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, EmptyState, IconButton, Modal, Select, TextInput } from '../../../design-system';
import { employeeService } from '../../../services/employeeService';
import type { User } from '../../../types';
import { can, type EffectivePermission } from '../../../security/permissions';
import { PasswordResetDialog } from './PasswordResetDialog';
import { UserDetailsDialog } from './UserDetailsDialog';

export function EmployeeList({
  notify,
  mode = 'employees',
  permissions,
}: {
  notify: (value: string) => void;
  mode?: 'employees' | 'accounts';
  permissions: EffectivePermission[];
}) {
  const { t } = useTranslation();
  const [rows, setRows] = useState<User[]>([]);
  const [edit, setEdit] = useState<any>(null);
  const [resetTarget, setResetTarget] = useState<User | null>(null);
  const [detail, setDetail] = useState<User | null>(null);
  const [search, setSearch] = useState('');
  const [role, setRole] = useState('');
  const [status, setStatus] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [createdPassword, setCreatedPassword] = useState<{ user: User; password: string } | null>(
    null,
  );
  const saveLock = useRef(false);
  const [photoLoading, setPhotoLoading] = useState(false);
  const choosePhoto = async (file?: File) => {
    if (!file) return;
    setPhotoLoading(true);
    const url = URL.createObjectURL(file);
    try {
      if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024) throw new Error();
      const image = new Image();
      image.src = url;
      await image.decode();
      const scale = Math.min(1, 256 / Math.max(image.width, image.height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.round(image.width * scale));
      canvas.height = Math.max(1, Math.round(image.height * scale));
      const context = canvas.getContext('2d');
      if (!context) throw new Error();
      context.drawImage(image, 0, 0, canvas.width, canvas.height);
      setEdit((current: any) => current ? { ...current, photo: canvas.toDataURL('image/png') } : current);
    } catch { setError(t('employeePhotoError')); }
    finally { URL.revokeObjectURL(url); setPhotoLoading(false); }
  };
  const mayUpdate = can(permissions, 'EMPLOYEES', 'UPDATE');
  const mayManageRoles = can(permissions, 'ADMINISTRATION', 'READ');
  const load = () => employeeService.list().then(setRows);
  useEffect(() => {
    void load();
  }, []);
  const filtered = rows.filter(
    (member) =>
      (!role || member.role === role) &&
      (!status || Boolean(member.active) === (status === 'active')) &&
      [
        member.firstName,
        member.lastName,
        member.first_name,
        member.last_name,
        member.username,
        member.email,
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
        .includes(search.trim().toLowerCase()),
  );
  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (saveLock.current || photoLoading) return;
    saveLock.current = true;
    setSaving(true);
    setError('');
    const form = Object.fromEntries(new FormData(event.currentTarget));
    try {
      const result = await employeeService.save({
        ...edit,
        ...form,
        active: edit.id ? Boolean(edit.active) : true,
      });
      setEdit(null);
      await load();
      if (form.role === 'employee' && result.temporaryPassword)
        setCreatedPassword({ user: result.user, password: result.temporaryPassword });
      else notify(t('employeeSaved'));
    } catch (caught: any) {
      setError(
        caught.message?.includes('DUPLICATE_USER') ? t('userAlreadyExists') : t('operationFailed'),
      );
    } finally {
      saveLock.current = false;
      setSaving(false);
    }
  };
  const toggleActive = async (member: User) => {
    if (saveLock.current) return;
    saveLock.current = true;
    try {
      await employeeService.save({
        ...member,
        firstName: member.firstName || member.first_name,
        lastName: member.lastName || member.last_name,
        active: !member.active,
      });
      await load();
      notify(member.active ? t('employeeDisabled') : t('employeeEnabled'));
    } finally {
      saveLock.current = false;
    }
  };
  return (
    <div className="ops-domain">
      <div className="ops-page__subactions">
        {mode === 'employees' && mayUpdate && (
          <Button icon={Plus} onClick={() => setEdit({ role: 'employee' })}>
            {t('newEmployee')}
          </Button>
        )}
      </div>
      <div className="ops-filters">
        <TextInput
          type="search"
          label={t('searchUsers')}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <Select label={t('role')} value={role} onChange={(event) => setRole(event.target.value)}>
          <option value="">{t('allRoles')}</option>
          <option value="employee">{t('employee')}</option>
          <option value="manager">{t('manager')}</option>
          <option value="owner">{t('owner')}</option>
        </Select>
        <Select
          label={t('status')}
          value={status}
          onChange={(event) => setStatus(event.target.value)}
        >
          <option value="">{t('allStatuses')}</option>
          <option value="active">{t('active')}</option>
          <option value="inactive">{t('inactive')}</option>
        </Select>
      </div>
      {!filtered.length ? (
        <EmptyState title={t('noTeamMembers')} />
      ) : (
        <div className="ops-list">
          {filtered.map((member) => {
            const fullName =
              `${member.firstName || member.first_name || ''} ${member.lastName || member.last_name || ''}`.trim();
            return (
              <article
                className="ops-list-item"
                key={member.id}
                tabIndex={0}
                onClick={() => setDetail(member)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    setDetail(member);
                  }
                }}
              >
                <div className="ops-list-item__main">
                  <strong>{fullName || member.username}</strong>
                  <span>
                    {mode === 'accounts'
                      ? `${member.username} · ${member.email || '—'}`
                      : member.phone || member.email || '—'}
                  </span>
                </div>
                <span>{t(member.role)}</span>
                <span className={`ops-status ops-status--${member.active ? 'active' : 'inactive'}`}>
                  {t(member.active ? 'active' : 'inactive')}
                </span>
                <div className="ops-list-item__actions">
                  {mayUpdate && (
                    <>
                      <IconButton icon={Pencil} label={t('editEmployee', { name: fullName })} onClick={(event) => { event.stopPropagation(); setEdit(member); }} />
                      <IconButton icon={Power} label={member.active ? t('disable') : t('enable')} onClick={(event) => { event.stopPropagation(); void toggleActive(member); }} />
                      <IconButton icon={KeyRound} label={t('newPassword')} onClick={(event) => { event.stopPropagation(); setResetTarget(member); }} />
                    </>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}
      {edit && (
        <Modal
          title={edit.id ? t('editEmployeeTitle') : t('newEmployee')}
          description={t('employeeFormHint')}
          closeLabel={t('close')}
          onClose={() => !saving && setEdit(null)}
          dismissible={!saving && !photoLoading}
        >
          <form className="ops-form" onSubmit={save}>
            <fieldset>
              <legend>{t('employeeProfile')}</legend>
              <label className="employee-photo-field">{t('employeePhoto')}
                {edit.photo && <img className="employee-avatar" src={edit.photo} alt="" />}
                <input type="file" accept="image/jpeg,image/png,image/webp" disabled={saving || photoLoading} onChange={(event) => { void choosePhoto(event.target.files?.[0]); event.target.value = ''; }} />
                <small>{t('employeePhotoHint')}</small>
              </label>
              {edit.photo && <Button type="button" variant="ghost" disabled={photoLoading} onClick={() => setEdit({ ...edit, photo: null })}>{t('removeArticleImage')}</Button>}
              <TextInput
                name="firstName"
                label={t('firstName')}
                defaultValue={edit.firstName || edit.first_name || ''}
                required
              />
              <TextInput
                name="lastName"
                label={t('lastName')}
                defaultValue={edit.lastName || edit.last_name || ''}
                required
              />
              <TextInput name="phone" label={t('phone')} defaultValue={edit.phone || ''} />
              <TextInput
                name="hireDate"
                type="date"
                label={t('hireDate')}
                defaultValue={edit.hireDate || ''}
              />
            </fieldset>
            <fieldset>
              <legend>{t('accountAndAccess')}</legend>
              <TextInput
                name="username"
                label={t('usernameOnly')}
                defaultValue={edit.username || ''}
                required
              />
              <TextInput
                name="email"
                type="email"
                label={t('email')}
                defaultValue={edit.email || ''}
                required={edit.role !== 'employee'}
              />
              {edit.role === 'owner' ? (
                <>
                  <TextInput label={t('role')} value={t('owner')} readOnly />
                  <input name="role" value="owner" type="hidden" />
                </>
              ) : (
                <Select
                  name="role"
                  label={t('role')}
                  value={edit.role || 'employee'}
                  onChange={(event) => setEdit({ ...edit, role: event.target.value })}
                >
                  <option value="employee">{t('employee')}</option>
                  {mayManageRoles && <option value="manager">{t('manager')}</option>}
                </Select>
              )}
              {edit.role !== 'employee' && (
                <>
                  <TextInput
                    name="securityQuestion"
                    label={t('securityQuestion')}
                    defaultValue={edit.securityQuestion || ''}
                    required
                  />
                  <TextInput name="securityAnswer" label={t('securityAnswer')} required />
                </>
              )}
            </fieldset>
            {error && (
              <p className="ds-field__error" role="alert">
                {error}
              </p>
            )}
            <div className="ops-form__actions">
              <Button type="button" variant="secondary" onClick={() => setEdit(null)}>
                {t('cancel')}
              </Button>
              <Button type="submit" loading={saving} loadingLabel={t('saving')}>
                {t('save')}
              </Button>
            </div>
          </form>
        </Modal>
      )}
      {detail && <UserDetailsDialog user={detail} onClose={() => setDetail(null)} />}
      {resetTarget && (
        <PasswordResetDialog
          user={resetTarget}
          notify={notify}
          onClose={() => setResetTarget(null)}
        />
      )}
      {createdPassword && (
        <PasswordResetDialog
          user={createdPassword.user}
          temporaryPassword={createdPassword.password}
          notify={notify}
          onClose={() => setCreatedPassword(null)}
        />
      )}
    </div>
  );
}
