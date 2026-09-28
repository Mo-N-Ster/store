import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';
export default defineConfig({ root: path.resolve('scripts/input-smoke'), base: './', plugins: [react()], build: { outDir: '../../artifacts/input-smoke', emptyOutDir: false } });
