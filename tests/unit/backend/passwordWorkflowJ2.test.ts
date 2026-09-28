import fs from 'node:fs';
import { describe, expect, it } from 'vitest';
import { assertAdministrativePasswordTarget } from '../../../backend/src/domain/auth/adminPasswordPolicy';
import { authorizationFor, isPermissionGranted } from '../../../backend/src/domain/rbac/ipcPermissions';
import { defaultPermissions } from '../../../backend/src/domain/rbac/permissionMatrix';

describe('J.2 administrative password workflow', () => {
  it('allows an owner to reset managers and employees but never the Primary Owner', () => {
    expect(assertAdministrativePasswordTarget({ role: 'manager', active: 1 }, { id: 1, role: 'owner' })).toBe(true);
    expect(assertAdministrativePasswordTarget({ role: 'employee', active: 1 }, { id: 1, role: 'owner' })).toBe(true);
    expect(() => assertAdministrativePasswordTarget({ role: 'owner', active: 1 }, { id: 1, role: 'owner' })).toThrow('FORBIDDEN');
  });
  it('allows managers to reset employees but not privileged accounts', () => {
    expect(assertAdministrativePasswordTarget({ role: 'employee', active: 1 }, { id: 2, role: 'manager' })).toBe(true);
    expect(() => assertAdministrativePasswordTarget({ role: 'manager', active: 1 }, { id: 2, role: 'manager' })).toThrow('FORBIDDEN');
  });
  it('protects both dedicated channels and binds the actor in the backend', () => {
    for (const method of ['generateUserPassword', 'setUserPassword']) {
      expect(authorizationFor(method)?.classification).toBe('PROTECTED');
      expect(isPermissionGranted(defaultPermissions('CASHIER'), method)).toBe(false);
    }
    const handlers = fs.readFileSync('backend/src/main/ipcHandlers.ts', 'utf8');
    expect(handlers).toContain('first.actorId = session.id');
    expect(handlers).toContain('first.actorRole = session.role');
  });
  it('keeps secrets out of storage and requires confirmation in the UI', () => {
    const dialog = fs.readFileSync('frontend/src/pages/Dashboard/employees/PasswordResetDialog.tsx', 'utf8');
    expect(dialog).toContain('newPassword !== confirmation');
    expect(dialog).not.toMatch(/localStorage|sessionStorage/);
    expect(dialog).toContain('clearSecrets()');
    expect(dialog).toContain('employeeService.generateUserPassword');
    expect(dialog).not.toContain('Math.random');
  });
});
