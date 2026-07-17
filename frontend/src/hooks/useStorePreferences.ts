import { useCallback, useEffect, useState } from 'react';
import { settingsService } from '../services/settingsService';

export type StorePreferences = {
  currency: string;
  discountsEnabled: boolean;
};

const defaults: StorePreferences = { currency: 'EUR', discountsEnabled: true };
export const SETTINGS_UPDATED_EVENT = 'store:settings-updated';

export function useStorePreferences() {
  const [preferences, setPreferences] = useState(defaults);
  const reload = useCallback(() => {
    void settingsService.get().then((settings: Record<string, string>) =>
      setPreferences({
        currency: settings.currency || defaults.currency,
        discountsEnabled: settings.discountsEnabled !== 'false',
      }),
    );
  }, []);

  useEffect(() => {
    reload();
    window.addEventListener(SETTINGS_UPDATED_EVENT, reload);
    return () => window.removeEventListener(SETTINGS_UPDATED_EVENT, reload);
  }, [reload]);

  return preferences;
}
