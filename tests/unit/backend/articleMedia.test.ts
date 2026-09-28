import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { MAX_ARTICLE_IMAGE_BYTES, assertArticleMediaRef, articleImageDataUrl, commitStagedArticleImage, detectArticleImage, managedArticlePath, stageArticleImage, validateArticleImage } from '../../../backend/src/domain/media/articleMedia';

const temporary: string[] = [];
const temp = () => { const value = fs.mkdtempSync(path.join(os.tmpdir(), 'store-j2-media-')); temporary.push(value); return value; };
afterEach(() => { for (const directory of temporary.splice(0)) fs.rmSync(directory, { recursive: true, force: true }); });
const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0xdb, 0, 1]);
const png = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0]);
const webp = Buffer.concat([Buffer.from('RIFF0000WEBP', 'ascii'), Buffer.from([1])]);

describe('J.2 managed article media', () => {
  it('rejects unmanaged cleanup paths and preserves the original source', () => {
    const root = temp();
    for (const ref of ['../source.png', 'C:\\source.png', '/source.png', 'store.db', '.staging'])
      expect(() => managedArticlePath(root, ref)).toThrow('INVALID_MEDIA');
    const source = path.join(root, 'original.png'); fs.writeFileSync(source, png);
    const media = path.join(root, 'media'); const staged = stageArticleImage(source, media);
    commitStagedArticleImage(staged.token, media);
    expect(fs.readFileSync(source)).toEqual(png);
  });
  it('accepts JPEG, PNG and WebP by signature rather than extension', () => {
    expect(detectArticleImage(jpeg)?.mimeType).toBe('image/jpeg');
    expect(detectArticleImage(png)?.mimeType).toBe('image/png');
    expect(detectArticleImage(webp)?.mimeType).toBe('image/webp');
  });
  it('rejects unsupported and oversized content', () => {
    expect(() => validateArticleImage(Buffer.from('not an image'))).toThrow('INVALID_MEDIA');
    expect(() => validateArticleImage(Buffer.concat([jpeg, Buffer.alloc(MAX_ARTICLE_IMAGE_BYTES)]))).toThrow('MEDIA_TOO_LARGE');
  });
  it('generates safe managed names and never preserves the source name', () => {
    const root = temp(); const source = path.join(root, '.. unsafe name.jpeg'); fs.writeFileSync(source, jpeg);
    const selected = stageArticleImage(source, path.join(root, 'media'));
    expect(selected.token).toMatch(/^[a-f0-9-]{36}\.jpg$/);
    expect(selected.token).not.toContain('unsafe');
    const committed = commitStagedArticleImage(selected.token, path.join(root, 'media'));
    expect(fs.readFileSync(committed.target)).toEqual(jpeg);
  });
  it('rejects invalid references and returns a placeholder-compatible null for missing media', () => {
    for (const value of ['../x.jpg', 'C:/x.jpg', 'x.gif', '/x.png']) expect(() => assertArticleMediaRef(value)).toThrow('INVALID_MEDIA');
    expect(articleImageDataUrl('00000000-0000-0000-0000-000000000000.jpg', temp())).toBeNull();
  });
});
