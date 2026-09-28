# STORE 3.0 — PHASE E.5 SECURITY HARDENING REPORT

## 1. Executive summary
Phase E.5 replaces implicit IPC allow with an exhaustive typed registry and default deny. Effective permissions now flow backend → minimal session view → preload → frontend adapter. Phase F was not started.

## 2. Security baseline
Baseline: 21 files, 90 tests, lint/build/diff/startup passing, version 2.0.1. No database migration was introduced.

## 3. Trust boundaries
Renderer untrusted; preload allowlist only; main process authenticates/authorizes; existing business/database layer executes.

## 4. Current session model
Sessions are keyed by Electron sender, expire after 30 minutes inactivity and bind actor identity server-side.

## 5. Current RBAC model
Existing modules/actions and database-derived role permissions are retained unchanged.

## 6. Current elevation model
Ten-minute Manager/Owner credential elevation preserves Employee actor identity and records a distinct authorizer. Granular scope remains deferred.

## 7. Complete IPC inventory

Direction is Renderer → Main for every row. Caller is the named frontend service or direct shell handler. Test coverage is `securityHardening` plus the related domain regression.

| Name(s) | Final classification | Module/action | Callers | Security rationale |
|---|---|---|---|---|
| needsSetup, setupAdmin, login | PUBLIC | — | authService | Required before a session; setup invariant/credential proof enforced backend-side. |
| forgotPasswordQuestion, recoverPassword | PUBLIC | — | authService | Limited, rate-limited recovery path. |
| logout, dropElevation, touchSession, session, verifyAdmin | AUTHENTICATED | — | authService/App | Sender-bound session lifecycle and minimal snapshot. |
| users | PROTECTED | EMPLOYEES:READ | employeeService | Personnel/account data. |
| saveUser, resetPassword, securityQuestion, resetManagerPassword | PROTECTED | EMPLOYEES:UPDATE | employeeService | Account mutation/credential operations. |
| products | PROTECTED | PRODUCTS:READ | productService | Catalogue read. |
| saveProduct | PROTECTED | PRODUCTS:UPDATE | productService | Product/stock mutation. |
| deleteProduct | PROTECTED | PRODUCTS:DELETE | productService | Destructive archive/stock mutation. |
| adjustStock | PROTECTED | STOCKS:UPDATE | operationsService | Direct stock mutation. |
| startInventory | PROTECTED | STOCKS:CREATE | operationsService | Inventory draft creation. |
| recordInventoryLine | PROTECTED | STOCKS:UPDATE | operationsService | Inventory draft mutation. |
| validateInventory | PROTECTED | STOCKS:VALIDATE | operationsService | Applies stock differences. |
| suppliers, purchases | PROTECTED | PURCHASES:READ | operationsService | Purchasing data read. |
| saveSupplier, savePurchaseItem | PROTECTED | PURCHASES:UPDATE | operationsService | Supplier/draft mutation. |
| createPurchase | PROTECTED | PURCHASES:CREATE | operationsService | Draft creation. |
| validatePurchase | PROTECTED | PURCHASES:VALIDATE | operationsService | Applies received stock. |
| cancelPurchase | PROTECTED | PURCHASES:DELETE | operationsService | Cancellation/compensation. |
| createInvoice | PROTECTED | POS:VALIDATE | saleService | Commits sale/stock. |
| invoices, invoice, printInvoice, exportInvoicePdf | PROTECTED | POS:READ | saleService/direct | Invoice read/output. |
| deleteInvoice | PROTECTED | POS:DELETE | saleService | Destructive compensation. |
| currentCashSession | PROTECTED | CASH:READ | saleService | Actor cash session. |
| openCashSession | PROTECTED | CASH:CREATE | saleService | Actor cash opening. |
| closeCashSession | PROTECTED | CASH:VALIDATE | saleService | Actor cash closure. |
| history, reports | PROTECTED | FINANCES:READ | sale/reportService | Financial detail. |
| deleteHistory | PROTECTED | FINANCES:DELETE | saleService | Destructive financial operation. |
| exportReportPdf | PROTECTED | FINANCES:READ | reportService | Authorized report export. |
| emailReportPdf, retryEmailQueue | PROTECTED | FINANCES:CREATE | report/dashboardService | Outbound delivery. |
| emailReportLogs | PROTECTED | FINANCES:READ | dashboardService | Delivery diagnostics. |
| deleteEmailReportLogs | PROTECTED | FINANCES:DELETE | dashboardService | Log deletion. |
| attendance, attendanceStatuses | PROTECTED | PRESENCE:READ | attendance/App | Own presence; employee status scope server-limited. |
| correctAttendance | PROTECTED | PRESENCE:UPDATE | attendanceService | Administrative correction. |
| messages, messageRecipients, sendMessage, markMessage, deleteMessage | AUTHENTICATED | — | messageService | Actor-scoped messaging; identity rebound server-side. |
| notifications | PROTECTED | STOCKS:READ | dashboardService | Stock alerts. |
| deleteNotifications | PROTECTED | STOCKS:DELETE | dashboardService | Alert deletion. |
| dashboard | PROTECTED | DASHBOARD:READ | dashboardService | Existing aggregates only. |
| settings | PROTECTED | SETTINGS:READ | settingsService | Global settings, secrets stripped. |
| preferences | AUTHENTICATED | — | settingsService | Minimal currency/discount snapshot. |
| saveSettings | PROTECTED | SETTINGS:UPDATE | settingsService | Global/SMTP mutation. |
| testEmail | PROTECTED | SETTINGS:VALIDATE | settingsService | Uses secret only backend-side. |
| backup, exportBackup | PROTECTED | BACKUPS:CREATE | settingsService | Backup creation/copy. |
| backups | PROTECTED | BACKUPS:READ | settingsService | Backup/path metadata. |
| restoreBackup | PROTECTED | RESTORE:VALIDATE | settingsService | Database replacement. |
| reset | PROTECTED | RESET:VALIDATE | settingsService | Destructive reset plus owner password proof. |
| systemDiagnostics, openDataFolder | PROTECTED | ADMINISTRATION:READ | settingsService | Sensitive paths/system data. |
| importProducts, importProductsPdf | PROTECTED | PRODUCTS:CREATE | productService | Bulk product mutation. |
| saveExport, selectFile | AUTHENTICATED | — | product/sale/api | User-mediated dialog; consuming mutation remains protected. |

