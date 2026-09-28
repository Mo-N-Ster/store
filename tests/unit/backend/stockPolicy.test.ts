import { describe, expect, it } from 'vitest';
import {
  validateInventoryQuantity,
  validateStockAdjustment,
} from '../../../backend/src/domain/stock/stockPolicy';

describe('stock policy', () => {
  it('requires a non-negative integer and a reason for adjustments', () => {
    expect(validateStockAdjustment({ newQuantity: 4, reason: '  Comptage physique  ' })).toEqual({
      newQuantity: 4,
      reason: 'Comptage physique',
    });
    expect(() => validateStockAdjustment({ newQuantity: -1, reason: 'Erreur' })).toThrow(
      'VALIDATION_ERROR',
    );
    expect(() => validateStockAdjustment({ newQuantity: 2, reason: '' })).toThrow(
      'VALIDATION_ERROR',
    );
  });

  it('rejects invalid inventory quantities', () => {
    expect(validateInventoryQuantity(0)).toBe(0);
    expect(() => validateInventoryQuantity(1.5)).toThrow('VALIDATION_ERROR');
  });
});
