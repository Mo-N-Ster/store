import fs from 'node:fs';
import { describe, expect, it } from 'vitest';
import { IPC_METHODS } from '../../../backend/src/ipc/channels';
import { authorizationFor, ipcAuthorizationPolicy, isIpcAuthorized, isPermissionGranted } from '../../../backend/src/domain/rbac/ipcPermissions';
import { defaultPermissions } from '../../../backend/src/domain/rbac/permissionMatrix';

describe('Phase E.5 authorization hardening', () => {
  const owner = defaultPermissions('ADMIN');
  const manager = defaultPermissions('MANAGER');
  const employee = defaultPermissions('CASHIER');

  it('classifies every declared channel exactly once and denies unknown channels', () => {
    expect(Object.keys(ipcAuthorizationPolicy).sort()).toEqual([...IPC_METHODS].sort());
    expect(authorizationFor('inventedChannel')).toBeUndefined();
    expect(isPermissionGranted(owner, 'inventedChannel')).toBe(false);
    expect(isIpcAuthorized('inventedChannel', owner)).toBe(false);
  });

  it('allows unauthenticated callers only into explicit public entry points', () => {
    for (const method of IPC_METHODS) {
      const expected = authorizationFor(method)?.classification === 'PUBLIC';
      expect(isIpcAuthorized(method, null), method).toBe(expected);
    }
  });

  it('enforces representative Owner, Manager and Employee boundaries', () => {
    for (const method of ['saveProduct', 'adjustStock', 'validateInventory', 'validatePurchase', 'saveUser', 'correctAttendance']) {
      expect(isIpcAuthorized(method, owner), `owner ${method}`).toBe(true);
      expect(isIpcAuthorized(method, manager), `manager ${method}`).toBe(method !== 'correctAttendance');
      expect(isIpcAuthorized(method, employee), `employee ${method}`).toBe(false);
    }
    for (const method of ['restoreBackup', 'reset']) {
      expect(isIpcAuthorized(method, owner)).toBe(true);
      expect(isIpcAuthorized(method, manager)).toBe(false);
      expect(isIpcAuthorized(method, employee)).toBe(false);
    }
  });

  it('keeps each role on its backend-derived permissions without elevation', () => {
    expect(isIpcAuthorized('adjustStock', manager)).toBe(true);
    expect(isIpcAuthorized('adjustStock', employee)).toBe(false);
    expect(isIpcAuthorized('adjustStock', null)).toBe(false);
  });

  it('keeps preload an explicit allowlist without raw Node or generic IPC exposure', () => {
    const preload = fs.readFileSync('backend/src/preload/index.cts', 'utf8');
    for (const method of IPC_METHODS) expect(preload, method).toContain(`'${method}'`);
    expect(preload).not.toMatch(/exposeInMainWorld\([^,]+,\s*ipcRenderer/);
    expect(preload).not.toMatch(/exposeInMainWorld\([^,]+,\s*require/);
    expect(preload).not.toContain('send: ipcRenderer.send');
  });

  it('binds sensitive actor fields to the backend session', () => {
    const handlers = fs.readFileSync('backend/src/main/ipcHandlers.ts', 'utf8');
    for (const binding of ['first.employeeId = session.id', 'first.userId = session.id', 'first.correctedBy = session.id', 'first.senderId = session.id']) expect(handlers).toContain(binding);
    expect(handlers).toContain("session.role === 'employee'");
  });

  it('returns a minimal permission snapshot and no known secret fields', () => {
    const handlers = fs.readFileSync('backend/src/main/ipcHandlers.ts', 'utf8');
    const start = handlers.indexOf("registerSpecial('session'");
    const sessionHandler = handlers.slice(start, handlers.indexOf("'exportBackup'", start));
    expect(sessionHandler).toContain('effectivePermissions');
    for (const secret of ['password_hash', 'security_answer_hash', 'smtpPassword', 'sessionRef:']) expect(sessionHandler).not.toContain(secret);
  });
});
