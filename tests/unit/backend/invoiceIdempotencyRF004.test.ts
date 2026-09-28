import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import Database from 'better-sqlite3';
import { applyDatabaseMigrations } from '../../../backend/src/database/migrations';
import { afterEach, expect, it, vi } from 'vitest';

const state = vi.hoisted(() => ({ profile: '', handlers: new Map<string, (...args: any[]) => any>() }));
vi.mock('electron', () => ({
  app: { getPath: () => state.profile, getVersion: () => '2.0.1' },
  safeStorage: { isEncryptionAvailable: () => true },
  ipcMain: { handle: (name: string, handler: (...args: any[]) => any) => state.handlers.set(name, handler) },
  dialog: {}, shell: {},
}));
import { api, closeDatabase, initDatabase } from '../../../backend/src/database/storeDatabase';
import { registerIpcHandlers } from '../../../backend/src/main/ipcHandlers';

afterEach(() => {
  closeDatabase();
  if (state.profile) fs.rmSync(state.profile, { recursive: true, force: true });
});

it('RF-004: rejects another actor and basket reusing a committed sale key', async () => {
  state.profile = fs.mkdtempSync(path.join(os.tmpdir(), 'store-rf004-test-'));
  await initDatabase();
  api.setupAdmin({ username: 'owner', email: 'owner@fixture.invalid', firstName: 'Test', lastName: 'Owner', password: 'Owner-only-123', securityQuestion: 'Question', securityAnswer: 'Answer' });
  api.saveUser({ username: 'employee', firstName: 'Test', lastName: 'Employee', role: 'employee', password: 'Employee-only-123' });
  const a = api.saveProduct({ name: 'A', category: 'Test', price: 10, stockQuantity: 5, minStockThreshold: 0 });
  const b = api.saveProduct({ name: 'B', category: 'Test', price: 10, stockQuantity: 5, minStockThreshold: 0 });
  registerIpcHandlers();
  const ownerEvent = { sender: { id: 601 } };
  const employeeEvent = { sender: { id: 602 } };
  const call = (event: typeof ownerEvent, name: string, input?: unknown) => state.handlers.get(`store:${name}`)!(event, input);
  await call(ownerEvent, 'login', { identifier: 'owner', password: 'Owner-only-123', role: 'manager' });
  await call(ownerEvent, 'openCashSession', { openingAmount: 0 });
  const original = await call(ownerEvent, 'createInvoice', { lines: [{ productId: a, quantity: 1 }], amountReceived: 10, idempotencyKey: 'rf004-fixture-key' });
  await call(employeeEvent, 'login', { identifier: 'employee', password: 'Employee-only-123', role: 'employee' });
  expect(await call(employeeEvent, 'currentCashSession', {})).toBeUndefined();
  // Invoice details are legitimately readable by this role and reveal the stored key.
  const visibleInvoice = await call(employeeEvent, 'invoice', original.invoice.id);
  const request = { lines: [{ productId: b, quantity: 2 }], amountReceived: 20, idempotencyKey: visibleInvoice.invoice.idempotency_key };
  let response: any;
  let rejected = false;
  try { response = await call(employeeEvent, 'createInvoice', request); } catch { rejected = true; }
  expect.soft(rejected, 'cross-actor, different-basket reuse must be rejected').toBe(true);
  expect(response?.invoice?.id, 'must not acknowledge the unrelated existing invoice').toBeUndefined();
});

