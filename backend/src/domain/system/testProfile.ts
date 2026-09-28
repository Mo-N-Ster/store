import path from 'node:path';

/** Development-only, explicit isolated profile. Never reuse personal userData. */
export function isolatedTestProfile(value: string | undefined, packaged: boolean) {
  if (!value) return null;
  if (packaged || !path.isAbsolute(value) || !/^store-j6ra-[a-z0-9-]+$/i.test(path.basename(value))) throw new Error('INVALID_TEST_PROFILE');
  return path.resolve(value);
}
