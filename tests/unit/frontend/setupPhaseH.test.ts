import fs from 'node:fs';
import Database from 'better-sqlite3';
import { describe, expect, it } from 'vitest';
import { schema } from '../../../backend/src/database/schema';
import { authorizationFor } from '../../../backend/src/domain/rbac/ipcPermissions';
import { validateUser } from '../../../backend/src/domain/user/user.validators';

const wizard = fs.readFileSync('frontend/src/pages/auth/SetupWizard.tsx', 'utf8');
const app = fs.readFileSync('frontend/src/App.tsx', 'utf8');
const backend = fs.readFileSync('backend/src/database/storeDatabase.ts', 'utf8');
const css = fs.readFileSync('frontend/src/design-system/setup.css', 'utf8');

describe('STORE 3.0 Phase H setup', () => {
  it('routes exclusively from backend setup state', () => {
    expect(app).toContain('authService.needsSetup().then(setNeedsSetup)');
    expect(app).toContain('if (needsSetup) return <Setup');
    expect(wizard).not.toMatch(/localStorage\.(setItem|getItem)\(['"]setup/);
  });
  it('implements the six required wizard steps', () => {
    for (const step of ['welcome', 'shop', 'owner', 'preferences', 'verification', 'ready']) expect(wizard).toContain(`'${step}'`);
    expect(wizard).toContain("aria-current={itemIndex === index ? 'step'");
  });
  it('keeps the Owner role backend-defined and guards repeat setup', () => {
    expect(wizard).not.toMatch(/role\s*:\s*['"]owner/);
    expect(backend).toContain("validateUser({ ...input, role: 'owner' }, true)");
    expect(backend).toContain("if (!api.needsSetup()) throw new Error('SETUP_ALREADY_COMPLETED')");
  });
  it('keeps setup public surface minimal and explicit', () => {
    expect(authorizationFor('needsSetup')?.classification).toBe('PUBLIC');
    expect(authorizationFor('setupAdmin')?.classification).toBe('PUBLIC');
    for (const method of ['settings', 'saveSettings', 'backup', 'restoreBackup', 'systemDiagnostics']) expect(authorizationFor(method)?.classification).not.toBe('PUBLIC');
  });
  it('does not review or persist Owner secrets', () => {
    const verification = wizard.slice(wizard.indexOf('function Verification'), wizard.indexOf('function Review'));
    expect(verification).not.toContain('draft.password');
    expect(verification).not.toContain('draft.securityAnswer');
    expect(wizard).not.toContain("localStorage.setItem('password'");
    expect(wizard).not.toContain("localStorage.setItem('securityAnswer'");
  });
  it('uses the existing password and recovery validation contract', () => {
    expect(() => validateUser({ username: 'owner', email: 'owner@example.test', firstName: 'A', lastName: 'B', password: '1234567', securityQuestion: 'Q', securityAnswer: 'A', role: 'owner' }, true)).toThrow('INVALID_USER');
    expect(() => validateUser({ username: 'owner', email: 'owner@example.test', firstName: 'A', lastName: 'B', password: '12345678', securityQuestion: 'Q', securityAnswer: 'A', role: 'owner' }, true)).not.toThrow();
  });
  it('protects final initialization from duplicate submission and fake readiness', () => {
    expect(wizard).toContain('if (lock.current) return');
    expect(wizard).toContain("await authService.setupAdmin");
    expect(wizard).toContain("await settingsService.save");
    expect(wizard.indexOf("setStep('ready')")).toBeGreaterThan(wizard.indexOf('await settingsService.save'));
  });
  it('supports safe preference retry after Owner creation without a second bootstrap', () => {
    expect(wizard).toContain('let owner = createdOwner');
    expect(wizard).toContain('createdOwner ? t(\'retryPreferences\')');
  });
  it('models fresh and configured installations in an isolated database', () => {
    const database = new Database(':memory:');
    try {
      database.exec(schema);
      const count = () => (database.prepare("SELECT COUNT(*) count FROM users WHERE role='owner'").get() as { count: number }).count;
      expect(count()).toBe(0);
      database.prepare("INSERT INTO users(username,email,password_hash,role,first_name,last_name,initials) VALUES('root','root@example.test','hash','owner','Primary','Owner','PO')").run();
      expect(count()).toBe(1);
    } finally { database.close(); }
  });
  it('provides tablet, portrait, keyboard and contrast foundations', () => {
    expect(css).toMatch(/min-height:\s*100dvh/);
    expect(css).toMatch(/@media\s*\(max-width:\s*700px\)/);
    expect(css).toMatch(/@media\s*\(prefers-contrast:\s*more\)/);
    expect(css).toMatch(/@media\s*\(prefers-reduced-motion:\s*reduce\)/);
    expect(wizard).toContain('heading.current?.focus()');
  });
});
