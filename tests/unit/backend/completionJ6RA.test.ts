import Database from 'better-sqlite3';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { schema } from '../../../backend/src/database/schema';
import { applyDatabaseMigrations, latestSchemaVersion } from '../../../backend/src/database/migrations';
import { permissionSnapshot, savePermissionDenials } from '../../../backend/src/domain/rbac/userPermissions';
import { defaultPermissions } from '../../../backend/src/domain/rbac/permissionMatrix';
import { isIpcAuthorized } from '../../../backend/src/domain/rbac/ipcPermissions';
import { validateSettings } from '../../../backend/src/domain/system/settingsPolicy';
import { readAudit } from '../../../backend/src/domain/system/auditReader';

describe('J.6R-A isolated permission persistence and audit', () => {
  let db: Database.Database;
  let directory: string;
  beforeEach(() => {
    directory = fs.mkdtempSync(path.join(os.tmpdir(), 'store-j6ra-'));
    db = new Database(path.join(directory, 'test.db')); db.exec(schema); applyDatabaseMigrations(db);
    const insert = db.prepare("INSERT INTO users(id,username,password_hash,role,first_name,last_name,initials) VALUES(?,?,'test-only',?,'Test',?,'T')");
    insert.run(1, 'owner', 'owner', 'Owner'); insert.run(2, 'manager', 'manager', 'Manager'); insert.run(3, 'employee', 'employee', 'Employee');
  });
  afterEach(() => { if (db?.open) db.close(); if (directory) fs.rmSync(directory, { recursive: true, force: true }); });
  const update = (denied = ['POS:VALIDATE']) => ({ actorId: 1, userId: 3, denied, expectedDenied: [], expectedRole: 'employee' });
  it('preserves every role default after additive migration', () => {
    for (const [id, role] of [[1, 'ADMIN'], [2, 'MANAGER'], [3, 'CASHIER']] as const)
      expect(permissionSnapshot(db, id).effective).toEqual([...defaultPermissions(role)].sort());
    expect(db.prepare('SELECT MAX(version) v FROM schema_migrations').get()).toEqual({ v: latestSchemaVersion });
  });
  it('persists denials, follows them in backend authorization and survives restart', () => {
    savePermissionDenials(db, update()); db.close(); db = new Database(path.join(directory, 'test.db'));
    const snapshot = permissionSnapshot(db, 3);
    expect(snapshot.denied).toEqual(['POS:VALIDATE']);
    expect(isIpcAuthorized('createInvoice', new Set(snapshot.effective))).toBe(false);
    expect(snapshot.inherited).toContain('POS:VALIDATE');
  });
  it('restores inherited rights without adding grants', () => {
    savePermissionDenials(db, update());
    const result = savePermissionDenials(db, { ...update([]), expectedDenied: ['POS:VALIDATE'] });
    expect(result.effective).toContain('POS:VALIDATE');
  });
  it.each(['UNKNOWN:READ', 'POS:EXECUTE', 'RESET:VALIDATE'])('rejects unsupported or non-inherited permission %s', (code) => {
    expect(() => savePermissionDenials(db, update([code]))).toThrow('VALIDATION_ERROR');
    expect(permissionSnapshot(db, 3).denied).toEqual([]);
  });
  it('rejects unauthorized actor and owner target', () => {
    expect(() => savePermissionDenials(db, { ...update(), actorId: 2 })).toThrow('FORBIDDEN');
    expect(() => savePermissionDenials(db, { ...update(), userId: 1 })).toThrow('LAST_OWNER_REQUIRED');
    expect(isIpcAuthorized('saveUserPermissions', defaultPermissions('MANAGER'))).toBe(false);
  });
  it('rejects stale edits atomically', () => {
    savePermissionDenials(db, update());
    expect(() => savePermissionDenials(db, update(['PRODUCTS:READ']))).toThrow('CONFLICT');
    expect(permissionSnapshot(db, 3).denied).toEqual(['POS:VALIDATE']);
  });
  it('rolls back restrictions when audit fails', () => {
    db.exec("CREATE TRIGGER fail_audit BEFORE INSERT ON audit_logs BEGIN SELECT RAISE(ABORT,'test failure'); END");
    expect(() => savePermissionDenials(db, update())).toThrow();
    expect(permissionSnapshot(db, 3).denied).toEqual([]);
  });
  it('keeps foreign keys and SQLite integrity valid', () => {
    savePermissionDenials(db, update());
    expect(db.pragma('integrity_check')).toEqual([{ integrity_check: 'ok' }]);
    expect(db.pragma('foreign_key_check')).toEqual([]);
    expect(() => db.prepare('INSERT INTO user_permission_denials VALUES(999,1)').run()).toThrow();
  });
  it('upgrades a representative version 15 database without losing records', () => {
    db.exec('DROP TABLE user_permission_denials; DELETE FROM schema_migrations WHERE version=16');
    applyDatabaseMigrations(db); applyDatabaseMigrations(db);
    expect(permissionSnapshot(db, 3).denied).toEqual([]);
    expect(db.prepare('SELECT COUNT(*) n FROM users').get()).toEqual({ n: 3 });
  });
  it('returns only bounded deterministic non-secret audit fields and filters them', () => {
    const insert = db.prepare("INSERT INTO audit_logs(user_id,action,entity,entity_id,details) VALUES(1,'test','user','3','secret must not escape')");
    for (let i = 0; i < 102; i++) insert.run();
    const rows = readAudit(db, { action: 'test', userId: 1 }) as { id: number }[];
    expect(rows).toHaveLength(100); expect(rows[0].id).toBe(102);
    expect(JSON.stringify(rows)).not.toContain('secret');
    expect(readAudit(db, { beforeId: rows.at(-1)!.id })).toHaveLength(2);
    expect(readAudit(db, { action: "' OR 1=1 --" })).toEqual([]);
    expect(() => readAudit(db, { from: 'bad' })).toThrow('VALIDATION_ERROR');
  });
  it('keeps audit inaccessible to non-administrators', () => {
    expect(isIpcAuthorized('auditLogs', defaultPermissions('CASHIER'))).toBe(false);
    expect(isIpcAuthorized('auditLogs', defaultPermissions('ADMIN'))).toBe(true);
  });
});

describe('J.6R-A settings validation', () => {
  it('accepts current shop and SMTP workflow values', () => {
    expect(() => validateSettings({ storeName: 'STORE', currency: 'XAF', discountsEnabled: 'false', smtpHost: 'smtp.example.test', smtpPort: '587', smtpSecure: 'false', smtpFrom: 'shop@example.test', smtpPassword: 'test-only' })).not.toThrow();
  });
  it.each([{ arbitrary: 'value' }, { smtpPasswordEncrypted: 'injected' }, { currency: 'USD' }, { discountsEnabled: 'yes' }, { smtpPort: '0' }, { smtpPort: '65536' }, { smtpHost: 'https://host/path' }, { email: 'bad' }, { storeName: '' }, { address: 'header\ninjection' }])('rejects invalid setting %j', (value) => {
    expect(() => validateSettings(value)).toThrow('VALIDATION_ERROR');
  });
});