it('RF-004: proves replay equivalence and preserves historical rows across migration/restart', async () => {
  state.profile = fs.mkdtempSync(path.join(os.tmpdir(), 'store-rf004-test-'));
  await initDatabase();
  api.setupAdmin({ username: 'owner', email: 'owner@fixture.invalid', firstName: 'Test', lastName: 'Owner', password: 'Owner-only-123', securityQuestion: 'Question', securityAnswer: 'Answer' });
  api.saveUser({ username: 'employee', firstName: 'Test', lastName: 'Employee', role: 'employee', password: 'Employee-only-123' });
  const a = api.saveProduct({ name: 'A', category: 'Test', price: 10, stockQuantity: 20, minStockThreshold: 0 });
  const b = api.saveProduct({ name: 'B', category: 'Test', price: 10, stockQuantity: 20, minStockThreshold: 0 });
  registerIpcHandlers();
  const owner = { sender: { id: 611 } };
  const employee = { sender: { id: 612 } };
  const call = (event: typeof owner, name: string, input?: unknown) => state.handlers.get(`store:${name}`)!(event, input);
  await call(owner, 'login', { identifier: 'owner', password: 'Owner-only-123', role: 'manager' });
  await call(owner, 'openCashSession', { openingAmount: 0 });
  const request = { lines: [{ productId: a, quantity: 1 }, { productId: b, quantity: 2 }], discount: 5, amountReceived: 30, idempotencyKey: 'rf004-equivalence' };
  const result = await call(owner, 'createInvoice', request);
  const snapshot = () => {
    const reader = new Database(path.join(state.profile, 'store.db'), { readonly: true });
    try {
      return ['invoices', 'invoice_lines', 'payments', 'stock_movements', 'products'].map((table) => reader.prepare(`SELECT * FROM ${table} ORDER BY rowid`).all());
    } finally { reader.close(); }
  };
  const committed = snapshot();
  expect((await call(owner, 'createInvoice', { ...request, lines: [...request.lines].reverse() })).invoice.id).toBe(result.invoice.id);
  expect(snapshot()).toEqual(committed);
  for (const changed of [
    { lines: [{ productId: a, quantity: 2 }] },
    { discount: 6 }, { amountReceived: 31 }, { amountReceived: undefined },
  ]) {
    await expect(call(owner, 'createInvoice', { ...request, ...changed })).rejects.toThrow();
    expect(snapshot()).toEqual(committed);
  }
  // Replay uses the original request, not mutable pricing/settings/remaining stock.
  api.saveSettings({ discountsEnabled: 'false' });
  expect((await call(owner, 'createInvoice', request)).invoice.id).toBe(result.invoice.id);
  expect(snapshot()).toEqual(committed);
  await call(employee, 'login', { identifier: 'employee', password: 'Employee-only-123', role: 'employee' });
  await call(employee, 'openCashSession', { openingAmount: 0 });
  await expect(call(employee, 'createInvoice', { ...request, employeeId: result.invoice.employee_id })).rejects.toThrow();
  expect(snapshot()).toEqual(committed);
  await call(owner, 'closeCashSession', { countedAmount: 25 });
  await expect(call(owner, 'createInvoice', request)).rejects.toThrow();
  expect(snapshot()).toEqual(committed);
  await call(owner, 'openCashSession', { openingAmount: 0 });
  await expect(call(owner, 'createInvoice', request)).rejects.toThrow();
  expect(snapshot()).toEqual(committed);

  // Construct a genuine pre-18 schema fixture from this isolated committed sale.
  closeDatabase();
  const historical = new Database(path.join(state.profile, 'store.db'));
  historical.exec('ALTER TABLE invoices DROP COLUMN idempotency_request; DELETE FROM schema_migrations WHERE version=18');
  const before = historical.prepare('SELECT * FROM invoices').all();
  const otherBefore = ['invoice_lines', 'payments', 'stock_movements', 'products'].map((table) => historical.prepare(`SELECT * FROM ${table} ORDER BY rowid`).all());
  applyDatabaseMigrations(historical);
  expect(historical.prepare('SELECT idempotency_request FROM invoices').all()).toEqual([{ idempotency_request: null }]);
  const after = historical.prepare('SELECT * FROM invoices').all() as Record<string, unknown>[];
  expect(after.map(({ idempotency_request: _command, ...row }) => row)).toEqual(before);
  expect(['invoice_lines', 'payments', 'stock_movements', 'products'].map((table) => historical.prepare(`SELECT * FROM ${table} ORDER BY rowid`).all())).toEqual(otherBefore);
  expect(historical.pragma('integrity_check', { simple: true })).toBe('ok');
  expect(historical.pragma('foreign_key_check')).toEqual([]);
  historical.close();
  await initDatabase();
  const historicalSnapshot = snapshot();
  // Match the original cash context, so NULL itself must prevent replay.
  closeDatabase();
  const fixture = new Database(path.join(state.profile, 'store.db'));
  fixture.prepare("UPDATE cash_sessions SET status='CLOSED' WHERE employee_id=?").run(result.invoice.employee_id);
  fixture.prepare("UPDATE cash_sessions SET status='OPEN' WHERE id=?").run(result.invoice.cash_session_id);
  fixture.close();
  await initDatabase();
  await expect(call(owner, 'createInvoice', request)).rejects.toThrow();
  expect(snapshot()).toEqual(historicalSnapshot);
});
