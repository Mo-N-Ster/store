import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { hasPendingRestore, markPendingRestore, recoverPendingRestore } from '../../../backend/src/domain/backup/restoreRecovery';
const roots: string[] = [];
const token = '12345678-1234-1234-1234-123456789012';
function fixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'store-restore-test-')); roots.push(root);
  const rollback = path.join(root, 'restore-staging', token, 'rollback');
  fs.mkdirSync(path.join(rollback, 'media'), { recursive: true });
  fs.writeFileSync(path.join(rollback, 'store.sqlite'), 'original-db');
  fs.writeFileSync(path.join(rollback, 'media', 'photo.jpg'), 'original-photo');
  fs.writeFileSync(path.join(root, 'store.db'), 'replacement-db');
  markPendingRestore(root, token); return { root, rollback };
}
afterEach(() => { vi.restoreAllMocks(); for (const root of roots.splice(0)) fs.rmSync(root, { recursive: true, force: true }); });
describe('Restore filesystem journal (byte-copy recovery)', () => {
  it('restores both stores and removes marker only at completion', () => {
    const { root } = fixture(); recoverPendingRestore(root);
    expect(fs.readFileSync(path.join(root, 'store.db'), 'utf8')).toBe('original-db');
    expect(fs.readFileSync(path.join(root, 'media', 'articles', 'photo.jpg'), 'utf8')).toBe('original-photo');
    expect(hasPendingRestore(root)).toBe(false);
  });
  it('preserves rollback material on media-copy failure and succeeds on retry', () => {
    const { root, rollback } = fixture(); const copy = vi.spyOn(fs, 'cpSync').mockImplementationOnce(() => { throw new Error('locked'); });
    expect(() => recoverPendingRestore(root)).toThrow('locked'); expect(hasPendingRestore(root)).toBe(true);
    expect(fs.existsSync(path.join(rollback, 'store.sqlite'))).toBe(true); copy.mockRestore(); recoverPendingRestore(root); expect(hasPendingRestore(root)).toBe(false);
  });
  it('rejects traversal tokens and concurrent journals', () => {
    const { root } = fixture(); expect(() => markPendingRestore(root, '../unsafe')).toThrow('RESTORE_RECOVERY_REQUIRED');
    expect(() => markPendingRestore(root, token)).toThrow('RESTORE_RECOVERY_REQUIRED');
  });
});
