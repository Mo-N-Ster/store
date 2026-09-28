import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Archive,
  Database,
  FileClock,
  KeyRound,
  Settings,
  ShieldCheck,
  TriangleAlert,
  Users,
} from 'lucide-react';
import {
  Button,
  Checkbox,
  ConfirmDialog,
  EmptyState,
  Select,
  TextInput,
} from '../../../design-system';
import {
  can,
  permissionActions,
  type EffectivePermission,
  type PermissionAction,
} from '../../../security/permissions';
import { settingsService } from '../../../services/settingsService';
import { selectFile } from '../../../services/api';
import { SETTINGS_UPDATED_EVENT, SUPPORTED_CURRENCIES } from '../../../hooks/useStorePreferences';
import { PasswordInput } from '../../../components/UI/PasswordInput';
import type { User } from '../../../types';
import { PermissionEditor } from './PermissionEditor';
import { AuditPanel } from './AuditPanel';

type Section =
  'home' | 'users' | 'permissions' | 'settings' | 'backups' | 'diagnostics' | 'audit' | 'danger';
type BackupRow = { name: string; path: string; size: number; modifiedAt: string };
type Diagnostics = {
  appVersion: string;
  schemaVersion: number;
  databaseSize: number;
  availableDiskBytes: number | null;
  storageHealth: string;
  backupCount: number;
  pendingEmails: number;
  lastHeartbeat: string | null;
  integrity: string;
};
const sections: {
  id: Exclude<Section, 'home'>;
  icon: typeof Users;
  permission: [string, PermissionAction];
  group: string;
}[] = [
  { id: 'users', icon: Users, permission: ['EMPLOYEES', 'READ'], group: 'access' },
  { id: 'permissions', icon: KeyRound, permission: ['ADMINISTRATION', 'READ'], group: 'access' },
  { id: 'settings', icon: Settings, permission: ['SETTINGS', 'READ'], group: 'configuration' },
  { id: 'backups', icon: Archive, permission: ['BACKUPS', 'READ'], group: 'dataProtection' },
  { id: 'diagnostics', icon: Database, permission: ['ADMINISTRATION', 'READ'], group: 'system' },
  { id: 'audit', icon: FileClock, permission: ['ADMINISTRATION', 'READ'], group: 'system' },
  {
    id: 'danger',
    icon: TriangleAlert,
    permission: ['RESET', 'VALIDATE'],
    group: 'criticalActions',
  },
];

export function SettingsPage({
  user,
  notify,
  permissions,
  onOpenTeam,
}: {
  user: User;
  notify: (x: string) => void;
  permissions: EffectivePermission[];
  onOpenTeam: () => void;
}) {
  const { t } = useTranslation();
  const available = useMemo(
    () => sections.filter(({ permission }) => can(permissions, ...permission)),
    [permissions],
  );
  const [section, setSection] = useState<Section>('home');
  useEffect(() => {
    if (section !== 'home' && !available.some((item) => item.id === section)) setSection('home');
  }, [available, section]);
  return (
    <div className="admin-page">
      <header className="admin-heading">
        <div>
          <span className="eyebrow">{t('systemAdministration')}</span>
          <h1>{section === 'home' ? t('administration') : t(`admin_${section}`)}</h1>
        </div>
        <p>{t('adminIntro')}</p>
      </header>
      <div className="admin-layout">
        <nav className="admin-nav" aria-label={t('administrationSections')}>
          <button className={section === 'home' ? 'active' : ''} onClick={() => setSection('home')}>
            <ShieldCheck />
            {t('adminHome')}
          </button>
          {available.map(({ id, icon: Icon, group }, index) => (
            <div className="admin-nav__entry" key={id}>
              {(index === 0 || available[index - 1]?.group !== group) && (
                <span>{t(`adminGroup_${group}`)}</span>
              )}
              <button
                className={section === id ? 'active' : ''}
                aria-current={section === id ? 'page' : undefined}
                onClick={() => setSection(id)}
              >
                <Icon />
                {t(`admin_${id}`)}
              </button>
            </div>
          ))}
        </nav>
        <main className="admin-content">
          {section === 'home' && <AdminHome available={available} onOpen={setSection} />}
          {section === 'users' && (
            <Button onClick={onOpenTeam}>{t('navTeam')}</Button>
          )}
          {section === 'permissions' && (
            <PermissionsPanel permissions={permissions} />
          )}
          {section === 'settings' && <SettingsPanel notify={notify} permissions={permissions} />}
          {section === 'backups' && <BackupsPanel notify={notify} permissions={permissions} />}
          {section === 'diagnostics' && <DiagnosticsPanel notify={notify} />}
          {section === 'audit' && (
            <AuditPanel />
          )}
          {section === 'danger' && <DangerPanel user={user} notify={notify} />}
        </main>
      </div>
    </div>
  );
}

