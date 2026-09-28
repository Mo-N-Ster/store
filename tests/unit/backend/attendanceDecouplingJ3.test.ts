import fs from 'node:fs';
import { describe, expect, it } from 'vitest';
import { authorizationFor, isPermissionGranted } from '../../../backend/src/domain/rbac/ipcPermissions';
import { defaultPermissions } from '../../../backend/src/domain/rbac/permissionMatrix';

describe('J.3 authentication/attendance separation', () => {
  it('contains no login, logout, timeout, startup, or shutdown attendance mutation', () => {
    const handlers = fs.readFileSync('backend/src/main/ipcHandlers.ts', 'utf8');
    const main = fs.readFileSync('backend/src/main/index.ts', 'utf8');
    expect(handlers).not.toContain('api.attendance');
    expect(handlers).not.toContain('clockAttendance({');
    expect(main).not.toContain('closeOpenAttendances');
    expect(main).not.toContain('recoverInterruptedAttendances');
  });
  it('protects narrow attendance capabilities without granting correction authority', () => {
    for (const method of ['clockAttendance', 'attendanceSheet', 'attendanceHistory'])
      expect(authorizationFor(method)?.permission).toBe('PRESENCE:READ');
    expect(isPermissionGranted(defaultPermissions('CASHIER'), 'clockAttendance')).toBe(true);
    expect(isPermissionGranted(defaultPermissions('CASHIER'), 'correctAttendance')).toBe(false);
  });
  it('binds the facilitating application actor but not the attendance subject', () => {
    const handlers = fs.readFileSync('backend/src/main/ipcHandlers.ts', 'utf8');
    expect(handlers).toContain("if (name === 'clockAttendance') first.facilitatedBy = session.id");
    expect(handlers).not.toContain("if (name === 'clockAttendance') first.employeeId = session.id");
  });
});
