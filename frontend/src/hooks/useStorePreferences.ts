import { useCallback, useEffect, useState } from 'react';
import { settingsService } from '../services/settingsService';

export type StorePreferences = {
  currency: string;
  discountsEnabled: boolean;
};

const defaults: StorePreferences = { currency: 'EUR', discountsEnabled: true };
export const SUPPORTED_CURRENCIES = [
  'EUR',
  'XOF',
  'XAF',
  'CAD',
  'GBP',
  'CHF',
  'NGN',
  'GHS',
] as const;
export const SETTINGS_UPDATED_EVENT = 'store:settings-updated';

export function useStorePreferences() {
  const [preferences, setPreferences] = useState(defaults);
  const reload = useCallback(() => {
    void settingsService.get().then((settings: Record<string, string>) =>
      setPreferences({
        currency: SUPPORTED_CURRENCIES.includes(
          settings.currency as (typeof SUPPORTED_CURRENCIES)[number],
        )
          ? settings.currency
          : defaults.currency,
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
