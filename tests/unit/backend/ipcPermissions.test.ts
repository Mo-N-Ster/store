import { describe, expect, it } from 'vitest';
import { isPermissionGranted } from '../../../backend/src/domain/rbac/ipcPermissions';
import { defaultPermissions } from '../../../backend/src/domain/rbac/permissionMatrix';

describe('IPC permission enforcement', () => {
  it('restricts backup, restore, reset and settings changes to administrators', () => {
    const admin = defaultPermissions('ADMIN');
    const manager = defaultPermissions('MANAGER');
    for (const method of ['backup', 'restoreBackup', 'reset', 'saveSettings']) {
      expect(isPermissionGranted(admin, method)).toBe(true);
      expect(isPermissionGranted(manager, method)).toBe(false);
    }
    expect(isPermissionGranted(admin, 'settings')).toBe(true);
    expect(isPermissionGranted(manager, 'settings')).toBe(false);
    expect(isPermissionGranted(defaultPermissions('CASHIER'), 'preferences')).toBe(true);
  });

  it('allows managers to manage products but keeps cashiers read-only', () => {
    expect(isPermissionGranted(defaultPermissions('MANAGER'), 'saveProduct')).toBe(true);
    expect(isPermissionGranted(defaultPermissions('CASHIER'), 'saveProduct')).toBe(false);
    expect(isPermissionGranted(defaultPermissions('CASHIER'), 'products')).toBe(true);
  });

  it('allows cashiers to validate sales but not delete invoices', () => {
    const cashier = defaultPermissions('CASHIER');
    expect(isPermissionGranted(cashier, 'createInvoice')).toBe(true);
    expect(isPermissionGranted(cashier, 'deleteInvoice')).toBe(false);
    expect(isPermissionGranted(cashier, 'printInvoice')).toBe(true);
    expect(isPermissionGranted(cashier, 'exportInvoicePdf')).toBe(true);
  });
});
