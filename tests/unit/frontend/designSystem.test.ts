import fs from 'node:fs';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { Search } from 'lucide-react';
import {
  Alert,
  Badge,
  Button,
  Card,
  Drawer,
  Modal,
  NumberInput,
  ResponsiveTable,
  Select,
  TextInput,
  Toast,
  nextTabIndex,
  overlayFocusIndex,
} from '../../../frontend/src/design-system';

const html = (component: Parameters<typeof renderToStaticMarkup>[0]) => renderToStaticMarkup(component);

describe('STORE design system', () => {
  it('renders button variants, sizes, icons and a blocking loading state', () => {
    const markup = html(createElement(Button, { variant: 'danger', size: 'lg', loading: true, loadingLabel: 'Chargement', icon: Search }, 'Supprimer'));
    expect(markup).toContain('ds-button--danger');
    expect(markup).toContain('ds-button--lg');
    expect(markup).toContain('disabled');
    expect(markup).toContain('aria-busy="true"');
    expect(markup).toContain('Chargement');
  });

  it('associates visible field labels, help, errors and numeric input mode', () => {
    const text = html(createElement(TextInput, { id: 'email', label: 'E-mail', help: 'Aide', error: 'Erreur', required: true }));
    expect(text).toContain('for="email"');
    expect(text).toContain('email-help');
    expect(text).toContain('email-error');
    expect(text).toContain('aria-invalid="true"');
    const number = html(createElement(NumberInput, { id: 'amount', label: 'Montant' }));
    expect(number).toContain('inputMode="decimal"');
    expect(number).toContain('type="number"');
  });

  it('renders a native accessible select with an explicit placeholder', () => {
    const markup = html(createElement(Select, { id: 'role', label: 'Rôle', placeholder: 'Choisir' }, createElement('option', { value: 'owner' }, 'Owner')));
    expect(markup).toContain('for="role"');
    expect(markup).toContain('<option value="">Choisir</option>');
  });

  it('renders semantic status components with text and live regions', () => {
    expect(html(createElement(Badge, { variant: 'warning' }, 'Stock faible'))).toContain('Stock faible');
    expect(html(createElement(Alert, { variant: 'danger', title: 'Erreur' }, 'Action impossible'))).toContain('role="alert"');
    expect(html(createElement(Toast, { variant: 'error' }, 'Échec'))).toContain('aria-live="assertive"');
  });

  it('provides accessible modal and drawer semantics', () => {
    const modal = html(createElement(Modal, { title: 'Titre', description: 'Description', closeLabel: 'Fermer', onClose: () => undefined }, 'Contenu'));
    expect(modal).toContain('role="dialog"');
    expect(modal).toContain('aria-modal="true"');
    expect(modal).toContain('aria-labelledby');
    const drawer = html(createElement(Drawer, { side: 'bottom', title: 'Panier', closeLabel: 'Fermer', onClose: () => undefined }, 'Contenu'));
    expect(drawer).toContain('ds-overlay--drawer-bottom');
    expect(drawer).toContain('role="dialog"');
  });

  it('defines keyboard wrapping for tabs and overlay focus traps', () => {
    expect(nextTabIndex('ArrowRight', 2, 3)).toBe(0);
    expect(nextTabIndex('ArrowLeft', 0, 3)).toBe(2);
    expect(nextTabIndex('Home', 2, 3)).toBe(0);
    expect(nextTabIndex('End', 0, 3)).toBe(2);
    expect(overlayFocusIndex(false, 2, 3)).toBe(0);
    expect(overlayFocusIndex(true, 0, 3)).toBe(2);
    expect(overlayFocusIndex(false, 1, 3)).toBeNull();
  });

  it('makes responsive tables keyboard discoverable', () => {
    const markup = html(createElement(ResponsiveTable, { label: 'Produits' }, createElement('table', null)));
    expect(markup).toContain('role="region"');
    expect(markup).toContain('aria-label="Produits"');
    expect(markup).toContain('tabindex="0"');
  });

  it('prevents an interactive card from submitting a surrounding form', () => {
    const markup = html(createElement(Card, { interactive: true }, 'Ouvrir'));
    expect(markup).toContain('type="button"');
    expect(markup).toContain('ds-card--interactive');
  });

  it('declares semantic theme, touch, contrast and reduced-motion foundations', () => {
    const tokens = fs.readFileSync('frontend/src/design-system/tokens.css', 'utf8');
    const foundations = fs.readFileSync('frontend/src/design-system/foundations.css', 'utf8');
    for (const token of ['--color-background', '--color-surface', '--color-text', '--color-primary', '--color-success', '--color-warning', '--color-danger', '--color-info', '--touch-target-min']) expect(tokens).toContain(token);
    expect(tokens).toContain("html[data-theme='dark']");
    expect(tokens).toContain('@media (prefers-contrast: more)');
    expect(foundations).toContain('@media (pointer: coarse)');
    expect(foundations).toContain('@media (prefers-reduced-motion: reduce)');
    expect(foundations).toContain('min-width: var(--touch-target-min)');
  });
});