Current classification before E.5 was PUBLIC set + mapped PROTECTED set + all omissions implicitly allowed. Final classification is the table above; none remain implicit.

## 8. IPC/preload/handler consistency
Startup checks policy↔channels↔handlers. Tests check channels↔preload. No generic invocation bridge exists.

## 9. Previous UNCLASSIFIED findings
Products reads, invoices, attendance/statuses, messages, dashboard, preferences, file dialogs and several session operations were implicit. All are now explicit.

## 10. New findings
`attendanceStatuses` exposed all users to Employee and was filtered only by React. Backend now scopes Employee calls to the authenticated ID.

## 11. Final IPC classification model
PUBLIC, AUTHENTICATED, PROTECTED and SYSTEM_INTERNAL are explicit typed values.

## 12. Default-deny design
Unknown registry entry → deny; protected without exact permission → deny; internal → deny.

## 13. Default-deny implementation
`ipcAuthorizationPolicy`, startup exhaustiveness checks and closed `isPermissionGranted`.

## 14. Public capabilities
Only setup, login and recovery entry points.

## 15. Authenticated capabilities
Session lifecycle, actor-scoped messaging, minimal preferences and user-mediated generic dialogs.

## 16. Protected capabilities
All business, administrative, financial, stock and destructive operations listed above.

## 17. System-internal capabilities
No system-internal function is exposed by preload. Unknown/internal policies are denied.

## 18. effectivePermissions backend source
`permissionsForUser` reads existing role assignments and permissions from SQLite.

## 19. Safe renderer exposure
New `session` method returns a minimal sender-bound view.

## 20. Permission payload
User id/display name/base role, grouped module/actions, and optional authorizer display/expiry only. No secrets/session reference.

## 21. Frontend permission adapter
Pure `can(module, action)`; null and unknown values deny.

## 22. Navigation migration
Destinations derive from effective permissions; roles no longer reconstruct access policy.

## 23. Operational affordance migration
Product create/edit/delete/import, stock adjust/inventory validation and presence correction are permission-driven. Backend remains authoritative for all actions.

## 24. Owner authorization
Existing ADMIN permissions preserved, including restore/reset.

## 25. Manager authorization
Operational management preserved; restore/reset/settings/administration and attendance correction remain denied by actual matrix.

## 26. Employee authorization
POS/cash plus declared reads preserved; privileged direct calls denied.

## 27. Temporary elevation
Backend Manager-like permission snapshot; renderer cannot set authorizer or expiry.

## 28. Expired elevation
Every protected call removes expired elevation before authorization; stale UI cannot authorize.

## 29. Actor/authorizer integrity
Actor stays the Employee. Authorizer id/display are distinct session metadata.

## 30. Session expiration
Expired/missing sender session returns `AUTH_REQUIRED`; mounted React pages retain no backend authority.

