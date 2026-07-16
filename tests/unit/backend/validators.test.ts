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
});
