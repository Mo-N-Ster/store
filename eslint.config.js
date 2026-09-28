import js from '@eslint/js';
import tseslint from 'typescript-eslint';
export default tseslint.config(
  { ignores: ['dist/**', 'dist-electron/**', 'release/**', 'node_modules/**', 'artifacts/**'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: [
      'frontend/src/**/*.{ts,tsx}',
      'backend/src/**/*.ts',
      'tests/**/*.ts',
      'scripts/**/*.{js,mjs,cjs}',
    ],
    languageOptions: { parserOptions: { ecmaFeatures: { jsx: true } } },
    rules: {
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      'no-undef': 'off',
    },
  },
);
