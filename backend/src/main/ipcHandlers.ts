import { dialog, ipcMain, shell, type IpcMainInvokeEvent } from 'electron';
import { copyFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { api, permissionsForUser, sendReportEmail, sessionAuthority } from '../database/storeDatabase.js';
import { stageArticleImage } from '../domain/media/articleMedia.js';
import { IPC_METHODS, IPC_PREFIX, type IpcMethod } from '../ipc/channels.js';
import { embedStoreData } from '../services/stockPdfService.js';
import { isExpectedApplicationError, publicErrorCode } from '../domain/errors.js';
import { logTechnical } from '../services/technicalLogger.js';
import { authorizationFor, isPermissionGranted } from '../domain/rbac/ipcPermissions.js';
import { randomUUID } from 'node:crypto';
import { auditSession } from '../domain/system/auditWriter.js';
import { validateHistoryDeletion } from '../domain/rbac/historyOperation.js';
export function registerIpcHandlers() {
  const sanitizedIpcError = (error: unknown) => new Error(publicErrorCode(error));
  type Session = {
    id: number;
    role: 'owner' | 'manager' | 'employee';
    permissions: Set<string>;
    sessionRef: string;
    displayName: string;
  };
  const sessions = new Map<number, Session>();
  const apiMethods = new Set(Object.keys(api));
  const specialMethods = new Set(['logout', 'session', 'exportBackup', 'openDataFolder', 'printInvoice', 'exportInvoicePdf', 'saveExport', 'exportReportPdf', 'emailReportPdf', 'selectFile', 'selectArticleImage']);
  for (const method of IPC_METHODS) {
    if (!authorizationFor(method)) throw new Error(`Missing IPC authorization policy: ${method}`);
    if (!apiMethods.has(method) && !specialMethods.has(method)) throw new Error(`Missing IPC handler: ${method}`);
  }
  for (const method of apiMethods)
    if (!authorizationFor(method)) throw new Error(`Unclassified API handler: ${method}`);
  const registerSpecial = (name: IpcMethod, handler: Parameters<typeof ipcMain.handle>[1]) =>
    ipcMain.handle(`${IPC_PREFIX}${name}`, async (event, ...args) => {
      try { return await handler(event, ...args); }
      catch (error) {
        if (!isExpectedApplicationError(error)) logTechnical('ipc-failure', error, { method: name, userId: sessions.get(event.sender.id)?.id });
        throw sanitizedIpcError(error);
      }
    });

  const requireSession = (event: IpcMainInvokeEvent, name: string) => {
    const policy = authorizationFor(name);
    if (!policy || policy.classification === 'SYSTEM_INTERNAL') throw new Error('FORBIDDEN');
    if (policy.classification === 'PUBLIC') return undefined;
    const session = sessions.get(event.sender.id);
    if (!session) throw new Error('AUTH_REQUIRED');
    const current = sessionAuthority(session.id);
    if (!current?.active) { sessions.delete(event.sender.id); throw new Error('AUTH_REQUIRED'); }
    session.role = current.role;
    session.permissions = new Set(permissionsForUser(session.id));
    if (!isPermissionGranted(session.permissions, name)) throw new Error('FORBIDDEN');
    return session;
  };
  const secureArgs = (name: string, args: unknown[], session?: Session) => {
    if (!session) return args;
    const first = args[0] && typeof args[0] === 'object' ? { ...(args[0] as Record<string, unknown>) } : {};
    if (name === 'switchUser') first.currentUserId = session.id;
    if (name === 'saveUserPermissions') first.actorId = session.id;
    if (name === 'saveUser' && session.role !== 'owner' && first.id !== undefined) {
      const target = (api.users() as { id: number; role: string }[]).find((user) => user.id === first.id);
      if (!target || target.role !== 'employee') throw new Error('FORBIDDEN');
    }
    if (name === 'verifyAdmin') first.id = session.id;
    if (name === 'createInvoice') first.employeeId = session.id;
    if (['currentCashSession', 'openCashSession', 'closeCashSession'].includes(name))
      first.employeeId = session.id;
    if (['deleteProduct', 'deleteInvoice', 'deleteHistory'].includes(name)) first.userId = session.id;
    if (name === 'reset') first.adminId = session.id;
    if (['generateUserPassword', 'setUserPassword'].includes(name)) {
      first.actorId = session.id;
      first.actorRole = session.role;
    }
    if (
      [
        'adjustStock',
        'startInventory',
        'validateInventory',
        'saveSupplier',
        'createPurchase',
        'validatePurchase',
        'cancelPurchase',
      ].includes(name)
    )
      first.userId = session.id;
    if (name === 'correctAttendance') first.correctedBy = session.id;
    if (name === 'clockAttendance') first.facilitatedBy = session.id;
    if (name === 'attendanceStatuses' && session.role === 'employee') first.employeeId = session.id;
    if (name === 'messages') {
      first.userId = session.id;
      first.role = session.role;
    }
    if (name === 'sendMessage') first.senderId = session.id;
    if (['markMessage', 'deleteMessage'].includes(name)) first.userId = session.id;
    if (
      name === 'saveUser' &&
      session.role !== 'owner' &&
      first.role !== undefined &&
      first.role !== 'employee'
    )
      throw new Error('FORBIDDEN');
    return Object.keys(first).length ? [first, ...args.slice(1)] : args;
  };
  let databaseQueue: Promise<unknown> = Promise.resolve();
  for (const [name, handler] of Object.entries(api))
    ipcMain.handle(`${IPC_PREFIX}${name}`, (event, ...args) => {
      const execute = () =>
        Promise.resolve().then(() => {
          // Validate the operation before applying its fixed FINANCES:DELETE policy.
          if (name === 'deleteHistory') args = [validateHistoryDeletion(args[0])];
          const session = requireSession(event, name);
          const invoke = () => (handler as (...values: unknown[]) => unknown)(...secureArgs(name, args, session));
          return session ? auditSession.run({ id: session.id, displayName: session.displayName }, invoke) : invoke();
        }).then((result) => {
          if ((name === 'login' || name === 'setupAdmin' || name === 'switchUser') && result && typeof result === 'object') {
            const user = result as { id: number; role: Session['role']; first_name?: string; last_name?: string; username?: string };
            sessions.set(event.sender.id, {
              id: user.id,
              role: user.role,
              permissions: new Set(permissionsForUser(user.id)),
              sessionRef: randomUUID(),
              displayName: [user.first_name, user.last_name].filter(Boolean).join(' ') || user.username || `#${user.id}`,
            });
          }
          return result;
        });
      const guardedExecute = () =>
        execute().catch((error) => {
          if (!isExpectedApplicationError(error))
            logTechnical('ipc-failure', error, {
              method: name,
              userId: sessions.get(event.sender.id)?.id,
            });
          throw new Error(publicErrorCode(error));
        });
      const result = databaseQueue.then(guardedExecute, guardedExecute);
      databaseQueue = result.then(
        () => undefined,
        () => undefined,
      );
      return result;
    });
  registerSpecial('logout', (event) => {
    const session = requireSession(event, 'logout')!;
    if (api.currentCashSession({ employeeId: session.id })) throw new Error('CASH_SESSION_OPEN');
    sessions.delete(event.sender.id);
    return true;
  });
  registerSpecial('session', (event) => {
    const session = requireSession(event, 'session')!;
    const grouped = new Map<string, string[]>();
    for (const permission of session.permissions) {
      const [module, action] = permission.split(':');
      if (!module || !action) continue;
      grouped.set(module, [...(grouped.get(module) || []), action]);
    }
    return {
      user: { id: session.id, displayName: session.displayName, role: session.role },
      effectivePermissions: [...grouped].sort(([a], [b]) => a.localeCompare(b)).map(([module, actions]) => ({ module, actions: actions.sort() })),
    };
  });
  registerSpecial(
    'exportBackup',
    async (event) => {
      requireSession(event, 'exportBackup');
      const source = await api.backup();
      const result = await dialog.showSaveDialog({
        defaultPath: path.basename(source),
        filters: [{ name: 'STORE backup', extensions: ['store-backup'] }],
      });
      if (result.canceled || !result.filePath) return null;
      await copyFile(source, result.filePath);
      return result.filePath;
    },
  );
  registerSpecial('openDataFolder', async (event) => {
    requireSession(event, 'openDataFolder');
    const diagnostics = api.systemDiagnostics() as { dataDirectory: string };
    const error = await shell.openPath(diagnostics.dataDirectory);
    if (error) throw new Error('INVALID_EXPORT');
    return true;
  });
  registerSpecial(
    'printInvoice',
    async (event) => {
      requireSession(event, 'printInvoice');
      return new Promise<boolean>((resolve, reject) => {
        event.sender.print({ silent: false, printBackground: true }, (success, reason) => {
          if (success) resolve(true);
          else reject(new Error(reason || 'PRINT_FAILED'));
        });
      });
    },
  );
  registerSpecial(
    'exportInvoicePdf',
    async (event, { name }: { name: string }) => {
      requireSession(event, 'exportInvoicePdf');
      if (typeof name !== 'string' || !/^FACT-[A-Za-z0-9_-]+\.pdf$/.test(name))
        throw new Error('INVALID_EXPORT');
      const result = await dialog.showSaveDialog({
        defaultPath: name,
        filters: [{ name: 'PDF', extensions: ['pdf'] }],
      });
      if (result.canceled || !result.filePath) return null;
      const pdf = await event.sender.printToPDF({
        printBackground: true,
        landscape: false,
        pageSize: 'A4',
        margins: { marginType: 'custom', top: 0.25, bottom: 0.25, left: 0.25, right: 0.25 },
      });
      await writeFile(result.filePath, pdf);
      return result.filePath;
    },
  );
  registerSpecial(
    'saveExport',
    async (event, { name, content }: { name: string; content: string }) => {
      requireSession(event, 'saveExport');
      if (typeof name !== 'string' || typeof content !== 'string' || content.length > 10_000_000)
        throw new Error('INVALID_EXPORT');
      const result = await dialog.showSaveDialog({ defaultPath: name });
      if (result.canceled || !result.filePath) return null;
      await import('node:fs/promises').then((fs) =>
        fs.writeFile(result.filePath!, content, 'utf8'),
      );
      return result.filePath;
    },
  );
  registerSpecial(
    'exportReportPdf',
    async (event, input: { name: string; data?: unknown }) => {
      requireSession(event, 'exportReportPdf');
      const printedPdf = await event.sender.printToPDF({
        printBackground: true,
        landscape: true,
        pageSize: 'A4',
        margins: { marginType: 'custom', top: 0.3, bottom: 0.3, left: 0.3, right: 0.3 },
      });
      const result = await dialog.showSaveDialog({
        defaultPath: input.name,
        filters: [{ name: 'PDF', extensions: ['pdf'] }],
      });
      if (result.canceled || !result.filePath) return null;
      let pdf = printedPdf;
      if (input.data !== undefined) pdf = await embedStoreData(printedPdf, input.data);
      await writeFile(result.filePath, pdf);
      return result.filePath;
    },
  );
  registerSpecial(
    'emailReportPdf',
    async (event, input: { to: string; subject: string; text: string; filename: string }) => {
      const session = requireSession(event, 'emailReportPdf');
      if (!session || !isPermissionGranted(session.permissions, 'emailReportPdf'))
        throw new Error('FORBIDDEN');
      const pdf = await event.sender.printToPDF({
        printBackground: true,
        landscape: true,
        pageSize: 'A4',
        margins: { marginType: 'custom', top: 0.3, bottom: 0.3, left: 0.3, right: 0.3 },
      });
      return sendReportEmail({ ...input, pdf });
    },
  );
  registerSpecial(
    'selectArticleImage',
    async (event) => {
      requireSession(event, 'selectArticleImage');
      const result = await dialog.showOpenDialog({
        properties: ['openFile'],
        filters: [{ name: 'JPEG, PNG, WebP', extensions: ['jpg', 'jpeg', 'png', 'webp'] }],
      });
      if (result.canceled || !result.filePaths[0]) return null;
      const diagnostics = api.systemDiagnostics() as { dataDirectory: string };
      return stageArticleImage(result.filePaths[0], path.join(diagnostics.dataDirectory, 'media', 'articles'));
    },
  );
  registerSpecial(
    'selectFile',
    async (event, { filters }: { filters?: Electron.FileFilter[] }) => {
      requireSession(event, 'selectFile');
      const result = await dialog.showOpenDialog({ properties: ['openFile'], filters });
      return result.canceled ? null : result.filePaths[0];
    },
  );
}
