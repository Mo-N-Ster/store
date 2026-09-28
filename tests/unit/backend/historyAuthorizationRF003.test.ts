import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import Database from 'better-sqlite3';
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

it('RF-003: rejects invalid history type through Manager IPC without deleting attendance', async () => {
  state.profile = fs.mkdtempSync(path.join(os.tmpdir(), 'store-rf003-test-'));
  await initDatabase();
  const owner = api.setupAdmin({ username: 'owner', email: 'owner@fixture.invalid', firstName: 'Test', lastName: 'Owner', password: 'Fixture-only-123', securityQuestion: 'Question', securityAnswer: 'Answer' });
  api.saveUser({ username: 'manager', email: 'manager@fixture.invalid', firstName: 'Test', lastName: 'Manager', role: 'manager', password: 'Manager-only-123', securityQuestion: 'Question', securityAnswer: 'Answer' });
  const fixture = new Database(path.join(state.profile, 'store.db'));
  let attendanceId: number;
  try {
    attendanceId = Number(fixture.prepare('INSERT INTO attendances(employee_id,start_time,end_time) VALUES(?,?,?)').run(owner.id, '2026-01-01T08:00:00Z', '2026-01-01T17:00:00Z').lastInsertRowid);
  } finally { fixture.close(); }
  registerIpcHandlers();
  const event = { sender: { id: 503 } };
  const call = (name: string, input?: unknown) => state.handlers.get(`store:${name}`)!(event, input);
  await call('login', { identifier: 'manager', password: 'Manager-only-123', role: 'manager' });
  await expect(call('deleteHistory', { type: 'personnel', ids: [attendanceId] })).rejects.toThrow('FORBIDDEN');
  const countAttendance = () => {
    const db = new Database(path.join(state.profile, 'store.db'), { readonly: true });
    try { return (db.prepare('SELECT COUNT(*) n FROM attendances WHERE id=?').get(attendanceId) as { n: number }).n; }
    finally { db.close(); }
  };
  for (const type of [undefined, null, '', 'PERSONNEL', 'sales', 'attendance', 0, false, [], {}, ['purchases']]) {
    await expect(call('deleteHistory', { type, ids: [attendanceId], userId: owner.id })).rejects.toThrow();
    expect(countAttendance()).toBe(1);
  }
  for (const payload of [undefined, null, '', 12, [], { type: 'purchases', ids: null }, { type: 'purchases', ids: ['1'] }]) {
    await expect(call('deleteHistory', payload)).rejects.toThrow();
    expect(countAttendance()).toBe(1);
  }
  // A valid operation cannot use an attendance id to select that resource.
  await call('deleteHistory', { type: 'purchases', ids: [attendanceId], userId: owner.id });
  expect(countAttendance()).toBe(1);
  const productId = api.saveProduct({ name: 'Fixture', category: 'Test', price: 1, stockQuantity: 1, minStockThreshold: 0 });
  const check = new Database(path.join(state.profile, 'store.db'), { readonly: true });
  const movementId = (check.prepare('SELECT id FROM stock_movements WHERE product_id=?').get(productId) as { id: number }).id;
  check.close();
  expect(await call('deleteHistory', { type: 'purchases', ids: [movementId], userId: owner.id })).toBe(1);
  expect(countAttendance()).toBe(1);
  await call('login', { identifier: 'owner', password: 'Fixture-only-123', role: 'manager' });
  await expect(call('deleteHistory', { type: 'personnel', ids: [attendanceId] })).rejects.toThrow('FORBIDDEN');
  expect(await call('deleteHistory', { type: 'purchases', ids: [] })).toBe(0);
  await call('login', { identifier: 'manager', password: 'Manager-only-123', role: 'manager' });
  let rejected = false;
  try { await call('deleteHistory', { type: 'invalid-type', ids: [attendanceId] }); }
  catch { rejected = true; }
  const readOnly = new Database(path.join(state.profile, 'store.db'), { readonly: true });
  let count: number;
  try { count = (readOnly.prepare('SELECT COUNT(*) n FROM attendances WHERE id=?').get(attendanceId) as { n: number }).n; }
  finally { readOnly.close(); }
  expect.soft(rejected, 'invalid type must be rejected').toBe(true);
  expect(count, 'attendance row must be preserved').toBe(1);
});
