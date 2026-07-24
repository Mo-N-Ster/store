import { dialog, ipcMain } from 'electron';
import { writeFile } from 'node:fs/promises';
import { api, sendReportEmail } from '../database/storeDatabase.js';
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
  ipcMain.handle(`${IPC_PREFIX}exportReportPdf`, async (event, { name }: { name: string }) => {
    const pdf = await event.sender.printToPDF({
      printBackground: true,
      landscape: true,
      pageSize: 'A4',
      margins: { marginType: 'custom', top: 0.3, bottom: 0.3, left: 0.3, right: 0.3 },
    });
    const result = await dialog.showSaveDialog({
      defaultPath: name,
      filters: [{ name: 'PDF', extensions: ['pdf'] }],
    });
    if (result.canceled || !result.filePath) return null;
    await writeFile(result.filePath, pdf);
    return result.filePath;
  });
  ipcMain.handle(
    `${IPC_PREFIX}emailReportPdf`,
    async (event, input: { to: string; subject: string; text: string; filename: string }) => {
      const pdf = await event.sender.printToPDF({
        printBackground: true,
        landscape: true,
        pageSize: 'A4',
        margins: { marginType: 'custom', top: 0.3, bottom: 0.3, left: 0.3, right: 0.3 },
      });
      return sendReportEmail({ ...input, pdf });
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
