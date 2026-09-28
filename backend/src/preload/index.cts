// CommonJS is required by Electron for sandboxed preload scripts.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { contextBridge, ipcRenderer } = require('electron') as typeof import('electron');

// Un preload sandboxé ne peut charger que les modules autorisés par Electron.
// La liste reste locale afin de ne pas importer de fichier applicatif dans ce contexte.
const IPC_PREFIX = 'store:';
const IPC_METHODS = [
  'needsSetup', 'setupAdmin', 'login', 'switchUser', 'logout', 'session', 'verifyAdmin', 'users', 'saveUser',
  'resetPassword', 'generateUserPassword', 'setUserPassword', 'securityQuestion', 'resetManagerPassword', 'forgotPasswordQuestion',
  'recoverPassword', 'products', 'articleImage', 'selectArticleImage', 'saveProduct', 'deleteProduct', 'adjustStock', 'stockMovements',
  'startInventory', 'recordInventoryLine', 'validateInventory', 'createInvoice', 'invoices',
  'suppliers', 'saveSupplier', 'purchases', 'purchaseDetail', 'createPurchase', 'savePurchaseItem',
  'validatePurchase', 'cancelPurchase', 'currentCashSession', 'openCashSession', 'closeCashSession',
  'invoice', 'deleteInvoice', 'history', 'deleteHistory', 'clockAttendance', 'attendanceSheet', 'attendanceHistory', 'correctAttendance', 'attendanceStatuses',
  'messages', 'messageRecipients', 'sendMessage', 'markMessage', 'deleteMessage', 'notifications',
  'deleteNotifications', 'emailReportLogs', 'retryEmailQueue', 'deleteEmailReportLogs', 'dashboard', 'reports',
  'settings', 'preferences', 'saveSettings', 'backup', 'backups', 'systemDiagnostics', 'exportBackup', 'openDataFolder', 'reset', 'saveExport', 'selectFile', 'importProducts',
  'importProductsPdf', 'restoreBackup', 'testEmail', 'exportReportPdf', 'emailReportPdf',
  'printInvoice', 'exportInvoicePdf', 'userPermissions', 'saveUserPermissions', 'auditLogs',
] as const;

const bridge = Object.fromEntries(
  IPC_METHODS.map((name) => [
    name,
    (...args: unknown[]) => ipcRenderer.invoke(`${IPC_PREFIX}${name}`, ...args),
  ]),
);

contextBridge.exposeInMainWorld('store', bridge);
