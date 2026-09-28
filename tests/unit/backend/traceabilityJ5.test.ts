import fs from 'node:fs';
import Database from 'better-sqlite3';
import { describe, expect, it } from 'vitest';
import { schema } from '../../../backend/src/database/schema';
import { authorizationFor, isIpcAuthorized } from '../../../backend/src/domain/rbac/ipcPermissions';
import { defaultPermissions } from '../../../backend/src/domain/rbac/permissionMatrix';
import { purchaseSummary } from '../../../frontend/src/pages/Dashboard/charts/reportingModel';

const read = (path: string) => fs.readFileSync(path, 'utf8');

describe('J.5 stock, purchase and reporting traceability', () => {
  const databaseSource = read('backend/src/database/storeDatabase.ts');
  const stockPage = read('frontend/src/pages/Dashboard/stock/StockPage.tsx');
  const purchasesPage = read('frontend/src/pages/Dashboard/purchases/PurchasesPage.tsx');
  const reportsPage = read('frontend/src/pages/Dashboard/charts/ChartsPage.tsx');

  it('preserves real purchase, sale and inventory foreign-key relationships', () => {
    const database = new Database(':memory:');
    try {
      database.exec(schema);
      const columns = (table: string) => database.pragma(`foreign_key_list(${table})`) as Array<{ table: string }>;
      expect(columns('purchases').map((row) => row.table)).toContain('suppliers');
      expect(columns('purchase_items').map((row) => row.table)).toEqual(expect.arrayContaining(['purchases', 'products']));
      expect(columns('invoice_lines').map((row) => row.table)).toEqual(expect.arrayContaining(['invoices', 'products']));
      expect(columns('inventory_count_lines').map((row) => row.table)).toEqual(expect.arrayContaining(['inventory_counts', 'products']));
    } finally { database.close(); }
  });

  it('documents source relations only through persisted reason and reference', () => {
    expect(databaseSource).toContain("sm.reason='sale' AND i.id=sm.reference_id");
    expect(databaseSource).toContain("sm.reason IN ('purchase','purchase_cancellation') AND CAST(pu.id AS TEXT)=sm.reference_id");
    expect(databaseSource).toContain("sm.reason='inventory' AND CAST(ic.id AS TEXT)=sm.reference_id");
    expect(databaseSource).not.toMatch(/nearest|same timestamp|same quantity/i);
  });

  it('does not fabricate movement actors or before/after values', () => {
    const query = databaseSource.slice(databaseSource.indexOf('stockMovements:'), databaseSource.indexOf('startInventory:'));
    expect(query).not.toMatch(/createdBy|actor|beforeQuantity|afterQuantity/);
    expect(stockPage).not.toMatch(/beforeQuantity|afterQuantity|row\.actor/);
  });

  it('uses bounded, validated and deterministic movement retrieval', () => {
    expect(databaseSource).toContain('const boundedLimit = Math.min(500');
    expect(databaseSource).toContain("ORDER BY sm.created_at DESC,sm.id DESC LIMIT ?");
    expect(databaseSource).toContain("throw new Error('VALIDATION_ERROR')");
  });

  it('shows exact movement quantity, semantic direction and persisted reference', () => {
    expect(stockPage).toContain('row.quantity > 0 ? t(\'entries\') : t(\'exits\')');
    expect(stockPage).toContain('row.sourceReference');
    expect(stockPage).toContain('new Date(row.createdAt).toLocaleString()');
  });

  it('keeps purchase validation exactly once and links its persisted movements', () => {
    expect(databaseSource).toContain("if (purchase.status === 'VALIDATED') return true");
    expect(databaseSource).toContain("VALUES(?,?,'purchase',?,?)");
    expect(databaseSource).toContain("sm.reason IN ('purchase','purchase_cancellation') AND sm.reference_id=?");
  });

  it('presents purchase items, actors, totals and stock effects without mutation changes', () => {
    expect(purchasesPage).toContain('operationsService.purchaseDetail');
    for (const field of ['detail.createdBy', 'detail.total_amount', 'detail.items', 'detail.movements']) expect(purchasesPage).toContain(field);
  });

  it('keeps supplier-filtered purchase KPI and rows on the same population', () => {
    const filters = { from: '2026-09-01', to: '2026-09-30', grain: 'day' as const, supplierId: 1 };
    const result = purchaseSummary([
      { id: 1, supplier_id: 1, status: 'VALIDATED', total_amount: 40, effective_date: '2026-09-01' },
      { id: 2, supplier_id: 2, status: 'VALIDATED', total_amount: 90, effective_date: '2026-09-02' },
      { id: 3, supplier_id: 1, status: 'VALIDATED', total_amount: 10, effective_date: '2026-10-01' },
    ], filters);
    expect(result.rows.map((row) => row.id)).toEqual([1]);
    expect(result.validatedTotal).toBe(40);
  });

  it('removes unsupported margin and stock valuation from active reporting', () => {
    expect(databaseSource).not.toContain('estimatedGrossMargin');
    expect(databaseSource).not.toContain('stockValue');
    expect(reportsPage).not.toContain('estimatedGrossMargin');
  });

  it('protects narrow traceability channels and denies employees/direct unknown IPC', () => {
    for (const method of ['stockMovements', 'purchaseDetail']) expect(authorizationFor(method)?.classification).toBe('PROTECTED');
    expect(isIpcAuthorized('stockMovements', defaultPermissions('CASHIER'))).toBe(true);
    expect(isIpcAuthorized('purchaseDetail', defaultPermissions('CASHIER'))).toBe(false);
    expect(isIpcAuthorized('runSql', defaultPermissions('ADMIN'))).toBe(false);
  });
});
