import { describe, expect, it } from 'vitest';
import { defaultPermissions, legacyRoleToSystemRole } from '../../../backend/src/domain/rbac/permissionMatrix';

describe('initial permission matrix', () => {
  it('keeps system operations exclusive to administrators', () => {
    expect(defaultPermissions('ADMIN')).toContain('RESET:VALIDATE');
    expect(defaultPermissions('ADMIN')).toContain('RESTORE:VALIDATE');
    expect(defaultPermissions('MANAGER')).not.toContain('RESET:VALIDATE');
    expect(defaultPermissions('MANAGER')).not.toContain('RESTORE:VALIDATE');
    expect(defaultPermissions('CASHIER')).not.toContain('ADMINISTRATION:READ');
  });

  it('maps every legacy role to its version 2 system role', () => {
    expect(legacyRoleToSystemRole('owner')).toBe('ADMIN');
    expect(legacyRoleToSystemRole('manager')).toBe('MANAGER');
    expect(legacyRoleToSystemRole('employee')).toBe('CASHIER');
  });
});
