import { describe, expect, it } from 'vitest';
import { canEditPurchase, validatePurchaseItem } from '../../../backend/src/domain/purchase/purchasePolicy';

describe('purchase policy', () => {
  it('accepts only positive integer quantities and non-negative finite costs', () => {
    expect(validatePurchaseItem({ quantity: 3, unitCost: 12.5 })).toEqual({
      quantity: 3,
      unitCost: 12.5,
    });
    expect(() => validatePurchaseItem({ quantity: 0, unitCost: 12 })).toThrow('VALIDATION_ERROR');
    expect(() => validatePurchaseItem({ quantity: 1.5, unitCost: 12 })).toThrow(
      'VALIDATION_ERROR',
    );
    expect(() => validatePurchaseItem({ quantity: 1, unitCost: Number.NaN })).toThrow(
      'VALIDATION_ERROR',
    );
  });

  it('allows changes only while the purchase is a draft', () => {
    expect(canEditPurchase('DRAFT')).toBe(true);
    expect(canEditPurchase('VALIDATED')).toBe(false);
    expect(canEditPurchase('CANCELLED')).toBe(false);
  });
});
