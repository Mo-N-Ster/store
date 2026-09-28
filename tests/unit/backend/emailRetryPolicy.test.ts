import { describe, expect, it } from 'vitest';
import { nextEmailRetry } from '../../../backend/src/domain/email/emailRetryPolicy';

describe('offline email retry policy', () => {
  const currentTime = new Date('2026-09-23T10:00:00.000Z');

  it('uses progressive delays and stops after five network failures', () => {
    expect(nextEmailRetry(0, 'ECONNREFUSED', currentTime)).toEqual({
      attempts: 1,
      status: 'pending',
      nextAttemptAt: '2026-09-23T10:02:00.000Z',
    });
    expect(nextEmailRetry(4, 'ETIMEDOUT', currentTime)).toEqual({
      attempts: 5,
      status: 'failed',
      nextAttemptAt: '2026-09-23T10:32:00.000Z',
    });
  });

  it('does not consume attempts while SMTP is not configured', () => {
    expect(nextEmailRetry(2, 'SMTP_NOT_CONFIGURED', currentTime)).toEqual({
      attempts: 2,
      status: 'pending',
      nextAttemptAt: '2026-09-23T10:15:00.000Z',
    });
  });
});
