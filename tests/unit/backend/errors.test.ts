import { describe, expect, it } from 'vitest';
import { isExpectedApplicationError, publicErrorCode } from '../../../backend/src/domain/errors';

describe('public application errors', () => {
  it('preserves an approved business error code', () => {
    expect(publicErrorCode(new Error('INSUFFICIENT_STOCK'))).toBe('INSUFFICIENT_STOCK');
    expect(isExpectedApplicationError(new Error('INSUFFICIENT_STOCK'))).toBe(true);
  });

  it('does not expose an unexpected internal error', () => {
    expect(publicErrorCode(new Error('SQLITE_IOERR: secret path'))).toBe('INTERNAL_ERROR');
    expect(isExpectedApplicationError(new Error('SQLITE_IOERR: secret path'))).toBe(false);
  });
});
