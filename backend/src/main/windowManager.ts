import { app, BrowserWindow, shell } from 'electron';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { logTechnical } from '../services/technicalLogger.js';
const dirname = path.dirname(fileURLToPath(import.meta.url));
let mainWindow: BrowserWindow | null = null;

export function createMainWindow() {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.show();
    mainWindow.focus();
    return mainWindow;
  }

  const window = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 720,
    minHeight: 500,
    // Display the shell immediately. On slow or degraded GPUs, Electron's
    // `ready-to-show` event may never fire and would otherwise leave STORE
    // running invisibly even though the main process is healthy.
    show: process.env.STORE_TEST_SMOKE !== '1' || !process.env.STORE_TEST_PROFILE || app.isPackaged,
    backgroundColor: '#f5f2e9',
    title: 'STORE',
    icon: app.isPackaged
      ? path.join(dirname, '../../frontend/store-logo.png')
      : path.join(process.cwd(), 'frontend/public/store-logo.png'),
    webPreferences: {
      preload: path.join(dirname, '../preload/index.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  mainWindow = window;
  window.on('closed', () => {
    if (mainWindow === window) mainWindow = null;
  });
  window.webContents.on('did-fail-load', (_event, code, description, url) =>
    logTechnical('renderer-load-failed', undefined, { code, description, url }),
  );
  window.webContents.on('will-navigate', (event, url) => {
    const allowed = process.env.VITE_DEV_SERVER_URL;
    if ((allowed && url.startsWith(allowed)) || url.startsWith('file:')) return;
    event.preventDefault();
  });
  window.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https:\/\//i.test(url)) void shell.openExternal(url);
    return { action: 'deny' };
  });
  let recoveryTimer: NodeJS.Timeout | undefined;
  let rendererRecoveryAttempts = 0;
  window.on('unresponsive', () => {
    logTechnical('renderer-unresponsive');
    recoveryTimer = setTimeout(() => {
      if (!window.isDestroyed()) window.webContents.reload();
    }, 15000);
  });
  window.on('responsive', () => {
    if (recoveryTimer) clearTimeout(recoveryTimer);
    recoveryTimer = undefined;
  });
  window.webContents.on('render-process-gone', (_event, details) => {
    logTechnical('renderer-process-gone', undefined, {
      reason: details.reason,
      exitCode: details.exitCode,
    });
    if (!window.isDestroyed() && details.reason !== 'clean-exit' && rendererRecoveryAttempts < 2) {
      rendererRecoveryAttempts++;
      window.webContents.reload();
    }
  });
  if (process.env.VITE_DEV_SERVER_URL && /^http:\/\/(localhost|127\.0\.0\.1):\d+\/?$/i.test(process.env.VITE_DEV_SERVER_URL))
    void window.loadURL(process.env.VITE_DEV_SERVER_URL);
  else void window.loadFile(path.join(dirname, '../../frontend/index.html'));
  return window;
}
