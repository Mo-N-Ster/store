import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { User } from '../../../types';
import { settingsService } from '../../../services/settingsService';
import { selectFile } from '../../../services/api';
import { SETTINGS_UPDATED_EVENT, SUPPORTED_CURRENCIES } from '../../../hooks/useStorePreferences';
export function SettingsPage({ user, notify }: { user: User; notify: (x: string) => void }) {
  const { t } = useTranslation();
  const [settings, setSettings] = useState<any>({});
  useEffect(() => {
    settingsService.get().then(setSettings);
  }, []);
  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const values = Object.fromEntries(form) as Record<string, string>;
    values.discountsEnabled = form.has('discountsEnabled') ? 'true' : 'false';
    await settingsService.save(values);
    setSettings((current: any) => ({ ...current, ...values }));
    window.dispatchEvent(new Event(SETTINGS_UPDATED_EVENT));
    notify(t('settingsSaved'));
  };
  const reset = async () => {
    const password = prompt(t('ownerPassword'));
    if (!password || !confirm(t('confirmSystemReset'))) return;
    try {
      await settingsService.reset({ adminId: user.id, password });
      location.reload();
    } catch {
      notify(t('authenticationFailed'));
    }
  };
  const restore = async () => {
    const file = await selectFile([{ name: t('sqliteBackup'), extensions: ['db', 'sqlite'] }]);
    if (!file) return;
    if (!confirm(t('confirmRestore'))) return;
    await settingsService.restore(file);
    location.reload();
  };
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">{t('configuration')}</span>
          <h1>{t('settings')}</h1>
        </div>
      </div>
      <form key={JSON.stringify(settings)} className="settings settings-card" onSubmit={save}>
        <label>
          {t('storeName')}
          <input name="storeName" defaultValue={settings.storeName || 'STORE'} />
        </label>
        <label>
          {t('address')}
          <input name="address" defaultValue={settings.address || ''} />
        </label>
        <label>
          {t('phone')}
          <input name="phone" defaultValue={settings.phone || ''} />
        </label>
        <label>
          Email
          <input name="email" type="email" defaultValue={settings.email || ''} />
        </label>
        <label>
          {t('currency')}
          <select
            name="currency"
            defaultValue={
              SUPPORTED_CURRENCIES.includes(settings.currency) ? settings.currency : 'EUR'
            }
          >
            {SUPPORTED_CURRENCIES.map((code) => (
              <option key={code} value={code}>
                {t(`currency${code}`)}
              </option>
            ))}
          </select>
        </label>
        <label className="checkbox-label">
          <input
            name="discountsEnabled"
            type="checkbox"
            value="true"
            defaultChecked={settings.discountsEnabled !== 'false'}
          />
          {t('enableDiscounts')}
        </label>
        <label>
          {t('smtpServer')}
          <input name="smtpHost" defaultValue={settings.smtpHost || ''} />
        </label>
        <label>
          {t('smtpPort')}
          <input name="smtpPort" type="number" defaultValue={settings.smtpPort || 587} />
        </label>
        <label>
          {t('smtpUser')}
          <input name="smtpUser" defaultValue={settings.smtpUser || ''} />
        </label>
        <label>
          {t('smtpPassword')}
          <input name="smtpPassword" type="password" defaultValue={settings.smtpPassword || ''} />
        </label>
        <label>
          {t('sender')}
          <input name="smtpFrom" type="email" defaultValue={settings.smtpFrom || ''} />
        </label>
        <button>{t('save')}</button>
        <button
          type="button"
          className="ghost"
          onClick={() =>
            settingsService
              .testEmail()
              .then(() => notify(t('testEmailSent')))
              .catch(() => notify(t('smtpFailed')))
          }
        >
          {t('testSmtp')}
        </button>
      </form>
      <section className="danger-zone">
        <h3>{t('dataManagement')}</h3>
        <button
          onClick={() =>
            settingsService.backup().then((path: string) => notify(t('backupCreated', { path })))
          }
        >
          {t('backup')}
        </button>
        {user.role === 'owner' && (
          <button className="danger" onClick={reset}>
            {t('reset')}
          </button>
        )}
        <button className="ghost" onClick={restore}>
          {t('restoreBackup')}
        </button>
      </section>
    </>
  );
}
