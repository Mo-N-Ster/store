import { describe, expect, it } from 'vitest';
import { normalizeDecimal, validDecimal } from '../../../frontend/src/utils/decimalInput';
import { navigationFor } from '../../../frontend/src/navigation/navigation';
describe('Localized decimal entry and employee navigation', () => {
  it('accepts comma and point while rejecting ambiguous separators', () => {
    expect(normalizeDecimal('12,34')).toBe('12.34');
    expect(validDecimal(normalizeDecimal('12,34'), 0, 100, .01)).toBe(true);
    expect(validDecimal(normalizeDecimal('1,234,56'), 0, 2000, .01)).toBe(false);
  });
  it('keeps precision, finite values and range limits', () => {
    expect(validDecimal('12.345', 0, 100, .01)).toBe(false);
    expect(validDecimal('-1', 0, 100, .01)).toBe(false);
    expect(validDecimal('101', 0, 100, .01)).toBe(false);
    expect(validDecimal('Infinity')).toBe(false);
    expect(validDecimal('0.30', 0, 100, .01)).toBe(true);
  });
  it('removes Messages only for employee navigation', () => {
    const permissions = [{ module: 'DASHBOARD', actions: ['READ' as const] }];
    expect(navigationFor(permissions, 'employee').some((item) => item.id === 'messages')).toBe(false);
    expect(navigationFor(permissions, 'manager').some((item) => item.id === 'messages')).toBe(true);
    expect(navigationFor(permissions, 'owner').some((item) => item.id === 'messages')).toBe(true);
  });
});
