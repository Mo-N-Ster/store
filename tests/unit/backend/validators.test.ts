import { describe, expect, it } from 'vitest';
import { validateUser } from '../../../backend/src/domain/user/user.validators';
import { validateProduct } from '../../../backend/src/domain/product/product.validators';
describe('user validation', () => {
  it('accepts a complete owner', () =>
    expect(() =>
      validateUser(
        {
          username: 'owner',
          email: 'owner@store.test',
          password: 'password!',
          firstName: 'Store',
          lastName: 'Owner',
          role: 'owner',
          securityQuestion: 'Nom du magasin ?',
          securityAnswer: 'STORE',
        },
        true,
      ),
    ).not.toThrow());
  it('rejects a weak password', () =>
    expect(() =>
      validateUser(
        {
          username: 'owner',
          email: 'owner@store.test',
          password: 'short',
          firstName: 'Store',
          lastName: 'Owner',
          role: 'owner',
          securityQuestion: 'Nom du magasin ?',
          securityAnswer: 'STORE',
        },
        true,
      ),
    ).toThrow('INVALID_USER'));
});
describe('product validation', () => {
  it('accepts valid stock data', () =>
    expect(() =>
      validateProduct({
        name: 'Poivre',
        category: 'Épices',
        price: 2.5,
        stockQuantity: 10,
        minStockThreshold: 2,
      }),
    ).not.toThrow());
  it('rejects negative stock', () =>
    expect(() =>
      validateProduct({
        name: 'Poivre',
        category: 'Épices',
        price: 2.5,
        stockQuantity: -1,
        minStockThreshold: 2,
      }),
    ).toThrow('INVALID_PRODUCT'));
  it('rejects prices smaller than the supported cent', () =>
    expect(() =>
      validateProduct({
        name: 'Poivre',
        category: 'Épices',
        price: 2.555,
        stockQuantity: 10,
        minStockThreshold: 2,
      }),
    ).toThrow('INVALID_PRODUCT'));
  it('rejects fractional and non-finite inventory values', () => {
    expect(() =>
      validateProduct({
        name: 'Poivre', category: 'Épices', price: Number.NaN,
        stockQuantity: 10, minStockThreshold: 2,
      }),
    ).toThrow('INVALID_PRODUCT');
    expect(() =>
      validateProduct({
        name: 'Poivre', category: 'Épices', price: 2.5,
        stockQuantity: 1.5, minStockThreshold: 2,
      }),
    ).toThrow('INVALID_PRODUCT');
  });
});
