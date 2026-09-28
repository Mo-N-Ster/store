import { storeApi } from './api';
export const settingsService = {
  get: () => storeApi.settings(),
  preferences: () => storeApi.preferences(),
  save: (input: unknown) => storeApi.saveSettings(input),
  backup: () => storeApi.backup(),
  backups: () => storeApi.backups(),
  diagnostics: () => storeApi.systemDiagnostics(),
  exportBackup: () => storeApi.exportBackup(),
  openDataFolder: () => storeApi.openDataFolder(),
  reset: (input: unknown) => storeApi.reset(input),
  restore: (filePath: string) => storeApi.restoreBackup(filePath),
  testEmail: () => storeApi.testEmail(),
};
