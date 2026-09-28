import { vi } from 'vitest';

vi.mock('better-sqlite3', async (importOriginal) => {
  const original = await importOriginal<typeof import('better-sqlite3')>();
  const path = await import('node:path');
  const nativeBinding = path.resolve('artifacts/j6ra-node-abi/build/Release/better_sqlite3.node');
  // Real SQLite, real disk I/O and real transactions; only ABI location changes.
  const Constructor = new Proxy(original.default, {
    construct(Target, args) { return new Target(args[0], { ...args[1], nativeBinding }); },
  });
  return { ...original, default: Constructor };
});
