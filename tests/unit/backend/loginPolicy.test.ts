import { describe, expect, it } from 'vitest';
import { isLoginLocked, loginPolicy, nextFailedLogin } from '../../../backend/src/domain/auth/loginPolicy';

describe('login security policy', () => {
  it('uses bounded configurable values', () => {
    expect(loginPolicy({})).toEqual({ maxAttempts: 5, lockMinutes: 15 });
    expect(loginPolicy({ authMaxAttempts: '1', authLockMinutes: '9000' })).toEqual({
      maxAttempts: 3,
      lockMinutes: 1_440,
    });
  });

  it('locks the account when the configured threshold is reached', () => {
    const currentTime = new Date('2026-09-23T10:00:00.000Z');
    expect(nextFailedLogin(3, { maxAttempts: 5, lockMinutes: 15 }, currentTime)).toEqual({
      failedAttempts: 4,
      lockedUntil: null,
    });
    const locked = nextFailedLogin(4, { maxAttempts: 5, lockMinutes: 15 }, currentTime);
    expect(locked.failedAttempts).toBe(5);
    expect(locked.lockedUntil).toBe('2026-09-23T10:15:00.000Z');
    expect(isLoginLocked(locked.lockedUntil, currentTime)).toBe(true);
  });

  it('treats expired and malformed locks as inactive', () => {
    const currentTime = new Date('2026-09-23T10:00:00.000Z');
    expect(isLoginLocked('2026-09-23T09:59:59.000Z', currentTime)).toBe(false);
    expect(isLoginLocked('invalid', currentTime)).toBe(false);
  });
});
