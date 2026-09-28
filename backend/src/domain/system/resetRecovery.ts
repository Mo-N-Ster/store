import fs from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import type Database from 'better-sqlite3';

const marker = '_resetCommitToken';
/** The SQL marker commits with data deletion; a durable journal owns staged media. */
export function recoverReset(database: Database.Database, profile: string) {
  const journal = path.join(profile, '.store-reset-recovery');
  if (!fs.existsSync(journal)) return;
  const staged = path.join(journal, 'media');
  if (!fs.existsSync(path.join(journal, 'token')) && !fs.existsSync(staged)) {
    fs.rmdirSync(journal);
    return;
  }
  const token = fs.readFileSync(path.join(journal, 'token'), 'utf8');
  if (!/^[a-f0-9-]{36}$/.test(token)) throw new Error('RESET_RECOVERY_REQUIRED');
  const committed = database.prepare('SELECT value FROM settings WHERE key=?').get(marker) as { value: string } | undefined;
  const media = path.join(profile, 'media', 'articles');
  if (committed?.value !== token && fs.existsSync(staged)) {
    // Before SQL commit, restore original media. The replacement is still empty.
    if (fs.existsSync(media)) fs.rmdirSync(media);
    fs.renameSync(staged, media);
  }
  // Keep the token until staged content is gone, including on partial deletion failure.
  fs.rmSync(staged, { recursive: true, force: true });
  fs.unlinkSync(path.join(journal, 'token'));
  fs.rmdirSync(journal);
  if (committed?.value === token) database.prepare('DELETE FROM settings WHERE key=?').run(marker);
}

export function resetWithMedia(database: Database.Database, profile: string, clearData: () => void) {
  recoverReset(database, profile);
  const journal = path.join(profile, '.store-reset-recovery');
  const media = path.join(profile, 'media', 'articles');
  const token = randomUUID();
  fs.mkdirSync(journal);
  const fd = fs.openSync(path.join(journal, 'token'), 'wx');
  try { fs.writeFileSync(fd, token); fs.fsyncSync(fd); } finally { fs.closeSync(fd); }
  let committed = false;
  try {
    if (fs.existsSync(media)) fs.renameSync(media, path.join(journal, 'media'));
    fs.mkdirSync(media, { recursive: true });
    database.transaction(() => {
      clearData();
      database.prepare('INSERT OR REPLACE INTO settings(key,value) VALUES(?,?)').run(marker, token);
    })();
    committed = true;
  } finally {
    // Never report a committed reset as failed merely because deferred cleanup is locked.
    // A pending pre-commit recovery, however, must block and retain its journal.
    if (committed) { try { recoverReset(database, profile); } catch { /* recover at next startup */ } }
    else recoverReset(database, profile);
  }
}
