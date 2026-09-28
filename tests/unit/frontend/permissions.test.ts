import { describe, expect, it } from 'vitest';
import { can } from '../../../frontend/src/security/permissions';
import { navigationFor } from '../../../frontend/src/navigation/navigation';

describe('renderer permission adapter', () => {
  it('fails closed before loading and for unknown modules/actions', () => {
    expect(can(null, 'PRODUCTS', 'READ')).toBe(false);
    expect(can([{ module: 'PRODUCTS', actions: ['READ'] }], 'PRODUCTS', 'UPDATE')).toBe(false);
    expect(can([{ module: 'PRODUCTS', actions: ['READ'] }], 'UNKNOWN', 'READ')).toBe(false);
    expect(navigationFor(null)).toEqual([]);
  });
  it('derives representative affordances solely from effective permissions', () => {
    const permissions = [{ module: 'PRODUCTS', actions: ['READ', 'CREATE'] as const }].map((x) => ({ ...x, actions: [...x.actions] }));
    expect(can(permissions, 'PRODUCTS', 'CREATE')).toBe(true);
    expect(can(permissions, 'STOCKS', 'UPDATE')).toBe(false);
    expect(navigationFor(permissions).map((item) => item.id)).toContain('products');
  });
});
