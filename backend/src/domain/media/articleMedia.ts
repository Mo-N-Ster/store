import fs from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

export const MAX_ARTICLE_IMAGE_BYTES = 5 * 1024 * 1024;
export const ARTICLE_MEDIA_REF = /^[a-f0-9-]{36}\.(?:jpg|png|webp)$/;

export type ArticleImageKind = { extension: 'jpg' | 'png' | 'webp'; mimeType: 'image/jpeg' | 'image/png' | 'image/webp' };

export function detectArticleImage(buffer: Buffer): ArticleImageKind | null {
  if (buffer.length >= 3 && buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff)
    return { extension: 'jpg', mimeType: 'image/jpeg' };
  if (buffer.length >= 8 && buffer.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])))
    return { extension: 'png', mimeType: 'image/png' };
  if (buffer.length >= 12 && buffer.subarray(0, 4).toString('ascii') === 'RIFF' && buffer.subarray(8, 12).toString('ascii') === 'WEBP')
    return { extension: 'webp', mimeType: 'image/webp' };
  return null;
}

export function validateArticleImage(buffer: Buffer) {
  if (!buffer.length) throw new Error('INVALID_MEDIA');
  if (buffer.length > MAX_ARTICLE_IMAGE_BYTES) throw new Error('MEDIA_TOO_LARGE');
  const kind = detectArticleImage(buffer);
  if (!kind) throw new Error('INVALID_MEDIA');
  return kind;
}

export function assertArticleMediaRef(reference: string) {
  if (!ARTICLE_MEDIA_REF.test(reference) || path.basename(reference) !== reference)
    throw new Error('INVALID_MEDIA');
  return reference;
}

export function managedArticlePath(mediaRoot: string, reference: string, staging = false) {
  assertArticleMediaRef(reference);
  const root = path.resolve(mediaRoot);
  // Reject redirected directories and file links before any managed deletion.
  for (const directory of [path.dirname(root), root, ...(staging ? [path.join(root, '.staging')] : [])]) {
    if (fs.existsSync(directory) && fs.lstatSync(directory).isSymbolicLink()) throw new Error('INVALID_MEDIA');
  }
  const target = path.resolve(root, ...(staging ? ['.staging'] : []), reference);
  if (!target.startsWith(`${root}${path.sep}`)) throw new Error('INVALID_MEDIA');
  if (fs.existsSync(target) && fs.lstatSync(target).isSymbolicLink()) throw new Error('INVALID_MEDIA');
  return target;
}

export function stageArticleImage(sourcePath: string, mediaRoot: string) {
  const buffer = fs.readFileSync(sourcePath);
  const kind = validateArticleImage(buffer);
  const token = `${randomUUID()}.${kind.extension}`;
  const staging = path.join(mediaRoot, '.staging');
  fs.mkdirSync(staging, { recursive: true });
  fs.writeFileSync(managedArticlePath(mediaRoot, token, true), buffer, { flag: 'wx' });
  return { token, mimeType: kind.mimeType, previewDataUrl: `data:${kind.mimeType};base64,${buffer.toString('base64')}` };
}

export function commitStagedArticleImage(token: string, mediaRoot: string) {
  assertArticleMediaRef(token);
  const staged = managedArticlePath(mediaRoot, token, true);
  const target = managedArticlePath(mediaRoot, token);
  const buffer = fs.readFileSync(staged);
  validateArticleImage(buffer);
  fs.mkdirSync(mediaRoot, { recursive: true });
  fs.copyFileSync(staged, target, fs.constants.COPYFILE_EXCL);
  // Flush the imported copy before any database reference can be committed.
  try {
    const descriptor = fs.openSync(target, 'r+');
    try { fs.fsyncSync(descriptor); } finally { fs.closeSync(descriptor); }
    if (!fs.readFileSync(target).equals(buffer)) throw new Error('INVALID_MEDIA');
  } catch {
    // The database has not been touched. Keep this copy as a recoverable orphan;
    // the read-only integrity inventory can identify it without deleting data.
    throw new Error('INVALID_MEDIA');
  }
  return { reference: token, staged, target };
}

/** Read-only inventory: never repairs references or deletes orphaned files. */
export function inspectArticleMedia(references: string[], mediaRoot: string) {
  const referenced = new Set(references);
  const missing = references.filter((reference) => {
    try { assertArticleMediaRef(reference); return !fs.statSync(path.join(mediaRoot, reference)).isFile(); }
    catch { return true; }
  });
  const recoverableOrphans = fs.existsSync(mediaRoot)
    ? fs.readdirSync(mediaRoot).filter((name) => ARTICLE_MEDIA_REF.test(name) && !referenced.has(name))
    : [];
  return { missing, recoverableOrphans };
}

export function articleImageDataUrl(reference: string, mediaRoot: string) {
  try {
    assertArticleMediaRef(reference);
    const buffer = fs.readFileSync(path.join(mediaRoot, reference));
    const kind = validateArticleImage(buffer);
    return `data:${kind.mimeType};base64,${buffer.toString('base64')}`;
  } catch {
    return null;
  }
}

export function cleanupStaleArticleStaging(mediaRoot: string, now = Date.now()) {
  const staging = path.join(mediaRoot, '.staging');
  if (!fs.existsSync(staging)) return;
  for (const name of fs.readdirSync(staging)) {
    const target = path.join(staging, name);
    if (!ARTICLE_MEDIA_REF.test(name) || now - fs.statSync(target).mtimeMs > 24 * 60 * 60 * 1000)
      fs.rmSync(managedArticlePath(mediaRoot, name, true), { force: true });
  }
}
