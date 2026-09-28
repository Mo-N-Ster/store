import type { IpcMethod } from '../../ipc/channels.js';
import type { PermissionAction, PermissionModule } from './permissionMatrix.js';

export type PermissionCode = `${PermissionModule}:${PermissionAction}`;
export type IpcClassification = 'PUBLIC' | 'AUTHENTICATED' | 'PROTECTED' | 'SYSTEM_INTERNAL';
export type IpcPolicy = Readonly<{ classification: IpcClassification; permission?: PermissionCode; rationale: string }>;
const pub = (rationale: string): IpcPolicy => ({ classification: 'PUBLIC', rationale });
const auth = (rationale: string): IpcPolicy => ({ classification: 'AUTHENTICATED', rationale });
const protect = (permission: PermissionCode, rationale: string): IpcPolicy => ({ classification: 'PROTECTED', permission, rationale });

/** Backend authorization source of truth. Every renderer channel must be listed. */
export const ipcAuthorizationPolicy = {
  userPermissions: protect('ADMINISTRATION:READ', 'Reads inherited, denied and effective permissions.'),
  auditLogs: protect('ADMINISTRATION:READ', 'Read-only bounded audit metadata, without raw details or secrets.'),
  saveUserPermissions: protect('ADMINISTRATION:UPDATE', 'Owner-only subtractive overrides, protected again in the backend transaction.'),
  needsSetup: pub('Required before a session exists.'), setupAdmin: pub('One-time bootstrap guarded by setup state.'),
  login: pub('Authentication entry point.'), switchUser: auth('Atomically authenticates and replaces the sender-bound identity.'), forgotPasswordQuestion: pub('Limited recovery entry point.'),
  recoverPassword: pub('Backend-validated and rate-limited recovery proof.'),
  logout: auth('Ends the sender-bound session when cash safety permits.'), session: auth('Returns a minimal sender-bound session snapshot.'),
  verifyAdmin: auth('Re-authenticates the current privileged actor server-side.'),
  users: protect('EMPLOYEES:READ', 'Reads personnel/accounts.'), saveUser: protect('EMPLOYEES:UPDATE', 'Changes personnel/accounts.'),
  resetPassword: protect('EMPLOYEES:UPDATE', 'Issues a temporary employee credential.'),
  generateUserPassword: protect('EMPLOYEES:UPDATE', 'Issues a protected temporary credential with backend-bound actor attribution.'),
  setUserPassword: protect('EMPLOYEES:UPDATE', 'Sets a policy-validated credential with backend-bound actor attribution.'),
  securityQuestion: protect('EMPLOYEES:UPDATE', 'Supports an authorized reset.'),
  resetManagerPassword: protect('EMPLOYEES:UPDATE', 'Changes a privileged credential.'),
  products: protect('PRODUCTS:READ', 'Reads catalogue.'), articleImage: protect('PRODUCTS:READ', 'Reads one validated managed article image.'),
  selectArticleImage: protect('PRODUCTS:UPDATE', 'Selects and stages one validated article image.'), saveProduct: protect('PRODUCTS:UPDATE', 'Changes products and stock.'),
  deleteProduct: protect('PRODUCTS:DELETE', 'Archives product and stock.'), adjustStock: protect('STOCKS:UPDATE', 'Changes stock.'), stockMovements: protect('STOCKS:READ', 'Reads persisted stock movement traceability.'),
  startInventory: protect('STOCKS:CREATE', 'Creates count.'), recordInventoryLine: protect('STOCKS:UPDATE', 'Changes count draft.'),
  validateInventory: protect('STOCKS:VALIDATE', 'Applies differences to stock.'), suppliers: protect('PURCHASES:READ', 'Reads suppliers.'),
  saveSupplier: protect('PURCHASES:UPDATE', 'Changes suppliers.'), purchases: protect('PURCHASES:READ', 'Reads purchases.'), purchaseDetail: protect('PURCHASES:READ', 'Reads one purchase and its persisted relations.'),
  createPurchase: protect('PURCHASES:CREATE', 'Creates draft.'), savePurchaseItem: protect('PURCHASES:UPDATE', 'Changes draft.'),
  validatePurchase: protect('PURCHASES:VALIDATE', 'Applies purchase to stock.'), cancelPurchase: protect('PURCHASES:DELETE', 'Cancels/compensates purchase.'),
  createInvoice: protect('POS:VALIDATE', 'Commits sale and stock.'), currentCashSession: protect('CASH:READ', 'Reads actor cash session.'),
  openCashSession: protect('CASH:CREATE', 'Opens actor cash session.'), closeCashSession: protect('CASH:VALIDATE', 'Closes actor cash session.'),
  invoices: protect('POS:READ', 'Reads invoices.'), invoice: protect('POS:READ', 'Reads invoice detail.'),
  deleteInvoice: protect('POS:DELETE', 'Cancels invoice and compensates stock.'), history: protect('FINANCES:READ', 'Reads financial history.'),
  deleteHistory: protect('FINANCES:DELETE', 'Deletes financial history.'),
  clockAttendance: protect('PRESENCE:READ', 'Credential-confirmed explicit current-time attendance.'),
  attendanceSheet: protect('PRESENCE:READ', 'Reads the multi-user daily attendance sheet.'),
  attendanceHistory: protect('PRESENCE:READ', 'Reads persisted attendance history.'),
  correctAttendance: protect('PRESENCE:UPDATE', 'Administrative correction.'), attendanceStatuses: protect('PRESENCE:READ', 'Server-scoped presence.'),
  messages: auth('Actor-visible messages only.'), messageRecipients: auth('Active recipient discovery.'),
  sendMessage: auth('Sender identity is server-bound.'), markMessage: auth('Actor read state only.'), deleteMessage: auth('Actor deletion state only.'),
  notifications: protect('STOCKS:READ', 'Reads stock alerts.'), deleteNotifications: protect('STOCKS:DELETE', 'Deletes stock alerts.'),
  emailReportLogs: protect('FINANCES:READ', 'Reads delivery diagnostics.'), retryEmailQueue: protect('FINANCES:CREATE', 'Triggers delivery.'),
  deleteEmailReportLogs: protect('FINANCES:DELETE', 'Deletes delivery records.'), dashboard: protect('DASHBOARD:READ', 'Reads aggregates.'),
  reports: protect('FINANCES:READ', 'Reads reports.'), settings: protect('SETTINGS:READ', 'Reads global settings without secrets.'),
  preferences: auth('Reads minimal non-secret display settings.'), saveSettings: protect('SETTINGS:UPDATE', 'Changes global settings.'),
  testEmail: protect('SETTINGS:VALIDATE', 'Uses SMTP secret server-side.'), backup: protect('BACKUPS:CREATE', 'Creates backup.'),
  backups: protect('BACKUPS:READ', 'Lists backup metadata.'), systemDiagnostics: protect('ADMINISTRATION:READ', 'Reads sensitive diagnostics.'),
  exportBackup: protect('BACKUPS:CREATE', 'Copies backup via save dialog.'), openDataFolder: protect('ADMINISTRATION:READ', 'Opens private data directory.'),
  restoreBackup: protect('RESTORE:VALIDATE', 'Replaces active database.'), reset: protect('RESET:VALIDATE', 'Destroys operational data.'),
  saveExport: auth('Writes renderer-provided content via save dialog.'), selectFile: auth('Returns only a user-selected path; consumer remains protected.'),
  importProducts: protect('PRODUCTS:CREATE', 'Imports products.'), importProductsPdf: protect('PRODUCTS:CREATE', 'Imports products.'),
  exportReportPdf: protect('FINANCES:READ', 'Exports authorized report.'), emailReportPdf: protect('FINANCES:CREATE', 'Sends authorized report.'),
  printInvoice: protect('POS:READ', 'Prints authorized invoice view.'), exportInvoicePdf: protect('POS:READ', 'Exports authorized invoice view.'),
} as const satisfies Record<IpcMethod, IpcPolicy>;

export function authorizationFor(method: string): IpcPolicy | undefined { return ipcAuthorizationPolicy[method as IpcMethod]; }
export function isPermissionGranted(permissions: ReadonlySet<string>, method: string) {
  const policy = authorizationFor(method);
  if (!policy || policy.classification === 'SYSTEM_INTERNAL') return false;
  if (policy.classification === 'PUBLIC' || policy.classification === 'AUTHENTICATED') return true;
  return Boolean(policy.permission && permissions.has(policy.permission));
}
export function isIpcAuthorized(method: string, permissions: ReadonlySet<string> | null) {
  const policy = authorizationFor(method);
  if (!policy || policy.classification === 'SYSTEM_INTERNAL') return false;
  if (policy.classification === 'PUBLIC') return true;
  return permissions !== null && isPermissionGranted(permissions, method);
}
