import Database from 'better-sqlite3';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { resetWithMedia, recoverReset } from '../../../backend/src/domain/system/resetRecovery';

const roots: string[] = [];
const databases: Database.Database[] = [];
function fixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'store-reset-test-')); roots.push(root);
  const db = new Database(':memory:'); databases.push(db);
  db.exec("CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT NOT NULL); CREATE TABLE items(id INTEGER); INSERT INTO items VALUES(1)");
  const media = path.join(root, 'media', 'articles'); fs.mkdirSync(media, { recursive: true }); fs.writeFileSync(path.join(media, 'photo.jpg'), 'original');
  return { root, db, media };
}
afterEach(() => { vi.restoreAllMocks(); for (const db of databases.splice(0)) db.close(); for (const root of roots.splice(0)) fs.rmSync(root, { recursive: true, force: true }); });
describe('Reset DB/media recovery', () => {
  it('commits the empty database and empty media together', () => {
    const { db, root, media } = fixture(); resetWithMedia(db, root, () => { db.exec('DELETE FROM items; DELETE FROM settings'); });
    expect(db.prepare('SELECT * FROM items').all()).toEqual([]); expect(fs.readdirSync(media)).toEqual([]);
    expect(fs.existsSync(path.join(root, '.store-reset-recovery'))).toBe(false);
    expect(db.pragma('integrity_check', { simple: true })).toBe('ok');
  });
  it('restores SQL and original media after a transaction failure', () => {
    const { db, root, media } = fixture();
    expect(() => resetWithMedia(db, root, () => { db.exec('DELETE FROM items'); throw new Error('injected'); })).toThrow('injected');
    expect(db.prepare('SELECT * FROM items').all()).toHaveLength(1); expect(fs.readFileSync(path.join(media, 'photo.jpg'), 'utf8')).toBe('original');
  });
  it('leaves the database intact when media staging fails', () => {
    const { db, root, media } = fixture(); vi.spyOn(fs, 'renameSync').mockImplementationOnce(() => { throw new Error('locked'); });
    expect(() => resetWithMedia(db, root, () => db.exec('DELETE FROM items'))).toThrow('locked');
    expect(db.prepare('SELECT * FROM items').all()).toHaveLength(1); expect(fs.existsSync(path.join(media, 'photo.jpg'))).toBe(true);
  });
  it('recovers an interrupted pre-commit stage on startup', () => {
    const { db, root, media } = fixture(); const journal = path.join(root, '.store-reset-recovery'); fs.mkdirSync(journal);
    fs.writeFileSync(path.join(journal, 'token'), '12345678-1234-1234-1234-123456789012'); fs.renameSync(media, path.join(journal, 'media')); fs.mkdirSync(media);
    recoverReset(db, root); expect(fs.readFileSync(path.join(media, 'photo.jpg'), 'utf8')).toBe('original'); expect(db.prepare('SELECT * FROM items').all()).toHaveLength(1);
  });
  it('defers locked cleanup after commit without returning a false failure', () => {
    const { db, root, media } = fixture(); const remove = vi.spyOn(fs, 'rmSync').mockImplementationOnce(() => { throw new Error('locked'); });
    expect(() => resetWithMedia(db, root, () => db.exec('DELETE FROM items'))).not.toThrow(); expect(fs.readdirSync(media)).toEqual([]);
    expect(fs.existsSync(path.join(root, '.store-reset-recovery'))).toBe(true); remove.mockRestore(); recoverReset(db, root);
    expect(fs.existsSync(path.join(root, '.store-reset-recovery'))).toBe(false); expect(db.prepare('SELECT * FROM items').all()).toEqual([]);
  });
});
