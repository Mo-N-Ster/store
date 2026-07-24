import { BrowserWindow } from 'electron';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
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
    show: false,
    backgroundColor: '#f5f2e9',
    title: 'STORE',
    webPreferences: {
      preload: path.join(dirname, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  });
  mainWindow = window;
  window.on('closed', () => {
    if (mainWindow === window) mainWindow = null;
  });
  window.once('ready-to-show', () => window.show());
  window.webContents.on('did-fail-load', (_event, code, description, url) =>
    console.error('Renderer load failed', { code, description, url }),
  );
  let recoveryTimer: NodeJS.Timeout | undefined;
  let rendererRecoveryAttempts = 0;
  window.on('unresponsive', () => {
    console.error('Renderer became unresponsive');
    recoveryTimer = setTimeout(() => {
      if (!window.isDestroyed()) window.webContents.reload();
    }, 15000);
  });
  window.on('responsive', () => {
    if (recoveryTimer) clearTimeout(recoveryTimer);
    recoveryTimer = undefined;
  });
  window.webContents.on('render-process-gone', (_event, details) => {
    console.error('Renderer process exited', details);
    if (!window.isDestroyed() && details.reason !== 'clean-exit' && rendererRecoveryAttempts < 2) {
      rendererRecoveryAttempts++;
      window.webContents.reload();
    }
  });
  if (process.env.VITE_DEV_SERVER_URL) void window.loadURL(process.env.VITE_DEV_SERVER_URL);
  else void window.loadFile(path.join(dirname, '../../frontend/index.html'));
  return window;
}
