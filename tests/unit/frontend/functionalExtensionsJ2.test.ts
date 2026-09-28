import fs from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('Phase J.2 renderer and consistency contracts', () => {
  it('offers optional choose, replace and remove image controls without raw filesystem access', () => {
    const products = fs.readFileSync('frontend/src/pages/Dashboard/products/ProductList.tsx', 'utf8');
    const preload = fs.readFileSync('backend/src/preload/index.cts', 'utf8');
    expect(products).toContain('productService.selectImage()');
    expect(products).toContain('removeImage');
    expect(products).toContain('imageToken: imageDraft?.token');
    expect(preload).toContain("'selectArticleImage'");
    expect(preload).not.toContain("require('node:fs')");
    expect(preload).not.toContain('ipcRenderer.invoke(channel');
  });
  it('keeps old media until the database transaction succeeds and compensates a failed replacement', () => {
    const database = fs.readFileSync('backend/src/database/storeDatabase.ts', 'utf8');
    const transaction = database.indexOf('const result = db.transaction');
    const committed = database.indexOf('persisted = true;', transaction);
    const oldCleanup = database.indexOf('removeUnreferencedArticleImage(result.oldImageRef!)', transaction);
    const compensation = database.indexOf('removeUnreferencedArticleImage(committed.reference)', transaction);
    expect(transaction).toBeGreaterThan(-1);
    expect(committed).toBeGreaterThan(transaction);
    expect(oldCleanup).toBeGreaterThan(committed);
    expect(compensation).toBeGreaterThan(transaction);
    expect(database).toContain('if (committed && !persisted)');
    expect(database).not.toContain('fs.rmSync(committed.target');
  });
  it('uses one complete backup engine for manual, automatic, pre-restore and pre-reset backups', () => {
    const database = fs.readFileSync('backend/src/database/storeDatabase.ts', 'utf8');
    expect(database).toContain("type BackupKind = 'manual' | 'auto' | 'pre-restore' | 'pre-reset'");
    expect(database).toContain("createBackup('pre-restore')");
    expect(database).toContain("createBackup('pre-reset')");
    expect(database).toContain("createBackup('auto')");
    expect(database).toContain('createBackupBundle({');
  });
  it('uses explicit accessible manual and automatic password modes', () => {
    const dialog = fs.readFileSync('frontend/src/pages/Dashboard/employees/PasswordResetDialog.tsx', 'utf8');
    expect(dialog).toContain('type="radio"');
    expect(dialog).toContain("t('passwordManual')");
    expect(dialog).toContain("t('passwordAutomatic')");
    expect(dialog).toContain('minLength={8}');
  });
});
