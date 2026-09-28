import { app, BrowserWindow, dialog } from 'electron';
import {
  closeDatabase,
  ensureDailyBackup,
  initDatabase,
  processEmailQueue,
  recordHeartbeat,
} from '../database/storeDatabase.js';
import { registerIpcHandlers } from './ipcHandlers.js';
import { createMainWindow } from './windowManager.js';
import { logTechnical } from '../services/technicalLogger.js';
import { isolatedTestProfile } from '../domain/system/testProfile.js';
const testProfile = isolatedTestProfile(process.env.STORE_TEST_PROFILE, app.isPackaged);
if (testProfile) app.setPath('userData', testProfile);
function logFatal(kind: string, error: unknown) {
  logTechnical(kind, error);
}

process.on('uncaughtException', (error) => logFatal('uncaughtException', error));
process.on('unhandledRejection', (error) => logFatal('unhandledRejection', error));

app.whenReady().then(async () => {
  try {
    await initDatabase();
    registerIpcHandlers();
    createMainWindow();
    if (testProfile && process.env.STORE_TEST_SMOKE === '1') {
      setTimeout(() => app.quit(), 10000);
    }
    const heartbeat = setInterval(recordHeartbeat, 60_000);
    heartbeat.unref();
    const backupCheck = setInterval(
      () => void ensureDailyBackup().catch((error) => logFatal('automaticBackup', error)),
      60 * 60 * 1000,
    );
    backupCheck.unref();
    const emailQueue = setInterval(
      () => void processEmailQueue().catch((error) => logFatal('emailQueue', error)),
      2 * 60 * 1000,
    );
    emailQueue.unref();
  } catch (error) {
    logFatal('startup', error);
    if (testProfile && process.env.STORE_TEST_SMOKE === '1') { app.exit(1); return; }
    dialog.showErrorBox(
      'STORE',
      'Le démarrage a échoué. Consultez logs/technical.jsonl dans le dossier de données STORE.',
    );
    app.quit();
    return;
  }
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createMainWindow();
  });
});
app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
app.on('before-quit', () => {
  closeDatabase();
});
