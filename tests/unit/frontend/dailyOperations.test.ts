import fs from 'node:fs';
import { describe, expect, it } from 'vitest';
import { navigationFor } from '../../../frontend/src/navigation/navigation';

const read = (path: string) => fs.readFileSync(path, 'utf8');

describe('STORE 3.0 daily operations', () => {
  it('adds Stock and Purchases only to management navigation and personal presence to Employee', () => {
    const manager = navigationFor(['PRODUCTS', 'STOCKS', 'PURCHASES', 'EMPLOYEES'].map((module) => ({ module, actions: ['READ', 'UPDATE'] as const }))).map((item) => item.id);
    const employee = navigationFor([{ module: 'PRESENCE', actions: ['READ'] }]).map((item) => item.id);
    expect(manager).toEqual(expect.arrayContaining(['products', 'stock', 'purchases', 'team']));
    expect(employee).toContain('team');
    expect(employee).not.toContain('presence');
    expect(employee).not.toContain('purchases');
  });

  it('keeps Products search, category/stock filters and the existing PDF import', () => {
    const source = read('frontend/src/pages/Dashboard/products/ProductList.tsx');
    expect(source).toContain("type=\"search\"");
    expect(source).toContain("stockFilter");
    expect(source).toContain('productService.importPdf');
    expect(source).not.toContain('importCsv');
  });

  it('prevents duplicate product saves and uses a confirmed destructive action', () => {
    const source = read('frontend/src/pages/Dashboard/products/ProductList.tsx');
    expect(source).toContain('if (saveLock.current) return');
    expect(source).toContain('<ConfirmDialog');
    expect(source).toContain('danger');
  });

  it('uses the shared existing-threshold stock status model', () => {
    const source = read('frontend/src/pages/Dashboard/stock/StockPage.tsx');
    expect(source).toContain('productStockStatus(product)');
    expect(source).toContain("status === 'out'");
    expect(source).toContain("status === 'low'");
  });

  it('requires reason and non-negative resulting stock for adjustment', () => {
    const source = read('frontend/src/pages/Dashboard/stock/StockPage.tsx');
    expect(source).toContain('name="newQuantity"');
    expect(source).toContain('min="0"');
    expect(source).toContain('name="reason"');
    expect(source).toContain('minLength={3}');
  });

  it('presents inventory as a draft and prevents double validation', () => {
    const source = read('frontend/src/pages/Dashboard/stock/StockPage.tsx');
    expect(source).toContain('inventoryDraftWarning');
    expect(source).toContain('validateLock.current');
    expect(source).toContain('recordInventoryLine');
    expect(source).toContain('validateInventory');
  });

  it('makes inventory differences textual and not color-only', () => {
    const source = read('frontend/src/pages/Dashboard/stock/StockPage.tsx');
    expect(source).toContain("t('difference')");
    expect(source).toContain('line.counted - line.product.stockQuantity');
  });

  it('preserves purchase draft idempotence and prevents duplicate validation', () => {
    const source = read('frontend/src/pages/Dashboard/purchases/PurchasesPage.tsx');
    expect(source).toContain('purchaseKey.current ||= crypto.randomUUID()');
    expect(source).toContain('idempotencyKey: purchaseKey.current');
    expect(source).toContain('if (actionLock.current) return');
    expect(source).toContain('validatePurchase');
  });

  it('states that purchase drafts have no stock effect', () => {
    const source = read('frontend/src/pages/Dashboard/purchases/PurchasesPage.tsx');
    expect(source).toContain('draftNoStockEffect');
    expect(source.indexOf('savePurchaseItem')).toBeLessThan(source.indexOf('validatePurchase'));
  });

  it('keeps supplier fields limited to the existing persistence model', () => {
    const source = read('frontend/src/pages/Dashboard/purchases/PurchasesPage.tsx');
    for (const field of ['name', 'phone', 'email', 'address']) expect(source).toContain(`name="${field}"`);
    expect(source).not.toContain('creditLimit');
  });

  it('does not offer Primary Owner promotion in the employee form', () => {
    const source = read('frontend/src/pages/Dashboard/employees/EmployeeList.tsx');
    const roleStart = source.indexOf("edit.role === 'owner'");
    const roleControl = source.slice(roleStart, roleStart + 700);
    expect(roleControl).toContain('<option value="employee">');
    expect(roleControl).toContain('<option value="manager">');
    expect(roleControl).not.toContain('<option value="owner">');
  });

  it('preserves the temporary password 60-second presentation', () => {
    const source = read('frontend/src/pages/Dashboard/employees/PasswordResetDialog.tsx');
    expect(source).toContain('useState(60)');
    expect(source).toContain('temporary-password');
    expect(source).not.toContain('console.');
  });

  it('uses an explicit multi-user attendance sheet and keeps correction trace fields', () => {
    const source = read('frontend/src/pages/Dashboard/employees/PresencePage.tsx');
    expect(source).toContain('dailyAttendanceSheet');
    expect(source).toContain('attendanceService.clock');
    expect(source).toContain("action: row.state === 'PRESENT' ? 'CLOCK_OUT' : 'CLOCK_IN'");
    expect(source).not.toContain('myPresence');
    expect(source).toContain('name="reason"');
    expect(source).toContain('historicalCorrection');
  });

  it('defines responsive, contrast and reduced-motion foundations for all operations pages', () => {
    const css = read('frontend/src/design-system/operations.css');
    expect(css).toContain('@media (max-width: 56.25rem)');
    expect(css).toContain('@media (prefers-contrast: more)');
    expect(css).toContain('@media (prefers-reduced-motion: reduce)');
    expect(css).toContain('min-height: var(--control-lg)');
  });

  it('keeps daily operations frontend-only', () => {
    const files = [
      'frontend/src/pages/Dashboard/products/ProductList.tsx',
      'frontend/src/pages/Dashboard/stock/StockPage.tsx',
      'frontend/src/pages/Dashboard/purchases/PurchasesPage.tsx',
      'frontend/src/pages/Dashboard/employees/TeamPage.tsx',
      'frontend/src/pages/Dashboard/employees/PresencePage.tsx',
    ];
    for (const file of files) {
      const source = read(file);
      expect(source).not.toMatch(/backend\/|better-sqlite3|ipcRenderer/);
    }
  });
});
