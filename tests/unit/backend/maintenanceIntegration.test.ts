import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import Database from 'better-sqlite3';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
const env = vi.hoisted(() => ({ profile: '' }));
vi.mock('electron', () => ({ app: { getPath: () => env.profile, getVersion: () => '2.0.1' }, safeStorage: { isEncryptionAvailable: () => true, encryptString: (text: string) => Buffer.from(text), decryptString: (value: Buffer) => value.toString() } }));
import { api, closeDatabase, initDatabase } from '../../../backend/src/database/storeDatabase';
import { auditSession } from '../../../backend/src/domain/system/auditWriter';
import { inspectArticleMedia, stageArticleImage } from '../../../backend/src/domain/media/articleMedia';
let ownerId = 0;
beforeEach(async () => {
  env.profile = fs.mkdtempSync(path.join(os.tmpdir(), 'store-maintenance-test-'));
  await initDatabase();
  ownerId = api.setupAdmin({ username: 'testowner', firstName: 'Test', lastName: 'Owner', email: 'test@example.com', password: 'Test-only-123', securityQuestion: 'Test question', securityAnswer: 'Test answer' }).id;
});
afterEach(() => { vi.restoreAllMocks(); closeDatabase(); fs.rmSync(env.profile, { recursive: true, force: true }); });
describe('Actual maintenance API on disposable profile', () => {
  const article = { name: 'Media test', category: 'Test', price: 1, stockQuantity: 0, minStockThreshold: 0 };
  function selected() {
    const source = path.join(env.profile, 'original.png');
    fs.writeFileSync(source, Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64'));
    return stageArticleImage(source, path.join(env.profile, 'media', 'articles')).token;
  }
  function media(reference: string) { return path.join(env.profile, 'media', 'articles', reference); }
  function failDelete(target: string) {
    const original = fs.rmSync;
    vi.spyOn(fs, 'rmSync').mockImplementation((file, options) => {
      if (String(file) === target) throw Object.assign(new Error('injected cleanup failure'), { code: 'EPERM' });
      return original(file, options);
    });
  }
  function rejectUpdate() {
    const database = new Database(path.join(env.profile, 'store.db'));
    database.exec("CREATE TRIGGER reject_media_update BEFORE UPDATE ON products BEGIN SELECT RAISE(ABORT,'injected DB failure'); END");
    database.close();
  }
  it('creates without image and keeps the same image on an ordinary update', () => {
    const id = api.saveProduct(article);
    expect((api.products()[0] as any).imageRef).toBeNull();
    const token = selected(); api.saveProduct({ ...article, id, imageToken: token });
    api.saveProduct({ ...article, id, price: 2 });
    expect((api.products()[0] as any).imageRef).toBe(token);
    expect(fs.existsSync(media(token))).toBe(true);
  });
  it.each([false, true])('replaces A with B despite obsolete cleanup failure=%s', (failure) => {
    const a = selected(); const id = api.saveProduct({ ...article, imageToken: a });
    const b = selected(); if (failure) failDelete(media(a));
    expect(api.saveProduct({ ...article, id, imageToken: b })).toBe(id);
    expect((api.products()[0] as any).imageRef).toBe(b);
    expect(fs.existsSync(media(b))).toBe(true);
    expect(fs.existsSync(media(a))).toBe(failure);
    expect(fs.existsSync(path.join(env.profile, 'original.png'))).toBe(true);
    expect(inspectArticleMedia([b], path.dirname(media(b)))).toEqual({ missing: [], recoverableOrphans: failure ? [a] : [] });
  });
  it.each([false, true])('preserves A on DB failure despite compensation cleanup failure=%s', (failure) => {
    const a = selected(); const id = api.saveProduct({ ...article, imageToken: a });
    const b = selected(); rejectUpdate(); if (failure) failDelete(media(b));
    expect(() => api.saveProduct({ ...article, id, imageToken: b })).toThrow('injected DB failure');
    expect((api.products()[0] as any).imageRef).toBe(a);
    expect(fs.existsSync(media(a))).toBe(true);
    expect(fs.existsSync(media(b))).toBe(failure);
  });
  it.each([false, true])('removes reference before obsolete cleanup, cleanup failure=%s', (failure) => {
    const a = selected(); const id = api.saveProduct({ ...article, imageToken: a });
    if (failure) failDelete(media(a));
    api.saveProduct({ ...article, id, removeImage: true });
    expect((api.products()[0] as any).imageRef).toBeNull();
    expect(fs.existsSync(media(a))).toBe(failure);
  });
  it('keeps the referenced file on removal DB failure', () => {
    const a = selected(); const id = api.saveProduct({ ...article, imageToken: a }); rejectUpdate();
    expect(() => api.saveProduct({ ...article, id, removeImage: true })).toThrow('injected DB failure');
    expect((api.products()[0] as any).imageRef).toBe(a); expect(fs.existsSync(media(a))).toBe(true);
  });
  it('rejects colliding target and preserves references including archived shared articles', () => {
    const a = selected(); const id = api.saveProduct({ ...article, imageToken: a });
    fs.copyFileSync(media(a), path.join(path.dirname(media(a)), '.staging', a));
    expect(() => api.saveProduct({ ...article, id, imageToken: a })).toThrow();
    expect(fs.existsSync(media(a))).toBe(true);
    const otherId = api.saveProduct({ ...article, name: 'Other' });
    const database = new Database(path.join(env.profile, 'store.db'));
    database.prepare('UPDATE products SET image_ref=? WHERE id=?').run(a, otherId); database.close();
    api.deleteProduct({ id: otherId, userId: ownerId });
    api.saveProduct({ ...article, id, removeImage: true });
    expect(fs.existsSync(media(a))).toBe(true);
  });
  it('round-trips actual article media through restart, backup and restore with SQLite integrity', async () => {
    const a = selected(); api.saveProduct({ ...article, imageToken: a });
    closeDatabase(); await initDatabase();
    expect((api.products()[0] as any).imageRef).toBe(a); expect(api.articleImage(a)).toBeTruthy();
    const backup = await api.backup(); await api.restoreBackup(backup);
    expect((api.products()[0] as any).imageRef).toBe(a); expect(api.articleImage(a)).toBeTruthy();
    expect(inspectArticleMedia([a], path.dirname(media(a))).missing).toEqual([]);
    const database = new Database(path.join(env.profile, 'store.db'), { readonly: true });
    expect(database.pragma('integrity_check')).toEqual([{ integrity_check: 'ok' }]);
    expect(database.pragma('foreign_key_check')).toEqual([]); database.close();
  });
  it('reports a missing reference read-only and refuses a backup with missing media', async () => {
    const a = selected(); api.saveProduct({ ...article, imageToken: a }); fs.rmSync(media(a));
    expect(inspectArticleMedia([a], path.dirname(media(a))).missing).toEqual([a]);
    expect((api.products()[0] as any).imageRef).toBe(a); expect(api.articleImage(a)).toBeNull();
    await expect(api.backup()).rejects.toThrow('BACKUP_FAILED');
  });
  it('preserves a committed article image if post-commit staging cleanup fails', () => {
    const mediaRoot = path.join(env.profile, 'media', 'articles');
    const source = path.join(env.profile, 'fixture.png');
    const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64');
    fs.writeFileSync(source, png);
    const staged = stageArticleImage(source, mediaRoot);
    const original = fs.rmSync;
    let injected = false;
    vi.spyOn(fs, 'rmSync').mockImplementation((target, options) => {
      if (String(target) === path.join(mediaRoot, '.staging', staged.token)) {
        injected = true;
        throw Object.assign(new Error('injected staging cleanup failure'), { code: 'EPERM' });
      }
      return original(target, options);
    });
    let saveError: unknown;
    try {
      api.saveProduct({ name: 'Release media fixture', category: 'Test', price: 1, stockQuantity: 1, minStockThreshold: 0, imageToken: staged.token });
    } catch (error) { saveError = error; }
    expect(injected).toBe(true);
    const product = (api.products() as { imageRef: string }[])[0];
    expect(product.imageRef).toBe(staged.token);
    // SQLite has committed: cleanup must never remove its referenced media.
    expect(fs.existsSync(path.join(mediaRoot, product.imageRef))).toBe(true);
    expect(api.articleImage(product.imageRef)).toBe(`data:image/png;base64,${png.toString('base64')}`);
    expect(saveError).toBeUndefined();
  });
  it('matches username, full name and recovery identity without case sensitivity while preserving password case', () => {
    expect(api.login({ identifier: ' TESTOWNER ', password: 'Test-only-123', role: 'manager' }).id).toBe(ownerId);
    expect(api.login({ identifier: 'OWNER test', password: 'Test-only-123', role: 'manager' }).id).toBe(ownerId);
    expect(api.forgotPasswordQuestion('TEST OWNER').id).toBe(ownerId);
    expect(() => api.login({ identifier: 'testowner', password: 'test-only-123', role: 'manager' })).toThrow('INVALID_CREDENTIALS');
    const employee = api.saveUser({ username: 'Élodie01', firstName: 'Élodie', lastName: 'Ndiaye', password: 'Employee-123', role: 'employee' });
    expect(api.login({ identifier: 'ÉLODIE NDIAYE', password: 'Employee-123', role: 'employee' }).id).toBe(employee.user.id);
    expect(api.switchUser({ currentUserId: ownerId, identifier: 'élodie01', password: 'Employee-123' }).id).toBe(employee.user.id);
  });
  it('snapshots the authenticated responsible user and register balance without changing historical values', () => {
    api.saveSettings({ currency: 'XAF' });
    auditSession.run({ id: ownerId, displayName: 'Test Owner' }, () => api.openCashSession({ employeeId: ownerId, openingAmount: 1250 }));
    const row = api.auditLogs({ action: 'cash_session_opened' })[0] as any;
    expect(row).toMatchObject({ responsibleId: ownerId, responsibleName: 'Test Owner', cashAmount: 1250, cashCurrency: 'XAF' });
    expect(row.cashReference).toBeTruthy();
    api.saveSettings({ currency: 'EUR' });
    expect(api.auditLogs({ action: 'cash_session_opened' })[0]).toMatchObject({ cashAmount: 1250, cashCurrency: 'XAF' });
    auditSession.run({ id: ownerId, displayName: 'Test Owner' }, () => api.closeCashSession({ employeeId: ownerId, countedAmount: 1200 }));
    expect(api.auditLogs({ action: 'cash_session_closed' })[0]).toMatchObject({ cashAmount: 1250, cashCurrency: 'EUR', responsibleId: ownerId });
    api.login({ identifier: 'testowner', password: 'Test-only-123', role: 'manager' });
    const unauthenticated = (api.auditLogs({}) as any[]).find((entry) => entry.action === 'login');
    if (unauthenticated) expect(unauthenticated.responsibleId).toBeNull();
  });
  it('persists settings across reload and never returns the SMTP password', async () => {
    api.saveSettings({ storeName: 'Test shop', currency: 'XAF', discountsEnabled: 'false', smtpPassword: 'fake-secret' });
    closeDatabase(); await initDatabase();
    expect(api.settings()).toMatchObject({ storeName: 'Test shop', currency: 'XAF', discountsEnabled: 'false' });
    expect(JSON.stringify(api.settings())).not.toContain('fake-secret');
    expect(JSON.stringify(api.systemDiagnostics())).not.toContain('fake-secret');
  });
  it('round-trips a bundle through the actual restore API', async () => {
    api.saveSettings({ storeName: 'Before' }); const backup = await api.backup();
    api.saveSettings({ storeName: 'After' }); await api.restoreBackup(backup);
    expect(api.settings()).toMatchObject({ storeName: 'Before' }); expect(api.needsSetup()).toBe(false);
  });
  it('rejects wrong reset credentials and creates a backup before a successful reset', async () => {
    await expect(api.reset({ adminId: ownerId, password: 'wrong' })).rejects.toThrow('OWNER_REQUIRED');
    expect(api.needsSetup()).toBe(false);
    await api.reset({ adminId: ownerId, password: 'Test-only-123' });
    expect(api.needsSetup()).toBe(true); expect(api.backups().length).toBeGreaterThan(0);
    closeDatabase(); await initDatabase(); expect(api.needsSetup()).toBe(true);
  });
  it('keeps the current shop after a corrupt restore is rejected', async () => {
    api.saveSettings({ storeName: 'Preserved' }); const corrupt = path.join(env.profile, 'invalid.store-backup'); fs.writeFileSync(corrupt, 'corrupt');
    await expect(api.restoreBackup(corrupt)).rejects.toThrow();
    expect(api.settings()).toMatchObject({ storeName: 'Preserved' }); expect(api.needsSetup()).toBe(false);
  });
  it('migrates a supported v15 database-only backup during restore', async () => {
    closeDatabase();
    const legacyPath = path.join(env.profile, 'legacy.sqlite'); fs.copyFileSync(path.join(env.profile, 'store.db'), legacyPath);
    const legacy = new Database(legacyPath); legacy.exec('DROP TABLE user_permission_denials; DELETE FROM schema_migrations WHERE version=16'); legacy.close();
    await initDatabase(); await api.restoreBackup(legacyPath);
    expect(api.needsSetup()).toBe(false); expect(api.userPermissions({ userId: ownerId }).denied).toEqual([]);
  });
  it('rolls back a filesystem failure during actual replacement', async () => {
    api.saveSettings({ storeName: 'Backup' }); const backup = await api.backup(); api.saveSettings({ storeName: 'Current' });
    const original = fs.copyFileSync; let injected = false;
    vi.spyOn(fs, 'copyFileSync').mockImplementation((source, target, flags) => {
      if (!injected && String(target) === path.join(env.profile, 'store.db')) { injected = true; throw new Error('injected replacement failure'); }
      return original(source, target, flags);
    });
    await expect(api.restoreBackup(backup)).rejects.toThrow('injected replacement failure');
    expect(api.settings()).toMatchObject({ storeName: 'Current' }); expect(api.needsSetup()).toBe(false);
  });
});
