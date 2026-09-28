import { describe, expect, it } from 'vitest';
import { movementKey, movementTypes, statusKey } from '../../../frontend/src/utils/entityLabels';
describe('Consistent entity labels', () => {
  it('normalizes document case without inventing a draft for an unknown status', () => {
    expect(statusKey(' VALIDATED ')).toBe('validated');
    expect(statusKey('cancelled')).toBe('cancelled');
    expect(statusKey('future-state')).toBe('unknownStatus');
    expect(statusKey(null)).toBe('unknownStatus');
  });
  it('keeps interrupted and corrected attendance distinct from completed sessions', () => {
    expect(statusKey('INTERRUPTED', 'attendance')).toBe('interrupted');
    expect(statusKey('CORRECTED', 'attendance')).toBe('corrected');
    expect(statusKey('VALID', 'attendance')).toBe('attendanceCompleted');
    expect(statusKey('OPEN', 'attendance')).toBe('inProgress');
  });
  it('uses a bounded movement vocabulary in filters and rows', () => {
    for (const reason of movementTypes) expect(movementKey(reason.toUpperCase())).toBe(`movement_${reason}`);
    expect(movementKey('unexpected')).toBe('movement_other');
  });
});
