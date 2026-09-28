import { beforeEach, describe, expect, it, vi } from 'vitest';
import { registerIpcHandlers } from '../../../backend/src/main/ipcHandlers';
import { defaultPermissions } from '../../../backend/src/domain/rbac/permissionMatrix';
import { auditSession } from '../../../backend/src/domain/system/auditWriter';

const state = vi.hoisted(() => ({ handlers: new Map<string, (...args: any[]) => any>(), permissions: new Set<string>(), active: 1, role: 'owner', save: vi.fn(), targetRole: 'employee' }));
vi.mock('electron', () => ({ ipcMain: { handle: (name: string, handler: (...args: any[]) => any) => state.handlers.set(name, handler) }, dialog: {}, shell: {} }));
vi.mock('../../../backend/src/services/technicalLogger', () => ({ logTechnical: vi.fn() }));
vi.mock('../../../backend/src/database/storeDatabase', async () => {
  const { IPC_METHODS } = await import('../../../backend/src/ipc/channels');
  const special = new Set(['logout', 'session', 'exportBackup', 'openDataFolder', 'printInvoice', 'exportInvoicePdf', 'saveExport', 'exportReportPdf', 'emailReportPdf', 'selectFile', 'selectArticleImage']);
  const api = Object.fromEntries(IPC_METHODS.filter((name) => !special.has(name)).map((name) => [name, vi.fn(() => true)]));
  api.login = vi.fn(() => ({ id: 1, role: state.role, username: 'test' })) as any;
  api.users = vi.fn(() => [{ id: 2, role: state.targetRole }]) as any;
  api.saveUserPermissions = state.save;
  return { api, permissionsForUser: () => [...state.permissions], sessionAuthority: () => ({ id: 1, role: state.role, active: state.active }), sendReportEmail: vi.fn() };
});
const event = { sender: { id: 101 } };
const call = (name: string, input?: unknown) => state.handlers.get(`store:${name}`)!(event, input);
beforeEach(async () => {
  state.handlers.clear(); state.permissions = defaultPermissions('ADMIN'); state.active = 1; state.role = 'owner'; state.targetRole = 'employee'; state.save.mockReset();
  registerIpcHandlers(); await call('login', {});
});
describe('J.6R-A authoritative session refresh', () => {
  it('enforces new restrictions on the next request without a new login', async () => {
    await expect(call('products', {})).resolves.toBe(true);
    state.permissions.delete('PRODUCTS:READ');
    await expect(call('products', {})).rejects.toThrow('FORBIDDEN');
    const session = await call('session');
    expect(session.effectivePermissions.find((row: any) => row.module === 'PRODUCTS').actions).not.toContain('READ');
  });
  it('binds permission audit actor to the authenticated identity', async () => {
    state.save.mockImplementation(() => { expect(auditSession.getStore()).toEqual({ id: 1, displayName: 'test' }); return true; });
    await call('saveUserPermissions', { actorId: 999, userId: 2, denied: [] });
    expect(state.save).toHaveBeenCalledWith(expect.objectContaining({ actorId: 1 }));
    expect(auditSession.getStore()).toBeUndefined();
  });
  it('rejects permission editor access after administrative rights are removed', async () => {
    state.permissions = defaultPermissions('MANAGER'); state.role = 'manager';
    await expect(call('saveUserPermissions', {})).rejects.toThrow('FORBIDDEN');
  });
  it('invalidates an inactive authenticated user', async () => {
    state.active = 0;
    await expect(call('session')).rejects.toThrow('AUTH_REQUIRED');
    await expect(call('products', {})).rejects.toThrow('AUTH_REQUIRED');
  });
  it('blocks a manager relabelling a privileged target as an employee', async () => {
    state.role = 'manager'; state.permissions = defaultPermissions('MANAGER'); state.targetRole = 'owner';
    await expect(call('saveUser', { id: 2, role: 'employee' })).rejects.toThrow('FORBIDDEN');
  });
});
