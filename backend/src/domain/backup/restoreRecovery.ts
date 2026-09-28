import fs from 'node:fs';
import path from 'node:path';

const markerName = '.store-restore-pending';
export function hasPendingRestore(profile: string) { return fs.existsSync(path.join(profile, markerName)); }
export function markPendingRestore(profile: string, token: string) {
  if (!/^[a-f0-9-]{36}$/.test(token) || hasPendingRestore(profile)) throw new Error('RESTORE_RECOVERY_REQUIRED');
  const pending = path.join(profile, markerName);
  const temporary = `${pending}.tmp`;
  const fd = fs.openSync(temporary, 'w');
  try { fs.writeFileSync(fd, token); fs.fsyncSync(fd); } finally { fs.closeSync(fd); }
  fs.renameSync(temporary, pending);
}
export function finishPendingRestore(profile: string) { fs.unlinkSync(path.join(profile, markerName)); }
/** Called before SQLite is opened. Keep snapshot and marker until BOTH stores recover. */
export function recoverPendingRestore(profile: string) {
  if (!hasPendingRestore(profile)) return;
  const token = fs.readFileSync(path.join(profile, markerName), 'utf8');
  if (!/^[a-f0-9-]{36}$/.test(token)) throw new Error('RESTORE_RECOVERY_REQUIRED');
  const rollback = path.join(profile, 'restore-staging', token, 'rollback');
  const snapshot = path.join(rollback, 'store.sqlite');
  if (!fs.existsSync(snapshot)) throw new Error('RESTORE_RECOVERY_REQUIRED');
  const database = path.join(profile, 'store.db');
  for (const suffix of ['-wal', '-shm']) fs.rmSync(`${database}${suffix}`, { force: true });
  fs.copyFileSync(snapshot, database);
  const media = path.join(profile, 'media', 'articles');
  fs.rmSync(media, { recursive: true, force: true });
  if (fs.existsSync(path.join(rollback, 'media'))) fs.cpSync(path.join(rollback, 'media'), media, { recursive: true });
  else fs.mkdirSync(media, { recursive: true });
  finishPendingRestore(profile);
}
