import Database from 'better-sqlite3';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { schema } from '../../../backend/src/database/schema';
import { validateBackupDatabase } from '../../../backend/src/domain/backup/backupValidation';

const directories: string[] = [];
const temporaryDirectory = () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'store-release-reliability-'));
  directories.push(directory);
  return directory;
};
const insertOwner = (database: Database.Database) => database.prepare(`INSERT INTO users(
  username,email,password_hash,role,first_name,last_name,initials,active
) VALUES(?,?,?,?,?,?,?,1)`).run('owner', 'owner@example.test', 'test-hash', 'owner', 'Primary', 'Owner', 'PO');

afterEach(() => {
  for (const directory of directories.splice(0)) fs.rmSync(directory, { recursive: true, force: true });
});

describe('Phase I isolated release reliability', () => {
  it('round-trips a STORE backup without losing business data or settings', async () => {
    const directory = temporaryDirectory();
    const activeFile = path.join(directory, 'store.db');
    const backupFile = path.join(directory, 'manual-store.db');
    const restoredFile = path.join(directory, 'restored.db');
    const active = new Database(activeFile);
    active.pragma('journal_mode = WAL');
    active.pragma('synchronous = FULL');
    active.pragma('foreign_keys = ON');
    active.exec(schema);
    insertOwner(active);
    active.prepare("INSERT INTO settings(key,value) VALUES('storeName','State A')").run();
    active.prepare("INSERT INTO products(reference,name,category,price,stock_quantity,min_stock_threshold) VALUES('REL-1','Release item','Test',12,7,1)").run();
    await active.backup(backupFile);
    active.prepare("UPDATE products SET stock_quantity=2 WHERE reference='REL-1'").run();
    active.prepare("UPDATE settings SET value='State B' WHERE key='storeName'").run();
    active.close();
    fs.copyFileSync(backupFile, restoredFile);
    const restored = new Database(restoredFile, { readonly: true, fileMustExist: true });
    try {
      expect(validateBackupDatabase(restored)).toBe(true);
      expect(restored.pragma('integrity_check')).toEqual([{ integrity_check: 'ok' }]);
      expect(restored.prepare("SELECT stock_quantity stock FROM products WHERE reference='REL-1'").get()).toEqual({ stock: 7 });
      expect(restored.prepare("SELECT value FROM settings WHERE key='storeName'").get()).toEqual({ value: 'State A' });
      expect(restored.prepare("SELECT active FROM users WHERE role='owner'").get()).toEqual({ active: 1 });
    } finally { restored.close(); }
  });

  it('leaves a deterministic degraded state when Owner commit succeeds but preferences do not', () => {
    const filename = path.join(temporaryDirectory(), 'partial-setup.db');
    const writer = new Database(filename);
    writer.exec(schema);
    insertOwner(writer);
    writer.close();
    const restarted = new Database(filename, { readonly: true });
    try {
      expect(restarted.prepare("SELECT COUNT(*) count FROM users WHERE role='owner'").get()).toEqual({ count: 1 });
      expect(restarted.prepare("SELECT value FROM settings WHERE key='storeName'").get()).toBeUndefined();
      expect(restarted.pragma('integrity_check')).toEqual([{ integrity_check: 'ok' }]);
    } finally { restarted.close(); }
  });

  it('rejects a damaged restore candidate without changing the active database', () => {
    const directory = temporaryDirectory();
    const active = new Database(path.join(directory, 'active.db'));
    active.exec(schema);
    insertOwner(active);
    active.prepare("INSERT INTO settings(key,value) VALUES('storeName','Protected')").run();
    const invalid = new Database(path.join(directory, 'invalid.db'));
    invalid.exec('CREATE TABLE unrelated(id INTEGER PRIMARY KEY)');
    expect(() => validateBackupDatabase(invalid)).toThrow('INVALID_BACKUP');
    invalid.close();
    expect(active.prepare("SELECT value FROM settings WHERE key='storeName'").get()).toEqual({ value: 'Protected' });
    active.close();
  });
});
