import type Database from 'better-sqlite3';

const requiredTables = ['users', 'products', 'invoices', 'settings'] as const;

export function validateBackupDatabase(database: Database.Database) {
  const integrity = database.pragma('integrity_check') as { integrity_check: string }[];
  if (integrity.length !== 1 || integrity[0]?.integrity_check !== 'ok')
    throw new Error('INVALID_BACKUP');
  const tables = database
    .prepare("SELECT name FROM sqlite_master WHERE type='table'")
    .all() as { name: string }[];
  const names = new Set(tables.map(({ name }) => name));
  if (requiredTables.some((table) => !names.has(table))) throw new Error('INVALID_BACKUP');
  const owner = database
    .prepare("SELECT 1 FROM users WHERE role='owner' AND active=1 LIMIT 1")
    .get();
  if (!owner) throw new Error('INVALID_BACKUP');
  return true;
}
