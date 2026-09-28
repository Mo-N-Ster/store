import { describe, expect, it } from 'vitest';
import { validateUserPhoto } from '../../../backend/src/domain/user/userPhoto';

describe('local employee photos', () => {
  const png = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=';
  it('accepts a portable thumbnail and explicit removal', () => {
    expect(validateUserPhoto(png)).toBe(png);
    expect(validateUserPhoto(null)).toBeNull();
  });
  it('rejects external paths, SVG and mismatched media types', () => {
    for (const value of ['https://example.com/photo.png', 'C:\\photo.png', 'data:image/svg+xml;base64,PHN2Zz4=', png.replace('image/png', 'image/jpeg')])
      expect(() => validateUserPhoto(value)).toThrow('INVALID_MEDIA');
  });
  it('bounds database storage', () => {
    const bytes = Buffer.alloc(513 * 1024);
    Buffer.from('89504e470d0a1a0a', 'hex').copy(bytes);
    expect(() => validateUserPhoto(`data:image/png;base64,${bytes.toString('base64')}`)).toThrow('INVALID_MEDIA');
  });
});
