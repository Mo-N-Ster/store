import fs from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('Shared light PDF palette', () => {
  it('overrides both theme systems only for print media', () => {
    const css = fs.readFileSync('frontend/src/design-system/print-light.css', 'utf8');
    expect(css).toContain('@media print');
    expect(css).toContain("html[data-theme='dark']");
    expect(css).toContain('color-scheme: light !important');
    for (const variable of ['--color-surface', '--surface', '--panel', '--bg'])
      expect(css).toContain(`${variable}: #ffffff`);
    expect(css).toContain('--color-text: #17231b');
    expect(css).toContain('--text: #17231b');
  });
  it('loads after all screen and document-specific refinements', () => {
    const source = fs.readFileSync('frontend/src/index.tsx', 'utf8');
    expect(source.indexOf("import './design-system/print-light.css'")).toBeGreaterThan(source.indexOf("import './design-system/refinements.css'"));
  });
});
