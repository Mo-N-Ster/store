import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Full suite against the same SQLite package with an isolated Node ABI binary.
// Keeps an already-running Electron installation's native module untouched.
export default defineConfig({
  plugins: [react()],
  test: { setupFiles: ['./scripts/j6ra-node-sqlite-setup.ts'] },
});
