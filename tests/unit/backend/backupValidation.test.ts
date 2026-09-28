import Database from 'better-sqlite3';
import { describe, expect, it } from 'vitest';
import { schema } from '../../../backend/src/database/schema';
import { validateBackupDatabase } from '../../../backend/src/domain/backup/backupValidation';

describe('backup validation', () => {
  it('accepts an integral STORE database with an active owner', () => {
    const database = new Database(':memory:');
    try {
      database.exec(schema);
      database.prepare(
        `INSERT INTO users(username,password_hash,role,first_name,last_name,initials,active)
         VALUES('owner','hash','owner','Store','Owner','SO',1)`,
      ).run();
      expect(validateBackupDatabase(database)).toBe(true);
    } finally {
      database.close();
    }
  });

  it('rejects unrelated SQLite files and databases without an active owner', () => {
    const unrelated = new Database(':memory:');
    unrelated.exec('CREATE TABLE unrelated(id INTEGER PRIMARY KEY)');
    expect(() => validateBackupDatabase(unrelated)).toThrow('INVALID_BACKUP');
    unrelated.close();

    const ownerless = new Database(':memory:');
    try {
      ownerless.exec(schema);
      expect(() => validateBackupDatabase(ownerless)).toThrow('INVALID_BACKUP');
    } finally {
      ownerless.close();
    }
  });
});
