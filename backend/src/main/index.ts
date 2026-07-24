import { app, BrowserWindow, dialog } from 'electron';
import fs from 'node:fs';
import path from 'node:path';
import { closeDatabase, initDatabase } from '../database/storeDatabase.js';
import { registerIpcHandlers } from './ipcHandlers.js';
import { createMainWindow } from './windowManager.js';
function logFatal(kind: string, error: unknown) {
  const message = error instanceof Error ? `${error.stack || error.message}` : String(error);
  try {
    fs.appendFileSync(
      path.join(app.getPath('userData'), 'store-errors.log'),
      `[${new Date().toISOString()}] ${kind}\n${message}\n`,
      'utf8',
    );
  } catch {
    console.error(kind, error);
  }
}

process.on('uncaughtException', (error) => logFatal('uncaughtException', error));
process.on('unhandledRejection', (error) => logFatal('unhandledRejection', error));

app.whenReady().then(async () => {
  try {
    await initDatabase();
    registerIpcHandlers();
    createMainWindow();
  } catch (error) {
    logFatal('startup', error);
    dialog.showErrorBox(
      'STORE',
      'Le démarrage a échoué. Consultez store-errors.log dans le dossier de données STORE.',
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
app.on('before-quit', () => closeDatabase());
