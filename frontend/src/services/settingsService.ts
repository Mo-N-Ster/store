import { storeApi } from './api';
export const settingsService = {
  get: () => storeApi.settings(),
  save: (input: unknown) => storeApi.saveSettings(input),
  backup: () => storeApi.backup(),
  reset: (input: unknown) => storeApi.reset(input),
  restore: (filePath: string) => storeApi.restoreBackup(filePath),
};