function AdminHome({
  available,
  onOpen,
}: {
  available: typeof sections;
  onOpen: (section: Section) => void;
}) {
  const { t } = useTranslation();
  return (
    <div className="admin-home">
      <div className="admin-callout">
        <ShieldCheck />
        <div>
          <h2>{t('administrationProtected')}</h2>
          <p>{t('administrationProtectedHint')}</p>
        </div>
      </div>
      <div className="admin-card-grid">
        {available.map(({ id, icon: Icon }) => (
          <button key={id} onClick={() => onOpen(id)}>
            <Icon />
            <span>
              <strong>{t(`admin_${id}`)}</strong>
              <small>{t(`admin_${id}_hint`)}</small>
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}

function PermissionsPanel({ permissions }: { permissions: EffectivePermission[] }) {
  const { t } = useTranslation();
  const [filter, setFilter] = useState('');
  const [onlyRestricted, setOnlyRestricted] = useState(false);
  const visible = permissions.filter((permission) =>
    t(`module_${permission.module.toLowerCase()}`, { defaultValue: permission.module }).toLowerCase().includes(filter.toLowerCase()) &&
    (!onlyRestricted || permissionActions.some((action) => !permission.actions.includes(action))));
  return (
    <section>
      <div className="admin-section-heading">
        <div>
          <h2>{t('effectivePermissions')}</h2>
          <p>{t('permissionsReadOnlyHint')}</p>
        </div>
        <span className="admin-status ok">{t('effectivePermissions')}</span>
      </div>
      <div className="permission-toolbar">
        <label>{t('permissionSearch')}<input type="search" value={filter} onChange={(event) => setFilter(event.target.value)} /></label>
        <button type="button" className="ghost" aria-pressed={onlyRestricted} onClick={() => setOnlyRestricted(!onlyRestricted)}>{t('permissionRestrictedFilter')}</button>
        <span role="status">{t('permissionSummary', { modules: visible.length, count: permissions.reduce((sum, item) => sum + item.actions.length, 0) })}</span>
      </div>
      <div className="permission-list">
        {visible.map((permission) => (
          <details className="permission-domain" key={permission.module}>
            <summary>{t(`module_${permission.module.toLowerCase()}`, { defaultValue: permission.module })}<span>{permission.actions.length}/{permissionActions.length}</span></summary>
            <div>
              {permissionActions.map((action) => (
                <span
                  key={action}
                  className={permission.actions.includes(action) ? 'allowed' : 'denied'}
                >
                  {t(`permission_${action.toLowerCase()}`)} ·{' '}
                  {permission.actions.includes(action) ? t('allowed') : t('notAllowed')}
                </span>
              ))}
            </div>
          </details>
        ))}
      </div>
      {!visible.length && <p role="status">{t('helpNoResults')}</p>}
      {can(permissions, 'ADMINISTRATION', 'UPDATE') && <PermissionEditor />}
    </section>
  );
}

function SettingsPanel({
  notify,
  permissions,
}: {
  notify: (message: string) => void;
  permissions: EffectivePermission[];
}) {
  const { t } = useTranslation();
  const [data, setData] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const maySave = can(permissions, 'SETTINGS', 'UPDATE');
  useEffect(() => {
    let active = true;
    settingsService
      .get()
      .then((value) => {
        if (active) setData(value as Record<string, string>);
      })
      .catch(() => notify(t('operationFailed')));
    return () => {
      active = false;
    };
  }, [notify, t]);
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);
  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!maySave || saving) return;
    setSaving(true);
    const form = new FormData(event.currentTarget);
    const values = Object.fromEntries(form) as Record<string, string>;
    values.discountsEnabled = form.has('discountsEnabled') ? 'true' : 'false';
    values.smtpSecure = form.has('smtpSecure') ? 'true' : 'false';
    try {
      await settingsService.save(values);
      setData((current) => ({ ...current, ...values }));
      setDirty(false);
      window.dispatchEvent(new Event(SETTINGS_UPDATED_EVENT));
      notify(t('settingsSaved'));
    } catch {
      notify(t('operationFailed'));
    } finally {
      setSaving(false);
    }
  };
  return (
    <form
      key={JSON.stringify(data)}
      className="admin-form"
      onSubmit={save}
      onChange={() => setDirty(true)}
    >
      <fieldset>
        <legend>{t('storeWideSettings')}</legend>
        <p>{t('storeWideSettingsHint')}</p>
        <div className="admin-form-grid">
          <TextInput
            name="storeName"
            label={t('storeName')}
            defaultValue={data.storeName || 'STORE'}
            required
          />
          <TextInput name="address" label={t('address')} defaultValue={data.address || ''} />
          <TextInput name="phone" label={t('phone')} defaultValue={data.phone || ''} />
          <TextInput name="email" type="email" label={t('email')} defaultValue={data.email || ''} />
          <Select
            name="currency"
            label={t('currency')}
            help={t('currencyHistoryWarning')}
            defaultValue={SUPPORTED_CURRENCIES.some((code) => code === data.currency) ? data.currency : 'EUR'}
          >
            {SUPPORTED_CURRENCIES.map((code) => (
              <option key={code} value={code}>
                {t(`currency${code}`)}
              </option>
            ))}
          </Select>
          <Checkbox
            name="discountsEnabled"
            value="true"
            label={t('enableDiscounts')}
            defaultChecked={data.discountsEnabled !== 'false'}
          />
        </div>
      </fieldset>
      <fieldset className="admin-sensitive">
        <legend>{t('emailConfiguration')}</legend>
        <p>{t('securitySensitiveSettingsHint')}</p>
        <div className="admin-form-grid">
          <TextInput name="smtpHost" label={t('smtpServer')} defaultValue={data.smtpHost || ''} />
          <TextInput
            name="smtpPort"
            type="number"
            min="1"
            max="65535"
            label={t('smtpPort')}
            defaultValue={data.smtpPort || '587'}
          />
          <TextInput name="smtpUser" label={t('smtpUser')} defaultValue={data.smtpUser || ''} />
          <label className="ds-field">
            <span className="ds-field__label">{t('smtpPassword')}</span>
            <PasswordInput
              name="smtpPassword"
              defaultValue=""
              autoComplete="new-password"
              placeholder={data.smtpPasswordConfigured === 'true' ? '••••••••' : t('smtpPassword')}
            />
          </label>
          <TextInput
            name="smtpFrom"
            type="email"
            label={t('sender')}
            defaultValue={data.smtpFrom || ''}
          />
          <Checkbox
            name="smtpSecure"
            value="true"
            label={t('smtpSecure')}
            defaultChecked={data.smtpSecure === 'true'}
          />
        </div>
        {can(permissions, 'SETTINGS', 'VALIDATE') && (
          <Button
            type="button"
            variant="secondary"
            onClick={() =>
              settingsService
                .testEmail()
                .then(() => notify(t('testEmailSent')))
                .catch(() => notify(t('smtpFailed')))
            }
          >
            {t('testSmtp')}
          </Button>
        )}
      </fieldset>
      {dirty && (
        <p className="admin-unsaved" role="status">
          {t('unsavedChanges')}
        </p>
      )}
      <Button type="submit" loading={saving} loadingLabel={t('saving')} disabled={!maySave}>
        {t('save')}
      </Button>
    </form>
  );
}

function BackupsPanel({
  notify,
  permissions,
}: {
  notify: (message: string) => void;
  permissions: EffectivePermission[];
}) {
  const { t } = useTranslation();
  const [rows, setRows] = useState<BackupRow[]>([]);
  const [busy, setBusy] = useState(false);
  const [restoreFile, setRestoreFile] = useState<string | null>(null);
  const lock = useRef(false);
  const load = () =>
    settingsService
      .backups()
      .then((value) => setRows(value as BackupRow[]))
      .catch(() => notify(t('backupFailed')));
  useEffect(() => {
    let active = true;
    settingsService
      .backups()
      .then((value) => {
        if (active) setRows(value as BackupRow[]);
      })
      .catch(() => notify(t('backupFailed')));
    return () => {
      active = false;
      setRows([]);
    };
  }, [notify, t]);
  const run = async (operation: () => Promise<unknown>) => {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    try {
      const result = await operation();
      if (result === null) return;
      notify(t('operationSuccessful'));
      await load();
    } catch {
      notify(t('backupFailed'));
    } finally {
      lock.current = false;
      setBusy(false);
    }
  };
  const chooseRestore = async () => {
    if (lock.current) return;
    const file = await selectFile([
      { name: t('storeBackupBundle'), extensions: ['store-backup'] },
      { name: t('legacySqliteBackup'), extensions: ['db', 'sqlite'] },
    ]);
    if (file) setRestoreFile(file);
  };
  const restore = () =>
    run(async () => {
      await settingsService.restore(restoreFile!);
      location.reload();
    });
  return (
    <section>
      <div className="admin-section-heading">
        <div>
          <h2>{t('backupProtection')}</h2>
          <p>{t('automaticBackupContract')}</p>
        </div>
        <span className="admin-status ok">{t('localOnly')}</span>
      </div>
      <div className="admin-actions">
        {can(permissions, 'BACKUPS', 'CREATE') && (
          <>
            <Button
              loading={busy}
              loadingLabel={t('working')}
              onClick={() => run(settingsService.backup)}
            >
              {t('createManualBackup')}
            </Button>
            <Button
              variant="secondary"
              disabled={busy}
              onClick={() => run(settingsService.exportBackup)}
            >
              {t('copyBackupExternal')}
            </Button>
          </>
        )}
        {can(permissions, 'RESTORE', 'VALIDATE') && (
          <Button variant="danger" disabled={busy} onClick={chooseRestore}>
            {t('restoreBackup')}
          </Button>
        )}
      </div>
      <div className="admin-callout admin-callout--warning">
        <TriangleAlert />
        <p>{t('restoreDebtWarning')}</p>
      </div>
      <div className="backup-table" role="table" aria-label={t('recentBackups')}>
        <div role="row" className="backup-table__head">
          <span role="columnheader">{t('backupFile')}</span>
          <span role="columnheader">{t('date')}</span>
          <span role="columnheader">{t('size')}</span>
          <span role="columnheader">{t('backupType')}</span>
        </div>
        {rows.slice(0, 25).map((row) => (
          <div role="row" key={row.path}>
            <strong role="cell">{row.name}</strong>
            <span role="cell">{new Date(row.modifiedAt).toLocaleString()}</span>
            <span role="cell">{formatBytes(row.size)}</span>
            <span role="cell">{backupKind(row.name, t)}</span>
          </div>
        ))}
      </div>
      {!rows.length && <EmptyState title={t('noBackups')} />}
      {restoreFile && (
        <ConfirmDialog
          title={t('confirmRestoreTitle')}
          description={t('confirmRestoreDetailed')}
          confirmLabel={t('restoreBackup')}
          cancelLabel={t('cancel')}
          danger
          onClose={() => setRestoreFile(null)}
          onConfirm={restore}
        />
      )}
    </section>
  );
}

function DiagnosticsPanel({ notify }: { notify: (message: string) => void }) {
  const { t } = useTranslation();
  const [data, setData] = useState<Diagnostics | null>(null);
  useEffect(() => {
    let active = true;
    settingsService
      .diagnostics()
      .then((value) => {
        if (active) setData(value as Diagnostics);
      })
      .catch(() => notify(t('operationFailed')));
    return () => {
      active = false;
      setData(null);
    };
  }, [notify, t]);
  if (!data) return <p>{t('loading')}</p>;
  const items: [string, string | number, string][] = [
    [t('applicationVersion'), data.appVersion, 'ok'],
    [t('databaseVersion'), data.schemaVersion, 'ok'],
    [
      t('databaseIntegrity'),
      t(data.integrity === 'ok' ? 'integrityOk' : 'integrityError'),
      data.integrity === 'ok' ? 'ok' : 'error',
    ],
    [t('databaseSize'), formatBytes(data.databaseSize), 'ok'],
    [
      t('availableDiskSpace'),
      data.availableDiskBytes === null ? t('unknown') : formatBytes(data.availableDiskBytes),
      data.storageHealth,
    ],
    [t('backupCount'), data.backupCount, data.backupCount ? 'ok' : 'warning'],
    [t('pendingEmails'), data.pendingEmails, data.pendingEmails ? 'warning' : 'ok'],
    [
      t('lastActivity'),
      data.lastHeartbeat ? new Date(data.lastHeartbeat).toLocaleString() : t('unknown'),
      data.lastHeartbeat ? 'ok' : 'unknown',
    ],
  ];
  return (
    <section>
      <div className="admin-section-heading">
        <div>
          <h2>{t('systemHealth')}</h2>
          <p>{t('diagnosticsPrivacyHint')}</p>
        </div>
        <Button
          variant="secondary"
          onClick={() => settingsService.openDataFolder().catch(() => notify(t('operationFailed')))}
        >
          {t('openFolder')}
        </Button>
      </div>
      <div className="diagnostic-cards">
        {items.map(([label, value, status]) => (
          <article key={label}>
            <span className={`admin-status ${status}`}>{t(`diagnostic_${status}`)}</span>
            <small>{label}</small>
            <strong>{value}</strong>
          </article>
        ))}
      </div>
    </section>
  );
}

function DangerPanel({ user, notify }: { user: User; notify: (message: string) => void }) {
  const { t } = useTranslation();
  const [password, setPassword] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const reset = async () => {
    if (!password || lock.current) return;
    lock.current = true;
    setBusy(true);
    try {
      await settingsService.reset({ adminId: user.id, password });
      location.reload();
    } catch {
      notify(t('authenticationFailed'));
      setConfirming(false);
    } finally {
      lock.current = false;
      setBusy(false);
      setPassword('');
    }
  };
  return (
    <section className="danger-zone-card">
      <TriangleAlert />
      <div>
        <h2>{t('resetStore')}</h2>
        <p>{t('resetStoreDetailed')}</p>
        <ul>
          <li>{t('resetCreatesBackup')}</li>
          <li>{t('resetIrreversible')}</li>
          <li>{t('resetRequiresOwnerPassword')}</li>
        </ul>
        <label className="ds-field">
          <span className="ds-field__label">{t('ownerPassword')}</span>
          <PasswordInput
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
          />
        </label>
        <Button
          variant="danger"
          loading={busy}
          loadingLabel={t('working')}
          disabled={!password}
          onClick={() => setConfirming(true)}
        >
          {t('reset')}
        </Button>
      </div>
      {confirming && (
        <ConfirmDialog
          title={t('confirmSystemResetTitle')}
          description={t('confirmSystemReset')}
          confirmLabel={t('reset')}
          cancelLabel={t('cancel')}
          danger
          onClose={() => setConfirming(false)}
          onConfirm={reset}
        />
      )}
    </section>
  );
}
function formatBytes(value: number) {
  if (!Number.isFinite(value) || value < 0) return '—';
  const units = ['o', 'Ko', 'Mo', 'Go', 'To'];
  let amount = value;
  let index = 0;
  while (amount >= 1024 && index < units.length - 1) {
    amount /= 1024;
    index++;
  }
  return `${amount.toFixed(index ? 1 : 0)} ${units[index]}`;
}
function backupKind(name: string, t: (key: string) => string) {
  if (name.startsWith('auto-')) return t('automatic');
  if (name.startsWith('pre-reset')) return t('preReset');
  if (name.startsWith('pre-restore')) return t('preRestore');
  return t('manual');
}
