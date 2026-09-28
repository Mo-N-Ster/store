import { describe, expect, it } from 'vitest';
import { calculateCashDifference, validateMoneyAmount } from '../../../backend/src/domain/cash/cashPolicy';

describe('cash policy', () => {
  it('accepts non-negative finite amounts and rounds cents', () => {
    expect(validateMoneyAmount(10.126)).toBe(10.13);
    expect(validateMoneyAmount(0)).toBe(0);
    expect(() => validateMoneyAmount(-1)).toThrow('VALIDATION_ERROR');
    expect(() => validateMoneyAmount(Number.NaN)).toThrow('VALIDATION_ERROR');
  });

  it('calculates overages and shortages', () => {
    expect(calculateCashDifference(100, 105)).toBe(5);
    expect(calculateCashDifference(100, 97.5)).toBe(-2.5);
  });
});
