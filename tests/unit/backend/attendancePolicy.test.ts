import { describe, expect, it } from 'vitest';
import { validateAttendanceCorrection } from '../../../backend/src/domain/attendance/attendancePolicy';
import { validateAttendanceTransition } from '../../../backend/src/domain/attendance/attendancePolicy';

describe('attendance correction policy', () => {
  it('requires a reason and a positive time interval', () => {
    expect(() =>
      validateAttendanceCorrection({
        startTime: '2026-09-23T10:00:00Z',
        endTime: '2026-09-23T09:00:00Z',
        reason: 'Erreur',
      }),
    ).toThrow('VALIDATION_ERROR');
    expect(() =>
      validateAttendanceCorrection({
        startTime: '2026-09-23T09:00:00Z',
        endTime: '2026-09-23T10:00:00Z',
        reason: '  ',
      }),
    ).toThrow('VALIDATION_ERROR');
  });

  it('rejects future corrections and invalid normal transitions', () => {
    expect(() => validateAttendanceCorrection({ startTime: '2099-01-01T09:00:00Z', endTime: '2099-01-01T10:00:00Z', reason: 'Future' })).toThrow('VALIDATION_ERROR');
    expect(() => validateAttendanceTransition('CLOCK_IN', true)).toThrow('SERVICE_ALREADY_OPEN');
    expect(() => validateAttendanceTransition('CLOCK_OUT', false)).toThrow('NO_OPEN_SERVICE');
    expect(validateAttendanceTransition('CLOCK_IN', false)).toBe('CLOCK_IN');
  });

  it('normalizes a valid correction', () => {
    expect(
      validateAttendanceCorrection({
        startTime: '2026-09-23T09:00:00+02:00',
        endTime: '2026-09-23T17:00:00+02:00',
        reason: '  Correction badgeuse  ',
      }),
    ).toEqual({
      startTime: '2026-09-23T07:00:00.000Z',
      endTime: '2026-09-23T15:00:00.000Z',
      reason: 'Correction badgeuse',
    });
  });
});
