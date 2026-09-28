import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import Database from 'better-sqlite3';
import { afterEach, describe, expect, it } from 'vitest';
import { schema } from '../../../backend/src/database/schema';
import { createBackupBundle, inspectAndStageBackupBundle } from '../../../backend/src/domain/backup/backupBundle';
import { validateBackupDatabase } from '../../../backend/src/domain/backup/backupValidation';

const roots: string[] = [];
afterEach(() => { for (const root of roots.splice(0)) fs.rmSync(root, { recursive: true, force: true }); });

function integratedFixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'store-j6-profile-')); roots.push(root);
  const databasePath = path.join(root, 'store.db');
  const mediaRoot = path.join(root, 'media', 'articles'); fs.mkdirSync(mediaRoot, { recursive: true });
  const jpg = '10000000-0000-4000-8000-000000000001.jpg';
  const png = '20000000-0000-4000-8000-000000000002.png';
  fs.writeFileSync(path.join(mediaRoot, jpg), Buffer.from([0xff, 0xd8, 0xff, 0xdb, 1]));
  fs.writeFileSync(path.join(mediaRoot, png), Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1]));
  const db = new Database(databasePath); db.pragma('foreign_keys = ON'); db.exec(schema);
  const user = db.prepare('INSERT INTO users(username,password_hash,role,first_name,last_name,initials,active) VALUES(?,?,?,?,?,?,1)');
  user.run('owner', 'hash', 'owner', 'Primary', 'Owner', 'PO');
  user.run('manager', 'hash', 'manager', 'Manager', 'A', 'MA');
  user.run('employee-a', 'hash', 'employee', 'Employee', 'A', 'EA');
  user.run('employee-b', 'hash', 'employee', 'Employee', 'B', 'EB');
  db.prepare('INSERT INTO products(reference,name,category,price,stock_quantity,min_stock_threshold,image_ref) VALUES(?,?,?,?,?,?,?)')
    .run('A-1', 'Article JPEG', 'A', 10, 12, 2, jpg);
  db.prepare('INSERT INTO products(reference,name,category,price,stock_quantity,min_stock_threshold,image_ref) VALUES(?,?,?,?,?,?,?)')
    .run('B-1', 'Article PNG', 'B', 20, 7, 1, png);
  db.prepare('INSERT INTO products(reference,name,category,price,stock_quantity,min_stock_threshold,image_ref) VALUES(?,?,?,?,?,?,NULL)')
    .run('C-1', 'Article sans image', 'B', 5, 3, 1);
  db.prepare("INSERT INTO suppliers(name) VALUES('Supplier A'),('Supplier B')").run();
  db.prepare("INSERT INTO purchases(reference,supplier_id,status,total_amount,created_by,validated_by,validated_at) VALUES('ACH-J6',1,'VALIDATED',12,2,2,'2026-09-20T10:00:00Z')").run();
  db.prepare('INSERT INTO purchase_items(purchase_id,product_id,quantity,unit_cost,total_line) VALUES(1,1,2,6,12)').run();
  db.prepare("INSERT INTO stock_movements(product_id,quantity,reason,reference_id,unit_price,created_at) VALUES(1,2,'purchase','1',6,'2026-09-20T10:00:00Z')").run();
  db.prepare("INSERT INTO inventory_counts(reference,status,created_by,validated_by,validated_at) VALUES('INV-J6','VALIDATED',2,2,'2026-09-21T10:00:00Z')").run();
  db.prepare('INSERT INTO inventory_count_lines(inventory_id,product_id,expected_quantity,counted_quantity) VALUES(1,2,8,7)').run();
  db.prepare("INSERT INTO stock_movements(product_id,quantity,reason,reference_id,unit_price,created_at) VALUES(2,-1,'inventory','1',20,'2026-09-21T10:00:00Z')").run();
  db.prepare("INSERT INTO invoices(id,employee_id,subtotal,total_amount,status,invoice_date) VALUES('FACT-J6',3,10,10,'validated','2026-09-22T10:00:00Z')").run();
  db.prepare("INSERT INTO invoice_lines(invoice_id,product_id,product_name,category,quantity,unit_price,total_line) VALUES('FACT-J6',1,'Article JPEG','A',1,10,10)").run();
  db.prepare("INSERT INTO stock_movements(product_id,quantity,reason,reference_id,unit_price,created_at) VALUES(1,-1,'sale','FACT-J6',10,'2026-09-22T10:00:00Z')").run();
  db.prepare("INSERT INTO attendances(employee_id,start_time,end_time,source,status) VALUES(3,'2026-09-22T08:00:00Z','2026-09-22T16:00:00Z','EXPLICIT','VALID'),(4,'2026-09-22T09:00:00Z',NULL,'EXPLICIT','VALID')").run();
  db.prepare("INSERT INTO messages(sender_id,recipient_type,recipient_id,subject,content) VALUES(3,'user',4,'J6','Message intégré')").run();
  db.prepare("INSERT INTO audit_logs(user_id,action,entity,entity_id) VALUES(2,'purchase_validated','purchase','1')").run();
  db.close();
  return { root, databasePath, mediaRoot, media: [jpg, png] };
}

