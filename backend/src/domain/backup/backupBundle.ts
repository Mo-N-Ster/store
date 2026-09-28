import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { ARTICLE_MEDIA_REF, assertArticleMediaRef, validateArticleImage } from '../media/articleMedia.js';

export const BACKUP_FORMAT_VERSION = 2;
export const MAX_BACKUP_BYTES = 512 * 1024 * 1024;
const sha256 = (value: Buffer) => createHash('sha256').update(value).digest('hex');

type Entry = { path: string; size: number; sha256: string };
export type BackupManifest = {
  format: 'STORE_BACKUP';
  backupFormatVersion: number;
  applicationVersion: string;
  createdAt: string;
  kind: string;
  database: Entry;
  media: Array<Entry & { reference: string }>;
};
type Bundle = { manifest: BackupManifest; files: Record<string, string> };

export function assertSafeBundlePath(value: string) {
  if (!value || value.includes('\\') || value.startsWith('/') || /^[A-Za-z]:/.test(value))
    throw new Error('INVALID_BACKUP');
  const normalized = path.posix.normalize(value);
  if (normalized !== value || normalized.split('/').includes('..')) throw new Error('INVALID_BACKUP');
  return value;
}

export function createBackupBundle(input: { databaseSnapshot: string; mediaRoot: string; imageReferences: string[]; target: string; applicationVersion: string; kind: string }) {
  const database = fs.readFileSync(input.databaseSnapshot);
  const databasePath = 'store.sqlite';
  const files: Record<string, string> = { [databasePath]: database.toString('base64') };
  const media: BackupManifest['media'] = [];
  for (const reference of [...new Set(input.imageReferences)].sort()) {
    assertArticleMediaRef(reference);
    const buffer = fs.readFileSync(path.join(input.mediaRoot, reference));
    validateArticleImage(buffer);
    const entryPath = `media/articles/${reference}`;
    files[entryPath] = buffer.toString('base64');
    media.push({ path: entryPath, reference, size: buffer.length, sha256: sha256(buffer) });
  }
  const manifest: BackupManifest = {
    format: 'STORE_BACKUP', backupFormatVersion: BACKUP_FORMAT_VERSION,
    applicationVersion: input.applicationVersion, createdAt: new Date().toISOString(), kind: input.kind,
    database: { path: databasePath, size: database.length, sha256: sha256(database) }, media,
  };
  files['manifest.json'] = Buffer.from(JSON.stringify(manifest), 'utf8').toString('base64');
  const temporary = `${input.target}.tmp`;
  fs.writeFileSync(temporary, JSON.stringify({ manifest, files } satisfies Bundle), { flag: 'wx' });
  fs.renameSync(temporary, input.target);
  return input.target;
}

export function inspectAndStageBackupBundle(bundlePath: string, stagingRoot: string) {
  const stat = fs.statSync(bundlePath);
  if (stat.size > MAX_BACKUP_BYTES) throw new Error('INVALID_BACKUP');
  let bundle: Bundle;
  try { bundle = JSON.parse(fs.readFileSync(bundlePath, 'utf8')) as Bundle; }
  catch { throw new Error('INVALID_BACKUP'); }
  const { manifest, files } = bundle;
  if (manifest?.format !== 'STORE_BACKUP' || manifest.backupFormatVersion !== BACKUP_FORMAT_VERSION || !files || typeof files !== 'object')
    throw new Error('INVALID_BACKUP');
  try {
    const embeddedManifest = JSON.parse(Buffer.from(files['manifest.json'], 'base64').toString('utf8'));
    if (JSON.stringify(embeddedManifest) !== JSON.stringify(manifest)) throw new Error('INVALID_BACKUP');
  } catch { throw new Error('INVALID_BACKUP'); }
  const entries: Array<Entry & { reference?: string }> = [manifest.database, ...manifest.media];
  if (manifest.database.path !== 'store.sqlite') throw new Error('INVALID_BACKUP');
  fs.mkdirSync(stagingRoot, { recursive: true });
  for (const entry of entries) {
    assertSafeBundlePath(entry.path);
    if (entry.reference) {
      assertArticleMediaRef(entry.reference);
      if (entry.path !== `media/articles/${entry.reference}`) throw new Error('INVALID_BACKUP');
    }
    const encoded = files[entry.path];
    if (typeof encoded !== 'string') throw new Error('INVALID_BACKUP');
    const buffer = Buffer.from(encoded, 'base64');
    if (buffer.length !== entry.size || sha256(buffer) !== entry.sha256) throw new Error('INVALID_BACKUP');
    if (entry.reference) validateArticleImage(buffer);
    const destination = path.join(stagingRoot, ...entry.path.split('/'));
    const resolved = path.resolve(destination);
    if (!resolved.startsWith(`${path.resolve(stagingRoot)}${path.sep}`)) throw new Error('INVALID_BACKUP');
    fs.mkdirSync(path.dirname(destination), { recursive: true });
    fs.writeFileSync(destination, buffer, { flag: 'wx' });
  }
  if (Object.keys(files).some((entry) => entry !== 'manifest.json' && !entries.some(({ path: expected }) => expected === entry)))
    throw new Error('INVALID_BACKUP');
  return { manifest, databasePath: path.join(stagingRoot, 'store.sqlite'), mediaPath: path.join(stagingRoot, 'media', 'articles') };
}

export function mediaReferencesFromManifest(manifest: BackupManifest) {
  return manifest.media.map(({ reference }) => reference).filter((reference) => ARTICLE_MEDIA_REF.test(reference));
}
