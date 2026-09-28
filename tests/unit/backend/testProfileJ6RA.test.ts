import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { isolatedTestProfile } from '../../../backend/src/domain/system/testProfile';

describe('Explicit isolated J.6R-A smoke profile', () => {
  it('leaves normal launches unchanged', () => expect(isolatedTestProfile(undefined, false)).toBeNull());
  it('accepts only an explicit dedicated absolute development profile', () => {
    const profile = path.resolve('artifacts/store-j6ra-smoke');
    expect(isolatedTestProfile(profile, false)).toBe(profile);
    expect(() => isolatedTestProfile(profile, true)).toThrow('INVALID_TEST_PROFILE');
    expect(() => isolatedTestProfile('relative', false)).toThrow('INVALID_TEST_PROFILE');
    expect(() => isolatedTestProfile(path.resolve('artifacts/personal'), false)).toThrow('INVALID_TEST_PROFILE');
  });
});
