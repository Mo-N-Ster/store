import { describe, expect, it } from 'vitest';
import { validateHistoryDeletion, validateHistoryType } from '../../../backend/src/domain/rbac/historyOperation';

describe('history runtime discriminators', () => {
  it.each([undefined, null, '', 'invalid-type', 'PERSONNEL', 'attendance', 0, false, [], {}, ['purchases']])('rejects unknown type %j before mutation', (type) => {
    expect(() => validateHistoryType(type)).toThrow('VALIDATION_ERROR');
    expect(() => validateHistoryDeletion({ type, ids: [] })).toThrow('VALIDATION_ERROR');
  });
  it('accepts only three reads and purchase deletion, never attendance or sales deletion', () => {
    for (const type of ['sales', 'purchases', 'personnel']) expect(() => validateHistoryType(type)).not.toThrow();
    expect(validateHistoryDeletion({ type: 'purchases', ids: [1, 1, 2] })).toEqual({ type: 'purchases', ids: [1, 2] });
    expect(() => validateHistoryDeletion({ type: 'personnel', ids: [] })).toThrow('FORBIDDEN');
    expect(() => validateHistoryDeletion({ type: 'sales', ids: [] })).toThrow('VALIDATION_ERROR');
  });
});
