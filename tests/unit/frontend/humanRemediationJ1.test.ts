import fs from 'node:fs';
import { describe, expect, it } from 'vitest';
import { destinationIsAvailable, navigationFor } from '../../../frontend/src/navigation/navigation';
import type { EffectivePermission } from '../../../frontend/src/security/permissions';

const owner: EffectivePermission[] = ['DASHBOARD', 'POS', 'PRODUCTS', 'STOCKS', 'PURCHASES', 'EMPLOYEES', 'PRESENCE', 'FINANCES', 'SETTINGS']
  .map((module) => ({ module, actions: ['READ', 'CREATE', 'UPDATE', 'DELETE', 'VALIDATE'] }));

describe('Phase J.1 safe UX remediation', () => {
  it('keeps Help secondary while retaining permission-aware access', () => {
    expect(navigationFor(owner).map((item) => item.id)).not.toContain('help');
    expect(destinationIsAvailable(owner, 'help')).toBe(true);
    const shell = fs.readFileSync('frontend/src/components/Layout/StoreShell.tsx', 'utf8');
    expect(shell).toContain("choose('help')");
  });

  it('opens quick chat as an accessible right drawer without adding navigation history', () => {
    const shell = fs.readFileSync('frontend/src/components/Layout/StoreShell.tsx', 'utf8');
    const app = fs.readFileSync('frontend/src/App.tsx', 'utf8');
    expect(shell).toContain('side="right"');
    expect(shell).toContain('onClick={props.onOpenChat}');
    expect(shell).toContain('aria-expanded={props.chatOpen}');
    expect(shell).not.toContain("choose('chat')");
    expect(app).not.toContain("destination === 'chat'");
  });

  it('separates quick-chat responsibilities from message management', () => {
    const mailbox = fs.readFileSync('frontend/src/pages/Dashboard/mailbox/MailboxPage.tsx', 'utf8');
    expect(mailbox).toContain("variant === 'management' && <IconButton");
    expect(mailbox).toContain("className={variant === 'chat' ? 'quick-chat' : 'mailbox-management'}");
    expect(mailbox).toContain('role="log"');
    expect(mailbox).toContain('label={t(\'deleteMessage\')}');
  });

  it('uses canonical Article and Item navigation terminology', () => {
    const translations = fs.readFileSync('frontend/src/i18n/i18n.ts', 'utf8');
    expect(translations).toContain("navProducts: 'Articles'");
    expect(translations).toContain("navProducts: 'Items'");
    expect(translations).toContain("product: 'Article'");
    expect(translations).toContain("product: 'Item'");
  });

  it('keeps the compact-navigation control next to the brand and labels icon controls', () => {
    const shell = fs.readFileSync('frontend/src/components/Layout/StoreShell.tsx', 'utf8');
    const brand = shell.indexOf('className="store-nav__brand"');
    const toggle = shell.indexOf('className="store-shell__collapse"');
    const groups = shell.indexOf('{groups.map');
    expect(brand).toBeGreaterThan(-1);
    expect(toggle).toBeGreaterThan(brand);
    expect(toggle).toBeLessThan(groups);
    expect(shell).toContain("label={compact ? t('expandNavigation') : t('collapseNavigation')}");
    expect(shell).toContain("label={t('back')}");
  });

  it('defines constrained drawer and conversation scrolling for tablet layouts', () => {
    const css = fs.readFileSync('frontend/src/design-system/shell.css', 'utf8');
    expect(css).toContain('.quick-chat-drawer');
    expect(css).toContain('grid-template-rows: auto minmax(0, 1fr)');
    expect(css).toContain('.chat-conversation');
    expect(css).toContain('overflow-y: auto');
    expect(css).toContain('@media (max-width: 35rem)');
  });
});
