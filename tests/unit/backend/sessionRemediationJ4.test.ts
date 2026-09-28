import fs from 'node:fs';
import { describe, expect, it } from 'vitest';
import { IPC_METHODS } from '../../../backend/src/ipc/channels';
import { authorizationFor, isIpcAuthorized } from '../../../backend/src/domain/rbac/ipcPermissions';
import { defaultPermissions } from '../../../backend/src/domain/rbac/permissionMatrix';

const read = (path: string) => fs.readFileSync(path, 'utf8');

describe('J.4 authentication, session and user switching', () => {
  const handlers = read('backend/src/main/ipcHandlers.ts');
  const database = read('backend/src/database/storeDatabase.ts');
  const app = read('frontend/src/App.tsx');
  const shell = read('frontend/src/components/Layout/StoreShell.tsx');
  const switchDialog = read('frontend/src/components/UI/SwitchUserDialog.tsx');

  it('removes general inactivity expiry on both backend and renderer', () => {
    expect(handlers).not.toMatch(/lastActivityAt|30 \* 60 \* 1000|touchSession/);
    expect(app).not.toMatch(/sessionExpired|touchSession|30 \* 60 \* 1000/);
  });

  it('removes every callable temporary-elevation surface', () => {
    expect(IPC_METHODS).not.toContain('dropElevation');
    expect(authorizationFor('dropElevation')).toBeUndefined();
    expect(isIpcAuthorized('dropElevation', defaultPermissions('ADMIN'))).toBe(false);
    expect(handlers).not.toMatch(/elevatedUntil|authorizedById|dropElevation/);
    expect(shell).not.toMatch(/elevatedUntil|managerAccess|onRequestElevation/);
  });

  it('exposes one narrow authenticated switch operation through the strict preload', () => {
    expect(authorizationFor('switchUser')?.classification).toBe('AUTHENTICATED');
    expect(read('backend/src/preload/index.cts')).toContain("'switchUser'");
    expect(switchDialog).toContain('authService.switchUser({ identifier, password })');
    expect(switchDialog).not.toMatch(/localStorage|sessionStorage/);
  });

  it('binds the current identity in backend and replaces only after authentication succeeds', () => {
    expect(handlers).toContain("if (name === 'switchUser') first.currentUserId = session.id");
    expect(handlers.indexOf("return (handler as")).toBeLessThan(handlers.indexOf("name === 'switchUser') && result"));
    expect(handlers).toContain('permissions: new Set(permissionsForUser(user.id))');
  });

  it('preserves the old session when target authentication fails', () => {
    const replacement = handlers.slice(handlers.indexOf("name === 'login'"), handlers.indexOf('const guardedExecute'));
    expect(replacement.indexOf('sessions.set')).toBeGreaterThan(replacement.indexOf('.then((result)'));
    expect(database).toContain("throw new Error('INVALID_CREDENTIALS')");
  });

  it('applies normal lockout and disabled-account rules to the target', () => {
    expect(database).toContain('isLoginLocked(user.locked_until, currentTime)');
    expect(database).toContain('!user.active');
    expect(database).toContain('nextFailedLogin');
  });

  it('blocks switch and logout while current actor owns open cash', () => {
    expect(database).toContain("SELECT id FROM cash_sessions WHERE employee_id=? AND status='OPEN'");
    expect(database).toContain("throw new Error('CASH_SESSION_OPEN')");
    expect(handlers).toContain('if (api.currentCashSession({ employeeId: session.id }))');
    expect(handlers).toContain("throw new Error('CASH_SESSION_OPEN')");
  });

  it('blocks checkout and explicitly resolves a non-empty cart before identity change', () => {
    expect(app).toContain('sessionSafety.checkoutCritical');
    expect(app).toContain("window.confirm(t('abandonCartConfirm'))");
    expect(app).toContain('setClearCartRequest');
  });

  it('clears identity-sensitive UI and applies a safe authorized destination', () => {
    expect(app).toContain('setPermissions(null)');
    expect(app).toContain('setChatOpen(false)');
    expect(app).toContain('history.current = []');
    expect(app).toContain('destinationIsAvailable(view.effectivePermissions, current) ? current : defaultDestination(view.effectivePermissions)');
  });

  it('does not couple switch, logout or inactivity to attendance', () => {
    expect(handlers).not.toContain('api.attendance');
    expect(database.slice(database.indexOf('switchUser:'), database.indexOf('verifyAdmin:'))).not.toContain('attendances');
  });
});
