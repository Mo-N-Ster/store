import fs from 'node:fs';
import { describe, expect, it } from 'vitest';

const read = (path: string) => fs.readFileSync(path, 'utf8');
const admin = read('frontend/src/pages/Dashboard/settings/SettingsPage.tsx');
const employees = read('frontend/src/pages/Dashboard/employees/EmployeeList.tsx');
const password = read('frontend/src/pages/Dashboard/employees/PasswordResetDialog.tsx');
const css = read('frontend/src/design-system/administration.css');

describe('STORE 3.0 Phase G administration', () => {
  it('drives every administration destination from effective permissions', () => {
    expect(admin).toContain('sections.filter(({ permission }) => can(permissions, ...permission))');
    expect(admin).not.toContain("user.role === 'owner'");
  });
  it('keeps effective permissions readable and exposes the approved subtractive editor', () => {
    expect(admin).toContain('<PermissionEditor');
    expect(admin).toContain('permissionActions.map');
    expect(admin).not.toContain('savePermissions');
  });
  it('hides account mutations without EMPLOYEES:UPDATE', () => {
    expect(employees).toContain("can(permissions, 'EMPLOYEES', 'UPDATE')");
    expect(employees).toContain('{mayUpdate &&');
  });
  it('requires an explicit action before generating a temporary credential', () => {
    expect(password).toContain('onClick={() => void generate()}');
    expect(password).not.toContain("else if (!temporaryPassword) employeeService.resetPassword");
    expect(password).toContain('lock.current');
  });
  it('prevents duplicate backup, restore, and reset submissions', () => {
    expect(admin.match(/lock\.current/g)?.length).toBeGreaterThanOrEqual(5);
    expect(admin).toContain('<ConfirmDialog');
  });
  it('does not render full diagnostic paths or backup result paths', () => {
    expect(admin).not.toContain('data.dataDirectory');
    expect(admin).not.toContain('diagnostics.databasePath');
    expect(admin).not.toContain("backupCreated', { path");
  });
  it('exposes audit consultation and preserves the restore reauthentication limitation', () => {
    expect(admin).toContain('<AuditPanel');
    expect(admin).toContain('restoreDebtWarning');
  });
  it('provides tablet, portrait, and reduced-motion layouts', () => {
    expect(css).toContain('@media(max-width:1024px)');
    expect(css).toContain('@media(max-width:760px)');
    expect(css).toContain('@media(prefers-reduced-motion:reduce)');
  });
});
