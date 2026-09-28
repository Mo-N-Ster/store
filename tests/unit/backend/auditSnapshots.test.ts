import Database from 'better-sqlite3';
import { describe, expect, it } from 'vitest';
import { schema } from '../../../backend/src/database/schema';
import { applyDatabaseMigrations } from '../../../backend/src/database/migrations';
import { auditSession, writeAudit } from '../../../backend/src/domain/system/auditWriter';
import { readAudit } from '../../../backend/src/domain/system/auditReader';
import { foldIdentity } from '../../../backend/src/domain/auth/identity';

describe('Audit context migration and identity normalization', () => {
  it('preserves old audit records without inventing responsible user or money', () => {
    const db = new Database(':memory:');
    try {
      db.exec(schema); applyDatabaseMigrations(db);
      db.prepare("INSERT INTO audit_logs(action,entity,entity_id,outcome) VALUES('old','user','1','SUCCESS')").run();
      applyDatabaseMigrations(db);
      expect(readAudit(db)[0]).toMatchObject({ responsibleId: null, responsibleName: null, cashAmount: null, cashCurrency: null });
      expect(db.pragma('integrity_check', { simple: true })).toBe('ok');
      expect(db.pragma('foreign_key_check')).toEqual([]);
    } finally { db.close(); }
  });
  it('does not substitute the business actor for the authenticated responsible account', () => {
    const db = new Database(':memory:');
    try {
      db.exec(schema); applyDatabaseMigrations(db);
      auditSession.run({ id: 22, displayName: 'Signed-in person' }, () => writeAudit(db, null, 'test', 'user', '77'));
      expect(readAudit(db)[0]).toMatchObject({ userId: null, responsibleId: 22, responsibleName: 'Signed-in person', cashAmount: null });
      expect(auditSession.getStore()).toBeUndefined();
    } finally { db.close(); }
  });
  it('normalizes Unicode case and spaces, not accent distinctions', () => {
    expect(foldIdentity('  ÉLODIE   NDIAYE ')).toBe(foldIdentity('Élodie Ndiaye'));
    expect(foldIdentity('E\u0301lodie')).toBe(foldIdentity('ÉLODIE'));
    expect(foldIdentity('Elodie')).not.toBe(foldIdentity('Élodie'));
  });
});
