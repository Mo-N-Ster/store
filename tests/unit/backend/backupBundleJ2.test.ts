import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import Database from 'better-sqlite3';
import { afterEach, describe, expect, it } from 'vitest';
import { schema } from '../../../backend/src/database/schema';
import { BACKUP_FORMAT_VERSION, assertSafeBundlePath, createBackupBundle, inspectAndStageBackupBundle } from '../../../backend/src/domain/backup/backupBundle';
import { validateBackupDatabase } from '../../../backend/src/domain/backup/backupValidation';

const roots: string[] = [];
const temp = () => { const value = fs.mkdtempSync(path.join(os.tmpdir(), 'store-j2-backup-')); roots.push(value); return value; };
afterEach(() => { for (const root of roots.splice(0)) fs.rmSync(root, { recursive: true, force: true }); });

function fixture() {
  const root = temp(); const databasePath = path.join(root, 'store.db'); const mediaRoot = path.join(root, 'media'); fs.mkdirSync(mediaRoot);
  const imageRef = '00000000-0000-4000-8000-000000000001.jpg'; const image = Buffer.from([0xff, 0xd8, 0xff, 0xdb, 1]); fs.writeFileSync(path.join(mediaRoot, imageRef), image);
  const database = new Database(databasePath); database.exec(schema);
  database.prepare("INSERT INTO users(username,password_hash,role,first_name,last_name,initials,active) VALUES('owner','hash','owner','Primary','Owner','PO',1)").run();
  database.prepare("INSERT INTO products(name,category,price,stock_quantity,min_stock_threshold,image_ref) VALUES('A','C',10,2,0,?),('B','C',20,3,0,NULL)").run(imageRef);
  database.close(); return { root, databasePath, mediaRoot, imageRef, image };
}

describe('J.2 self-contained backup bundle', () => {
  it('round-trips database, business data, one image and one placeholder article', () => {
    const value = fixture(); const target = path.join(value.root, 'backup.store-backup');
    expect(path.resolve(value.root).startsWith(path.resolve(os.tmpdir()))).toBe(true);
    expect(path.resolve(value.root)).not.toBe(path.resolve(process.env.APPDATA || '.'));
    createBackupBundle({ databaseSnapshot: value.databasePath, mediaRoot: value.mediaRoot, imageReferences: [value.imageRef], target, applicationVersion: '2.0.1', kind: 'manual' });
    const mutated = new Database(value.databasePath);
    mutated.prepare("UPDATE products SET stock_quantity=99,image_ref=NULL WHERE name='A'").run();
    mutated.close();
    fs.rmSync(path.join(value.mediaRoot, value.imageRef));
    const staged = inspectAndStageBackupBundle(target, path.join(value.root, 'staged'));
    expect(staged.manifest.backupFormatVersion).toBe(BACKUP_FORMAT_VERSION);
    expect(staged.manifest.media).toHaveLength(1);
    expect(fs.readFileSync(path.join(staged.mediaPath, value.imageRef))).toEqual(value.image);
    const restored = new Database(staged.databasePath, { readonly: true });
    try {
      expect(validateBackupDatabase(restored)).toBe(true);
      expect(restored.prepare('SELECT name,image_ref imageRef FROM products ORDER BY name').all()).toEqual([{ name: 'A', imageRef: value.imageRef }, { name: 'B', imageRef: null }]);
      expect(restored.pragma('integrity_check')).toEqual([{ integrity_check: 'ok' }]);
      expect(restored.pragma('foreign_key_check')).toEqual([]);
    } finally { restored.close(); }
  });
  it('creates a valid new-format DB-only bundle when no article has media', () => {
    const value = fixture();
    const database = new Database(value.databasePath);
    database.prepare('UPDATE products SET image_ref=NULL').run(); database.close();
    const target = path.join(value.root, 'db-only.store-backup');
    createBackupBundle({ databaseSnapshot: value.databasePath, mediaRoot: value.mediaRoot, imageReferences: [], target, applicationVersion: '2.0.1', kind: 'auto' });
    const staged = inspectAndStageBackupBundle(target, path.join(value.root, 'db-only-stage'));
    expect(staged.manifest.media).toEqual([]);
  });
  it('rejects traversal, absolute and drive-qualified bundle paths', () => {
    for (const value of ['../store.sqlite', '/store.sqlite', 'C:/store.sqlite', 'media\\x.jpg']) expect(() => assertSafeBundlePath(value)).toThrow('INVALID_BACKUP');
  });
  it('rejects manifest tampering and missing media before staging can be committed', () => {
    const value = fixture(); const target = path.join(value.root, 'backup.store-backup');
    createBackupBundle({ databaseSnapshot: value.databasePath, mediaRoot: value.mediaRoot, imageReferences: [value.imageRef], target, applicationVersion: '2.0.1', kind: 'manual' });
    const bundle = JSON.parse(fs.readFileSync(target, 'utf8')); delete bundle.files[`media/articles/${value.imageRef}`]; fs.writeFileSync(target, JSON.stringify(bundle));
    expect(() => inspectAndStageBackupBundle(target, path.join(value.root, 'bad-stage'))).toThrow('INVALID_BACKUP');
  });
});
