import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { User } from '../../../types';
import { storeApi } from '../../../services/api';
export function SettingsPage({ user, notify }: { user: User; notify: (x: string) => void }) {
  const { t } = useTranslation();
  const [settings, setSettings] = useState<any>({});
  useEffect(() => {
    storeApi.settings().then(setSettings);
  }, []);
  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await storeApi.saveSettings(Object.fromEntries(new FormData(event.currentTarget)));
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
      await storeApi.reset({ adminId: user.id, password });
      location.reload();
    } catch {
      notify('Authentification incorrecte');
    }
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
        <button>{t('save')}</button>
      </form>
      <section className="danger-zone">
        <h3>Gestion des données</h3>
        <button
          onClick={() => storeApi.backup().then((path: string) => notify(`Sauvegarde: ${path}`))}
        >
          {t('backup')}
        </button>
        <button className="danger" onClick={reset}>
          {t('reset')}
        </button>
      </section>
    </>
  );
}