describe('J.6 deterministic integrated profile', () => {
  it('uses an explicit temporary profile containing J.1-J.5 representative truth', () => {
    const value = integratedFixture();
    expect(path.resolve(value.root).startsWith(path.resolve(os.tmpdir()))).toBe(true);
    expect(path.resolve(value.root)).not.toBe(path.resolve(process.env.APPDATA || '.'));
    const db = new Database(value.databasePath, { readonly: true });
    try {
      expect(db.prepare('SELECT COUNT(*) count FROM users').get()).toEqual({ count: 4 });
      expect(db.prepare('SELECT COUNT(*) count FROM products').get()).toEqual({ count: 3 });
      expect(db.prepare('SELECT reason,reference_id referenceId FROM stock_movements ORDER BY id').all()).toEqual([
        { reason: 'purchase', referenceId: '1' }, { reason: 'inventory', referenceId: '1' }, { reason: 'sale', referenceId: 'FACT-J6' },
      ]);
      expect(db.prepare("SELECT COUNT(*) count FROM attendances WHERE source='EXPLICIT'").get()).toEqual({ count: 2 });
      expect(db.pragma('integrity_check')).toEqual([{ integrity_check: 'ok' }]);
      expect(db.pragma('foreign_key_check')).toEqual([]);
    } finally { db.close(); }
  });

  it('round-trips integrated DB and managed media after deliberate isolated mutations', () => {
    const value = integratedFixture(); const target = path.join(value.root, 'integrated.store-backup');
    createBackupBundle({ databaseSnapshot: value.databasePath, mediaRoot: value.mediaRoot, imageReferences: value.media, target, applicationVersion: '2.0.1', kind: 'manual' });
    const mutated = new Database(value.databasePath);
    mutated.prepare("UPDATE products SET stock_quantity=0,image_ref=NULL WHERE reference='A-1'").run();
    mutated.prepare("DELETE FROM messages WHERE subject='J6'").run(); mutated.close();
    fs.rmSync(path.join(value.mediaRoot, value.media[0]));
    const staged = inspectAndStageBackupBundle(target, path.join(value.root, 'restore-staging'));
    expect(staged.manifest.backupFormatVersion).toBe(2);
    expect(staged.manifest.media.map((entry) => entry.reference).sort()).toEqual([...value.media].sort());
    expect(JSON.stringify(staged.manifest)).not.toMatch(/password|hash|secret/i);
    const restored = new Database(staged.databasePath, { readonly: true });
    try {
      expect(validateBackupDatabase(restored)).toBe(true);
      expect(restored.prepare("SELECT stock_quantity stock,image_ref imageRef FROM products WHERE reference='A-1'").get()).toEqual({ stock: 12, imageRef: value.media[0] });
      expect(restored.prepare("SELECT content FROM messages WHERE subject='J6'").get()).toEqual({ content: 'Message intégré' });
      expect(restored.prepare("SELECT COUNT(*) count FROM attendances WHERE end_time IS NULL").get()).toEqual({ count: 1 });
      expect(restored.pragma('integrity_check')).toEqual([{ integrity_check: 'ok' }]);
      expect(restored.pragma('foreign_key_check')).toEqual([]);
    } finally { restored.close(); }
    for (const reference of value.media) expect(fs.existsSync(path.join(staged.mediaPath, reference))).toBe(true);
  });

  it('keeps integrated security and business boundaries encoded together', () => {
    const handlers = fs.readFileSync('backend/src/main/ipcHandlers.ts', 'utf8');
    const database = fs.readFileSync('backend/src/database/storeDatabase.ts', 'utf8');
    const app = fs.readFileSync('frontend/src/App.tsx', 'utf8');
    expect(handlers).not.toMatch(/dropElevation|lastActivityAt|api\.attendance/);
    expect(handlers).toContain("if (api.currentCashSession({ employeeId: session.id }))");
    expect(handlers).toContain('first.employeeId = session.id');
    expect(database).toContain("if (purchase.status === 'VALIDATED') return true");
    expect(app).toContain('sessionSafety.checkoutCritical');
    expect(app).toContain("window.confirm(t('abandonCartConfirm'))");
  });
});
