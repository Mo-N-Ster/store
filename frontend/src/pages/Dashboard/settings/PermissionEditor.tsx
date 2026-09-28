import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Checkbox, Select } from '../../../design-system';
import { storeApi } from '../../../services/api';

type Snapshot = { user: { id: number; username: string; role: string }; inherited: string[]; denied: string[]; effective: string[] };
export function PermissionEditor() {
  const { t } = useTranslation();
  const [users, setUsers] = useState<Snapshot['user'][]>([]);
  const [target, setTarget] = useState('');
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [denied, setDenied] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  useEffect(() => { let active = true; void storeApi.users().then((rows) => { if (active) setUsers(rows.filter((row: Snapshot['user']) => row.role !== 'owner')); }).catch(() => { if (active) setMessage(t('operationFailed')); }); return () => { active = false; }; }, [t]);
  useEffect(() => {
    let active = true; setSnapshot(null); setMessage('');
    if (target) { setBusy(true); void storeApi.userPermissions({ userId: Number(target) }).then((value: Snapshot) => { if (active) { setSnapshot(value); setDenied(value.denied); } }).catch(() => { if (active) setMessage(t('operationFailed')); }).finally(() => { if (active) setBusy(false); }); }
    return () => { active = false; };
  }, [target, t]);
  const changed = snapshot && JSON.stringify([...denied].sort()) !== JSON.stringify(snapshot.denied);
  return <section className="ops-form"><h2>{t('customPermissionEditor')}</h2><p>{t('permissionDenialHint')}</p>
    <Select label={t('employee')} value={target} disabled={busy} onChange={(event) => { if (!changed || window.confirm(t('discardPermissionChanges'))) setTarget(event.target.value); }}><option value="">{t('selectPermissionUser')}</option>{users.map((user) => <option key={user.id} value={user.id}>{user.username} · {t(user.role)}</option>)}</Select>
    {snapshot && <><h3>{snapshot.user.username} · {t(snapshot.user.role)}</h3>
      {[...new Set(snapshot.inherited.map((code) => code.split(':')[0]))].map((module) => <fieldset key={module}><legend>{t(`module_${module.toLowerCase()}`, { defaultValue: module })}</legend>
        {snapshot.inherited.filter((code) => code.startsWith(`${module}:`)).map((code) => <Checkbox key={code} disabled={busy} checked={!denied.includes(code)} label={`${t(`permission_${code.split(':')[1].toLowerCase()}`)} — ${t(denied.includes(code) ? 'permissionExplicitDenied' : 'permissionInheritedAllowed')}`} onChange={(event) => setDenied(event.target.checked ? denied.filter((item) => item !== code) : [...denied, code])} />)}
      </fieldset>)}
      <div className="ops-form__actions"><Button disabled={busy || !changed} variant="secondary" onClick={() => { setDenied(snapshot.denied); setMessage(''); }}>{t('cancel')}</Button>
        <Button loading={busy} disabled={!changed} onClick={async () => { if (!window.confirm(t('confirmPermissionRestrictions'))) return; setBusy(true); setMessage(''); try { const result = await storeApi.saveUserPermissions({ userId: snapshot.user.id, denied, expectedDenied: snapshot.denied, expectedRole: snapshot.user.role }); setSnapshot(result); setDenied(result.denied); setMessage(t('operationSuccessful')); window.dispatchEvent(new Event('store:permissions-updated')); } catch (error: any) { setMessage(t(error.message?.includes('CASH_SESSION_OPEN') ? 'permissionCashBlocked' : error.message?.includes('CONFLICT') ? 'permissionConflict' : 'operationFailed')); } finally { setBusy(false); } }}>{t('save')}</Button></div>
    </>}{message && <p role="status">{message}</p>}</section>;
}
