import { dialog, ipcMain } from 'electron';
import { api } from '../database/storeDatabase.js';
import { IPC_PREFIX } from '../ipc/channels.js';
export function registerIpcHandlers() {
  let databaseQueue: Promise<unknown> = Promise.resolve();
  for (const [name, handler] of Object.entries(api))
    ipcMain.handle(`${IPC_PREFIX}${name}`, (_event, ...args) => {
      const execute = () =>
        Promise.resolve().then(() => (handler as (...values: unknown[]) => unknown)(...args));
      const result = databaseQueue.then(execute, execute);
      databaseQueue = result.then(
        () => undefined,
        () => undefined,
      );
      return result;
    });
  ipcMain.handle(
    `${IPC_PREFIX}saveExport`,
    async (_event, { name, content }: { name: string; content: string }) => {
      const result = await dialog.showSaveDialog({ defaultPath: name });
      if (result.canceled || !result.filePath) return null;
      await import('node:fs/promises').then((fs) =>
        fs.writeFile(result.filePath!, content, 'utf8'),
      );
      return result.filePath;
    },
  );
  ipcMain.handle(
    `${IPC_PREFIX}selectFile`,
    async (_event, { filters }: { filters?: Electron.FileFilter[] }) => {
      const result = await dialog.showOpenDialog({ properties: ['openFile'], filters });
      return result.canceled ? null : result.filePaths[0];
    },
  );
}