## 31–41. Module security
Products, Stock, Inventory, Purchase, Team, Presence, Messages, Dashboard, Preferences, Backup/Restore/Reset and file dialogs are classified in section 7. Actor fields are rebound; secrets stay server-side; restore/reset remain Owner-only. Dashboard is audited only, not redesigned.

## 42. Parameter tampering tests
Static contract tests verify backend rebinding of employee/user/corrector/sender IDs. `verifyAdmin` and reset owner ID are also overwritten.

## 43. Direct IPC attack tests
Policy tests call authorization decisions directly for all roles, unauthenticated and unknown channels.

## 44. Error leakage tests
Business and special handlers translate unexpected failures to `INTERNAL_ERROR`; details go to technical logs.

## 45. Preload isolation tests
Explicit allowlist checked; no raw IPC/Node primitive. Electron isolation flags remain active.

## 46. Files added
`frontend/src/security/permissions.ts`, two security test files, this report, `docs/SECURITY_AUTHORIZATION.md`.

## 47. Files modified
Authorization: `ipcPermissions.ts`, `ipcHandlers.ts`, `channels.ts`; preload: `index.cts`; presence scope: `storeDatabase.ts`; frontend: `authService.ts`, `App.tsx`, navigation and operational views; tests updated for permission snapshots.

## 48. Dependencies
None added.

## 49. Tests added
Two security-focused files plus migrated navigation coverage. Nine explicit security tests cover registry completeness, role matrix, unknown/unauthenticated denial, isolation, payload, actor rebinding and frontend fail-closed behavior.

## 50. Security-specific test result
PASS — 9/9.

## 51. Full regression result
PASS — 23 files, 99/99 tests, 0 failed, 0 skipped. The original 90-test surface remains represented; permission-navigation assertions were migrated from roles to backend snapshots.

## 52. Lint result
PASS — ESLint, zero warnings.

## 53. Build result
PASS — TypeScript project build and Vite production build.

## 54. git diff --check
PASS.

## 55. Electron startup
PASS — process remained active without console startup error until the deliberate SIGINT. Automated visual state remains NOT OBSERVABLE (`apps: []`) and is not presented as a visual pass.

## 56. Security authorization matrix

| Capability | Owner | Manager | Employee | Elevated Employee | Expired elevation | Unauthenticated |
|---|---:|---:|---:|---:|---:|---:|
| Product read | ALLOW | ALLOW | ALLOW | ALLOW | ALLOW (base) | DENY |
| Product edit | ALLOW | ALLOW | DENY | ALLOW | DENY | DENY |
| Stock adjust | ALLOW | ALLOW | DENY | ALLOW | DENY | DENY |
| Inventory validate | ALLOW | ALLOW | DENY | ALLOW | DENY | DENY |
| Purchase validate | ALLOW | ALLOW | DENY | ALLOW | DENY | DENY |
| Employee role change | ALLOW | Manager→Employee only | DENY | Manager→Employee only | DENY | DENY |
| Attendance correction | ALLOW | DENY | DENY | DENY | DENY | DENY |
| Backup restore | ALLOW | DENY | DENY | DENY | DENY | DENY |
| Reset | ALLOW | DENY | DENY | DENY | DENY | DENY |

## 57. Acceptance criteria matrix

E5-AC-001–016 PASS: exhaustive registry, explicit classifications, default deny, strict preload, backend permission source, minimal safe payload, permission navigation/actions and fail-closed loading.

E5-AC-017–028 PASS: role boundaries, session/elevation expiry, actor/authorizer separation, tampering rebinding, direct denial and safe errors.

E5-AC-029–040 PASS: every requested module and file-dialog family audited/classified/tested at the policy boundary.

E5-AC-041–046 PASS: no migration or business semantic change; Phase E draft limitations untouched.

E5-AC-047–056 PASS: historical and new tests, lint/build/diff/startup, no dependency/version change, documentation complete, Phase F not started.

## 58. Deviations
The session payload uses the existing role strings for identity/display while authorization uses effective permissions. This is intentional.

## 59. Remaining vulnerabilities/limitations
SECURITY DEBT: elevation is broad Manager-like access, not operation-scoped. Generic select/save dialogs are AUTHENTICATED but mutations consuming selected paths remain independently protected. Restore has permission plus backup validation but no second Owner prompt in this phase.

## 60. Functional debt intentionally deferred
Inventory/purchase draft resumption across restart remains deferred exactly as in Phase E.

## 61. Files intentionally not modified
Schema, migrations, hashing, sales/stock/purchase/inventory/cash transaction semantics, backup format, SMTP semantics and product version.

## 62. Recommendation for Phase F
READY FOR PHASE F only if all final gates below pass. Phase F requires separate authorization and has not started.
