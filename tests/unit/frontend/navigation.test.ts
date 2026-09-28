import fs from 'node:fs';
import { describe, expect, it } from 'vitest';
import { defaultDestination, destinationIsAvailable, navigationFor } from '../../../frontend/src/navigation/navigation';
import type { EffectivePermission } from '../../../frontend/src/security/permissions';

const permissions = (modules: string[]): EffectivePermission[] => modules.map((module) => ({ module, actions: ['READ', 'CREATE', 'UPDATE', 'DELETE', 'VALIDATE'] }));
const employee: EffectivePermission[] = [
  ...['DASHBOARD', 'PRESENCE', 'PRODUCTS', 'STOCKS'].map((module) => ({ module, actions: ['READ'] as const })).map((entry) => ({ ...entry, actions: [...entry.actions] })),
  ...['CASH', 'POS'].map((module) => ({ module, actions: ['READ', 'CREATE', 'UPDATE', 'VALIDATE'] as const })).map((entry) => ({ ...entry, actions: [...entry.actions] })),
];
const manager = permissions(['DASHBOARD', 'PRESENCE', 'CASH', 'POS', 'PRODUCTS', 'STOCKS', 'PURCHASES', 'EMPLOYEES', 'FINANCES']);
const owner = permissions(['DASHBOARD', 'PRESENCE', 'CASH', 'POS', 'PRODUCTS', 'STOCKS', 'PURCHASES', 'EMPLOYEES', 'FINANCES', 'ADMINISTRATION', 'SETTINGS', 'BACKUPS', 'RESTORE', 'RESET']);

describe('STORE 3.0 effective-permission navigation', () => {
  it('derives the default destination from effective permissions and fails closed', () => {
    expect(defaultDestination(owner)).toBe('home');
    expect(defaultDestination(manager)).toBe('home');
    expect(defaultDestination(employee)).toBe('home');
    expect(defaultDestination(null)).toBe('home');
    expect(navigationFor(null)).toEqual([]);
  });
  it('keeps administration unavailable without SETTINGS:READ', () => {
    expect(navigationFor(owner).map((item) => item.id)).toContain('administration');
    expect(navigationFor(manager).map((item) => item.id)).not.toContain('administration');
  });
  it('uses actual cashier permissions instead of reconstructing a role policy', () => {
    expect(navigationFor(employee).map((item) => item.id)).toEqual(['home', 'pos', 'products', 'team', 'messages']);
  });
  it('keeps authenticated secondary destinations reversible once permissions loaded', () => {
    for (const value of [owner, manager, employee]) {
      expect(destinationIsAvailable(value, 'help')).toBe(true);
    }
  });
  it('protects financial history through FINANCES:READ', () => {
    expect(destinationIsAvailable(owner, 'salesHistory')).toBe(true);
    expect(destinationIsAvailable(manager, 'salesHistory')).toBe(true);
    expect(destinationIsAvailable(employee, 'salesHistory')).toBe(false);
    expect(navigationFor(owner).map((item) => item.id)).not.toContain('salesHistory');
  });
  it('does not open a destination for an unknown permission', () => {
    const unknown = [{ module: 'UNKNOWN', actions: ['READ'] as const }];
    expect(navigationFor(unknown).map((item) => item.id)).toEqual(['messages']);
    expect(destinationIsAvailable(unknown, 'administration')).toBe(false);
  });
  it('keeps all navigation labels in the translation layer', () => {
    for (const value of [owner, manager, employee]) for (const item of navigationFor(value)) expect(item.labelKey).toMatch(/^nav[A-Z]/);
  });
  it('defines accessible responsive shell foundations', () => {
    const css = fs.readFileSync('frontend/src/design-system/shell.css', 'utf8');
    expect(css).toContain('@media (max-width: 68rem)'); expect(css).toContain('@media (max-width: 50rem)');
    expect(css).toContain('@media (prefers-reduced-motion: reduce)'); expect(css).toContain('@media (prefers-contrast: more)');
    expect(css).toContain('min-height: var(--touch-target-min)');
  });
  it('provides skip navigation, current-page semantics and landmarks', () => {
    const shell = fs.readFileSync('frontend/src/components/Layout/StoreShell.tsx', 'utf8');
    const primitives = fs.readFileSync('frontend/src/design-system/components/layout.tsx', 'utf8');
    expect(shell).toContain('href="#store-main"'); expect(shell).toContain("aria-current={active === item.id ? 'page' : undefined}");
    expect(shell).toContain('aria-label={compact ? t(item.labelKey) : undefined}'); expect(primitives).toContain('<header');
    expect(primitives).toContain('<nav'); expect(primitives).toContain('<main');
  });
});
