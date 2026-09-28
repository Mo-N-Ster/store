import { validateArticleImage } from '../media/articleMedia.js';

export function validateUserPhoto(photo: unknown): string | null {
  if (photo === null || photo === '') return null;
  if (typeof photo !== 'string' || photo.length > 750000) throw new Error('INVALID_MEDIA');
  const match = /^data:(image\/(?:jpeg|png|webp));base64,([A-Za-z0-9+/]+={0,2})$/.exec(photo);
  if (!match) throw new Error('INVALID_MEDIA');
  const bytes = Buffer.from(match[2], 'base64');
  const kind = validateArticleImage(bytes);
  if (kind.mimeType !== match[1] || bytes.length > 512 * 1024) throw new Error('INVALID_MEDIA');
  return `data:${kind.mimeType};base64,${bytes.toString('base64')}`;
}
