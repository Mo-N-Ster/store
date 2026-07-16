import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { User } from '../../../types';
import { settingsService } from '../../../services/settingsService';
import { selectFile } from '../../../services/api';
export function SettingsPage({ user, notify }: { user: User; notify: (x: string) => void }) {
  const { t } = useTranslation();
  const [settings, setSettings] = useState<any>({});
  useEffect(() => {
    settingsService.get().then(setSettings);
  }, []);
  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await settingsService.save(Object.fromEntries(new FormData(event.currentTarget)));
    notify('Paramètres enregistrés');
  };
  const reset = async () => {
    const password = prompt('Mot de passe admin');
    if (
      !password ||
      !confirm('Toutes les données seront définitivement supprimées. Souhaitez-vous continuer ?')
    )
      return;
    try {
      await settingsService.reset({ adminId: user.id, password });
      location.reload();
    } catch {
      notify('Authentification incorrecte');
    }
  };
  const restore = async () => {
    const file = await selectFile([{ name: 'Sauvegarde SQLite', extensions: ['db', 'sqlite'] }]);
    if (!file) return;
    if (!confirm('Restaurer cette sauvegarde remplacera les données actuelles. Continuer ?'))
      return;
    await settingsService.restore(file);
    location.reload();
  };
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">Configuration</span>
          <h1>{t('settings')}</h1>
        </div>
      </div>
      <form className="settings settings-card" onSubmit={save}>
        <label>
          {t('storeName')}
          <input name="storeName" defaultValue={settings.storeName || 'STORE'} />
        </label>
        <label>
          Adresse
          <input name="address" defaultValue={settings.address || ''} />
        </label>
        <label>
          Téléphone
          <input name="phone" defaultValue={settings.phone || ''} />
        </label>
        <label>
          Email
          <input name="email" type="email" defaultValue={settings.email || ''} />
        </label>
        <label>
          Serveur SMTP
          <input name="smtpHost" defaultValue={settings.smtpHost || ''} />
        </label>
        <label>
          Port SMTP
          <input name="smtpPort" type="number" defaultValue={settings.smtpPort || 587} />
        </label>
        <label>
          Utilisateur SMTP
          <input name="smtpUser" defaultValue={settings.smtpUser || ''} />
        </label>
        <label>
          Mot de passe SMTP
          <input name="smtpPassword" type="password" defaultValue={settings.smtpPassword || ''} />
        </label>
        <label>
          Expéditeur
          <input name="smtpFrom" type="email" defaultValue={settings.smtpFrom || ''} />
        </label>
        <button>{t('save')}</button>
        <button
          type="button"
          className="ghost"
          onClick={() =>
            settingsService
              .testEmail()
              .then(() => notify('Email de test envoyé.'))
              .catch(() => notify('Échec SMTP. Vérifiez la connexion et les paramètres.'))
          }
        >
          Tester SMTP
        </button>
      </form>
      <section className="danger-zone">
        <h3>Gestion des données</h3>
        <button
          onClick={() =>
            settingsService.backup().then((path: string) => notify(`Sauvegarde: ${path}`))
          }
        >
          {t('backup')}
        </button>
        <button className="danger" onClick={reset}>
          {t('reset')}
        </button>
        <button className="ghost" onClick={restore}>
          Restaurer une sauvegarde
        </button>
      </section>
    </>
  );
}
