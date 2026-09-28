import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import Database from 'better-sqlite3';
import { schema } from '../dist/backend/database/schema.js';
import { applyDatabaseMigrations } from '../dist/backend/database/migrations.js';
import { permissionSnapshot, savePermissionDenials } from '../dist/backend/domain/rbac/userPermissions.js';
import { defaultPermissions } from '../dist/backend/domain/rbac/permissionMatrix.js';
import { readAudit } from '../dist/backend/domain/system/auditReader.js';

// Additional real-SQLite check using the currently installed Electron ABI.
// This does NOT replace the complete Vitest/npm test gate.
const root = fs.mkdtempSync(path.join(os.tmpdir(), 'store-j6ra-native-'));
let db;
try {
  const filename = path.join(root, 'test.db');
  db = new Database(filename); db.exec(schema); applyDatabaseMigrations(db);
  const insert = db.prepare("INSERT INTO users(id,username,password_hash,role,first_name,last_name,initials) VALUES(?,?,'test-only',?,'Test',?,'T')");
  insert.run(1, 'owner', 'owner', 'Owner'); insert.run(2, 'employee', 'employee', 'Employee');
  assert.deepEqual(permissionSnapshot(db, 2).effective, [...defaultPermissions('CASHIER')].sort());
  const input = { actorId: 1, userId: 2, denied: ['POS:VALIDATE'], expectedDenied: [], expectedRole: 'employee' };
  savePermissionDenials(db, input);
  assert(!permissionSnapshot(db, 2).effective.includes('POS:VALIDATE'));
  assert.throws(() => savePermissionDenials(db, input), /CONFLICT/);
  assert.throws(() => savePermissionDenials(db, { ...input, actorId: 2 }), /FORBIDDEN/);
  assert.throws(() => savePermissionDenials(db, { ...input, userId: 1 }), /LAST_OWNER_REQUIRED/);
  assert.throws(() => savePermissionDenials(db, { ...input, denied: ['RESET:VALIDATE'] }), /VALIDATION_ERROR/);
  assert.equal(readAudit(db).length, 1);
  assert(!JSON.stringify(readAudit(db)).includes('details'));
  db.close(); db = new Database(filename); db.pragma('foreign_keys=ON');
  assert.deepEqual(permissionSnapshot(db, 2).denied, ['POS:VALIDATE']);
  assert.deepEqual(db.pragma('integrity_check'), [{ integrity_check: 'ok' }]);
  assert.deepEqual(db.pragma('foreign_key_check'), []);
  db.exec("CREATE TRIGGER fail_audit BEFORE INSERT ON audit_logs BEGIN SELECT RAISE(ABORT,'test failure'); END");
  assert.throws(() => savePermissionDenials(db, { ...input, denied: [], expectedDenied: ['POS:VALIDATE'] }));
  assert.deepEqual(permissionSnapshot(db, 2).denied, ['POS:VALIDATE']);
  console.log('J6RA_NATIVE_CHECK_PASS: persistence, owner protection, authorization, rollback, audit projection, integrity and foreign keys');
} finally {
  if (db?.open) db.close();
  fs.rmSync(root, { recursive: true, force: true });
}
