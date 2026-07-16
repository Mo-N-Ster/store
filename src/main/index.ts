import { app, BrowserWindow } from 'electron';
import { initDatabase } from '../database/storeDatabase.js';
import { registerIpcHandlers } from './ipcHandlers.js';
import { createMainWindow } from './windowManager.js';
app.whenReady().then(() => {
  initDatabase();
  registerIpcHandlers();
  createMainWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createMainWindow();
  });
});
app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
