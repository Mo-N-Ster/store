import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, expect, it, vi } from 'vitest';

const env = vi.hoisted(() => ({ profile: '', handlers: new Map<string, (...args: any[]) => any>() }));
vi.mock('electron', () => ({
  app: { getPath: () => env.profile, getVersion: () => '2.0.1' },
  safeStorage: { isEncryptionAvailable: () => true },
  ipcMain: { handle: (name: string, handler: (...args: any[]) => any) => env.handlers.set(name, handler) },
  dialog: {}, shell: {},
}));
import { api, closeDatabase, initDatabase } from '../../../backend/src/database/storeDatabase';
import { publicIdentity } from '../../../backend/src/domain/user/publicIdentity';
import { registerIpcHandlers } from '../../../backend/src/main/ipcHandlers';

afterEach(() => {
  closeDatabase();
  if (env.profile) fs.rmSync(env.profile, { recursive: true, force: true });
});

it('allowlists identity fields even when new internal columns appear', () => {
  const dto = publicIdentity({ id: 7, username: 'safe', role: 'employee', password_hash: 'fixture', security_answer_hash: 'fixture', failed_login_attempts: 4, locked_until: 'fixture', failed_recovery_attempts: 2, recovery_locked_until: 'fixture', last_login_at: 'fixture', future_secret: 'fixture' });
  expect(Object.keys(dto).sort()).toEqual(['active', 'email', 'first_name', 'hire_date', 'id', 'initials', 'last_name', 'phone', 'photo', 'role', 'username'].sort());
});

it('filters every public identity route through real IPC handlers and retains backend verification', async () => {
  env.profile = fs.mkdtempSync(path.join(os.tmpdir(), 'store-identity-secrets-test-'));
  await initDatabase(); env.handlers.clear(); registerIpcHandlers();
  const event = { sender: { id: 401 } };
  const call = (method: string, input?: unknown) => env.handlers.get(`store:${method}`)!(event, input);
  const owner = { username: 'fixture-owner', email: 'owner@fixture.invalid', firstName: 'Test', lastName: 'Owner', password: 'Fixture-only-123', securityQuestion: 'Fixture question', securityAnswer: 'Fixture answer' };
  const responses: unknown[] = [];
  const created = await call('setupAdmin', owner); responses.push(created);
  responses.push(await call('login', { identifier: owner.username, password: owner.password, role: 'manager' }));
  responses.push(await call('switchUser', { identifier: owner.username, password: owner.password }));
  responses.push(await call('session'));
  const employee = await call('saveUser', { username: 'fixture-employee', firstName: 'Test', lastName: 'Employee', role: 'employee', password: 'Employee-only-123', securityQuestion: 'Fixture question', securityAnswer: 'Fixture answer' });
  responses.push(employee.user); // Explicit one-time credential issuance is a separate established contract.
  responses.push(await call('saveUser', { id: employee.user.id, username: 'fixture-employee', firstName: 'Test', lastName: 'Employee', role: 'employee' }));
  for (const method of ['users', 'messageRecipients', 'attendanceSheet', 'attendanceHistory', 'attendanceStatuses', 'systemDiagnostics', 'auditLogs']) responses.push(await call(method));
  responses.push(await call('userPermissions', { userId: employee.user.id }));
  responses.push(await call('forgotPasswordQuestion', owner.username));
  responses.push(await call('securityQuestion', created.id));
  const keys = (value: unknown): string[] => !value || typeof value !== 'object' ? [] : Object.entries(value).flatMap(([key, child]) => [key, ...keys(child)]);
  const forbidden = ['password_hash', 'security_answer_hash', 'failed_login_attempts', 'locked_until', 'failed_recovery_attempts', 'recovery_locked_until', 'last_login_at'];
  for (const response of responses) for (const key of forbidden) expect(keys(response)).not.toContain(key);
  expect(await call('verifyAdmin', { password: owner.password })).toBe(true);
  expect(await call('recoverPassword', { id: created.id, answer: owner.securityAnswer, newPassword: 'Changed-only-123' })).toBe(true);
  expect((await call('login', { identifier: owner.username, password: 'Changed-only-123', role: 'manager' })).id).toBe(created.id);
  const log = path.join(env.profile, 'logs', 'technical.jsonl');
  if (fs.existsSync(log)) for (const key of forbidden) expect(fs.readFileSync(log, 'utf8').includes(key)).toBe(false);
});

it('RF-002: never exposes authentication or recovery hashes in public identity responses', async () => {
  env.profile = fs.mkdtempSync(path.join(os.tmpdir(), 'store-identity-secrets-test-'));
  await initDatabase();
  const created = api.setupAdmin({ username: 'fixture-owner', email: 'owner@fixture.invalid', firstName: 'Test', lastName: 'Owner', password: 'Fixture-only-123', securityQuestion: 'Fixture question', securityAnswer: 'Fixture answer' });
  const loggedIn = api.login({ identifier: 'fixture-owner', password: 'Fixture-only-123', role: 'manager' });
  // Only inspect key names: never include hash values in assertion output.
  for (const response of [created, loggedIn]) {
    expect(Object.keys(response)).not.toContain('password_hash');
    expect(Object.keys(response)).not.toContain('security_answer_hash');
  }
});
