# STORE 3.0 — Security and authorization

## Trust boundaries

The renderer is untrusted. It may choose what to display, but it cannot authorize an operation. `contextIsolation`, sandboxing and disabled Node integration remain enabled. The preload exposes only named functions; it never exposes `ipcRenderer`, `require`, filesystem, process or shell primitives.

The main process binds a session to `event.sender.id`. Actor identifiers supplied by the renderer are overwritten for sales, cash, destructive operations, attendance, purchases and messages. Sensitive business work remains in the existing database layer.

## Session model

A successful login creates a sender-bound session with the real actor, base role, backend-derived permission set, random attendance reference and 30-minute inactivity expiry. `session` returns only user identity for display, grouped effective permissions, and minimal elevation display data. It never returns password/recovery hashes, SMTP credentials, session references or secrets.

## Permission model

The existing modules and actions remain the vocabulary: `READ`, `CREATE`, `UPDATE`, `DELETE`, `VALIDATE`. Role assignments seed policy; `permissionsForUser` is the runtime authority. `ipcAuthorizationPolicy` is the single IPC authorization registry. Unknown channel or unknown policy is denied.

Classifications:

- `PUBLIC`: setup/authentication/recovery entry points only.
- `AUTHENTICATED`: safe actor-scoped operations available to every valid session.
- `PROTECTED`: authenticated session plus one explicit module/action.
- `SYSTEM_INTERNAL`: never generally callable from the renderer.

## Default deny and preload

Startup verifies that every declared channel has a policy and a handler, and every database API method has a policy. `isPermissionGranted` returns false for unknown methods. The preload maintains an explicit allowlist mirroring the channel contract; it is not an authorization boundary.

## Temporary elevation

Elevation verifies a Manager/Owner credential but preserves the Employee as actor. The backend stores the authorizer separately and imposes a ten-minute expiry. Each protected call checks expiry and restores the actor's base permissions. Dropping elevation is sender-bound. Renderer timestamps are display state only and cannot extend backend authority. The current elevation remains Manager-like rather than operation-scoped; granular least-privilege elevation is deferred.

## Frontend consumption

The renderer calls `session` after authentication and elevation changes. `can(module, action)` consumes only the returned snapshot. Navigation and representative sensitive controls derive from it. Missing/failed permission state hides all protected destinations and actions. Backend checks remain mandatory even when an affordance is hidden.

## Error and logging model

Expected failures return stable public codes such as `AUTH_REQUIRED` or `FORBIDDEN`. Unexpected handler failures are recorded by the technical logger and become `INTERNAL_ERROR` at the renderer boundary. Credentials, temporary passwords, recovery answers, SMTP passwords and session material must never be logged.

## Forbidden patterns

- Role checks as general frontend authorization.
- Generic `ipcRenderer.invoke(channel)` exposure.
- Trusting renderer actor, authorizer, elevation or expiry fields.
- Allowing an unregistered IPC or permission.
- Returning secrets or raw database records in a session payload.
- Broadening a permission to preserve an obsolete UI workflow.

## Testing strategy

Tests compare channel declarations, policy and preload exposure; exercise Owner, Manager, Employee, elevated/expired and unauthenticated decisions; verify unknown-deny behavior; inspect actor rebinding, minimal payload and isolation; then run the complete regression, lint, build, diff and Electron startup gates.
