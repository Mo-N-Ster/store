import { describe, expect, it } from 'vitest';
import { storageHealth } from '../../../backend/src/domain/system/storagePolicy';

describe('storage health policy', () => {
  it('warns before the disk is too full for safe backups', () => {
    expect(storageHealth(null)).toBe('unknown');
    expect(storageHealth(600 * 1024 * 1024)).toBe('healthy');
    expect(storageHealth(300 * 1024 * 1024)).toBe('warning');
    expect(storageHealth(100 * 1024 * 1024)).toBe('critical');
  });
});
