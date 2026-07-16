import Database from 'better-sqlite3';
import bcrypt from 'bcryptjs';
import { app } from 'electron';
import fs from 'node:fs';
import path from 'node:path';
import { schema } from './schema.js';
import type { UserInput } from '../domain/user/user.types.js';
import { validateUser } from '../domain/user/user.validators.js';
import type { ProductInput } from '../domain/product/product.types.js';
import { validateProduct } from '../domain/product/product.validators.js';
import type { SaleLineInput } from '../domain/sale/sale.types.js';
import { sendEmail } from '../services/emailService.js';

let db: Database.Database;
let databaseFile = '';
const now = () => new Date().toISOString();
const initials = (first: string, last: string) => `${first[0] ?? ''}${last[0] ?? ''}`.toUpperCase();

export function initDatabase() {
  const dir = app.getPath('userData');
  fs.mkdirSync(dir, { recursive: true });
  databaseFile = path.join(dir, 'store.db');
  db = new Database(databaseFile);
  db.pragma('journal_mode = WAL');
  db.exec(schema);
  dailyBackup();
}

export const api = {
  needsSetup: () =>
    (
      db.prepare('SELECT COUNT(*) count FROM users WHERE role = ?').get('admin') as {
        count: number;
      }
    ).count === 0,
  setupAdmin: (input: UserInput) => {
    if (!api.needsSetup()) throw new Error('SETUP_ALREADY_COMPLETED');
    validateUser({ ...input, role: 'admin' }, true);
    const hash = bcrypt.hashSync(input.password!, 10);
    const result = db
      .prepare(
        `INSERT INTO users(username,email,password_hash,role,first_name,last_name,initials,phone,hire_date,security_question,security_answer_hash)
      VALUES(?,?,?,?,?,?,?,?,?,?,?)`,
      )
      .run(
        input.username.trim(),
        input.email?.trim().toLowerCase() || null,
        hash,
        'admin',
        input.firstName.trim(),
        input.lastName.trim(),
        initials(input.firstName, input.lastName),
        input.phone ?? '',
        input.hireDate ?? null,
        input.securityQuestion?.trim() || null,
        input.securityAnswer
          ? bcrypt.hashSync(input.securityAnswer.trim().toLowerCase(), 10)
          : null,
      );
    return publicUser(Number(result.lastInsertRowid));
  },
  login: ({
    identifier,
    password,
    role,
  }: {
    identifier: string;
    password: string;
    role: string;
  }) => {
    const user = db
      .prepare('SELECT * FROM users WHERE (username = ? OR email = ?) AND role = ? AND active = 1')
      .get(identifier, identifier.toLowerCase(), role) as any;
    if (!user || !bcrypt.compareSync(password, user.password_hash))
      throw new Error('INVALID_CREDENTIALS');
    return stripPassword(user);
  },
  verifyAdmin: ({ id, password }: { id: number; password: string }) => {
    const user = db
      .prepare("SELECT * FROM users WHERE id=? AND role='admin' AND active=1")
      .get(id) as any;
    return Boolean(user && bcrypt.compareSync(password, user.password_hash));
  },
  users: () =>
    db
      .prepare(
        'SELECT id,username,email,role,first_name firstName,last_name lastName,initials,phone,hire_date hireDate,active,security_question securityQuestion FROM users ORDER BY active DESC,last_name',
      )
      .all(),
  saveUser: (input: UserInput & { id?: number }) => {
    validateUser(input, false);
    const role = input.role ?? 'employee';
    if (
      !input.id &&
      role === 'admin' &&
      (!input.securityQuestion?.trim() || !input.securityAnswer?.trim())
    )
      throw new Error('SECURITY_QUESTION_REQUIRED');
    if (input.id) {
      const fields = [
        input.username.trim(),
        input.email?.trim().toLowerCase() || null,
        role,
        input.firstName.trim(),
        input.lastName.trim(),
        initials(input.firstName, input.lastName),
        input.phone ?? '',
        input.hireDate ?? null,
        input.active === false ? 0 : 1,
        input.id,
      ];
      db.prepare(
        'UPDATE users SET username=?,email=?,role=?,first_name=?,last_name=?,initials=?,phone=?,hire_date=?,active=? WHERE id=?',
      ).run(...fields);
      if (input.password)
        db.prepare('UPDATE users SET password_hash=? WHERE id=?').run(
          bcrypt.hashSync(input.password, 10),
          input.id,
        );
      if (role === 'admin' && input.securityQuestion && input.securityAnswer)
        db.prepare('UPDATE users SET security_question=?,security_answer_hash=? WHERE id=?').run(
          input.securityQuestion.trim(),
          bcrypt.hashSync(input.securityAnswer.trim().toLowerCase(), 10),
          input.id,
        );
      enforceSingleAdmin(input.id, role);
      return publicUser(input.id);
    }
    const password = input.password || temporaryPassword();
    const result = db
      .prepare(
        `INSERT INTO users(username,email,password_hash,role,first_name,last_name,initials,phone,hire_date,active,security_question,security_answer_hash) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)`,
      )
      .run(
        input.username.trim(),
        input.email?.trim().toLowerCase() || null,
        bcrypt.hashSync(password, 10),
        role,
        input.firstName.trim(),
        input.lastName.trim(),
        initials(input.firstName, input.lastName),
        input.phone ?? '',
        input.hireDate ?? null,
        1,
        role === 'admin' ? input.securityQuestion?.trim() : null,
        role === 'admin' && input.securityAnswer
          ? bcrypt.hashSync(input.securityAnswer.trim().toLowerCase(), 10)
          : null,
      );
    enforceSingleAdmin(Number(result.lastInsertRowid), role);
    return { user: publicUser(Number(result.lastInsertRowid)), temporaryPassword: password };
  },
  resetPassword: (id: number) => {
    const target = db.prepare('SELECT role FROM users WHERE id=?').get(id) as
      { role: string } | undefined;
    if (!target || target.role === 'admin') throw new Error('SECURITY_QUESTION_REQUIRED');
    const password = temporaryPassword();
    db.prepare('UPDATE users SET password_hash=? WHERE id=?').run(
      bcrypt.hashSync(password, 10),
      id,
    );
    return password;
  },
  securityQuestion: (id: number) => {
    const row = db
      .prepare("SELECT security_question question FROM users WHERE id=? AND role='admin'")
      .get(id) as { question: string } | undefined;
    if (!row?.question) throw new Error('QUESTION_NOT_CONFIGURED');
    return row.question;
  },
  resetManagerPassword: ({
    id,
    answer,
    newPassword,
  }: {
    id: number;
    answer: string;
    newPassword: string;
  }) => {
    if (newPassword.length < 8) throw new Error('WEAK_PASSWORD');
    const row = db
      .prepare("SELECT security_answer_hash hash FROM users WHERE id=? AND role='admin'")
      .get(id) as { hash: string } | undefined;
    if (!row?.hash || !bcrypt.compareSync(answer.trim().toLowerCase(), row.hash))
      throw new Error('INVALID_SECURITY_ANSWER');
    db.prepare('UPDATE users SET password_hash=? WHERE id=?').run(
      bcrypt.hashSync(newPassword, 10),
      id,
    );
    return true;
  },
  products: ({ search = '', category = '' } = {}) =>
    db
      .prepare(
        `SELECT id,name,hashtag,category,description,price,stock_quantity stockQuantity,min_stock_threshold minStockThreshold
    FROM products WHERE deleted_at IS NULL AND (?='' OR name LIKE ? OR category LIKE ? OR hashtag LIKE ?) AND (?='' OR category=?) ORDER BY name`,
      )
      .all(search, `%${search}%`, `%${search}%`, `%${search}%`, category, category),
  saveProduct: (input: ProductInput & { id?: number }) => {
    validateProduct(input);
    if (input.id) {
      const old = db
        .prepare('SELECT stock_quantity stock FROM products WHERE id=?')
        .get(input.id) as { stock: number };
      db.prepare(
        `UPDATE products SET name=?,hashtag=?,category=?,description=?,price=?,stock_quantity=?,min_stock_threshold=?,updated_at=? WHERE id=?`,
      ).run(
        input.name,
        input.hashtag ?? '',
        input.category,
        input.description ?? '',
        input.price,
        input.stockQuantity,
        input.minStockThreshold,
        now(),
        input.id,
      );
      const delta = input.stockQuantity - old.stock;
      if (delta)
        db.prepare('INSERT INTO stock_movements(product_id,quantity,reason) VALUES(?,?,?)').run(
          input.id,
          delta,
          'adjustment',
        );
      checkStock(input.id);
      return input.id;
    }
    const r = db
      .prepare(
        `INSERT INTO products(name,hashtag,category,description,price,stock_quantity,min_stock_threshold) VALUES(?,?,?,?,?,?,?)`,
      )
      .run(
        input.name,
        input.hashtag ?? '',
        input.category,
        input.description ?? '',
        input.price,
        input.stockQuantity,
        input.minStockThreshold,
      );
    const id = Number(r.lastInsertRowid);
    if (input.stockQuantity)
      db.prepare('INSERT INTO stock_movements(product_id,quantity,reason) VALUES(?,?,?)').run(
        id,
        input.stockQuantity,
        'initial',
      );
    checkStock(id);
    return id;
  },
  deleteProduct: ({ id, userId }: { id: number; userId: number }) => {
    db.prepare('UPDATE products SET deleted_at=?,stock_quantity=0 WHERE id=?').run(now(), id);
    audit(userId, 'delete', 'product', String(id));
  },
  createInvoice: ({
    employeeId,
    lines,
    discount = 0,
  }: {
    employeeId: number;
    lines: SaleLineInput[];
    discount?: number;
  }) =>
    db.transaction(() => {
      if (!lines.length || discount < 0) throw new Error('INVALID_INVOICE');
      const products = lines.map((line) => ({
        line,
        product: db
          .prepare('SELECT * FROM products WHERE id=? AND deleted_at IS NULL')
          .get(line.productId) as any,
      }));
      for (const { line, product } of products)
        if (!product || line.quantity <= 0 || product.stock_quantity < line.quantity)
          throw new Error('INSUFFICIENT_STOCK');
      const subtotal = products.reduce((sum, x) => sum + x.line.quantity * x.product.price, 0);
      const applied = Math.min(discount, subtotal);
      const total = subtotal - applied;
      const base = invoiceId();
      let id = base;
      let n = 1;
      while (db.prepare('SELECT 1 FROM invoices WHERE id=?').get(id)) id = `${base}-${n++}`;
      db.prepare(
        'INSERT INTO invoices(id,employee_id,invoice_date,subtotal,total_amount,discount) VALUES(?,?,?,?,?,?)',
      ).run(id, employeeId, now(), subtotal, total, applied);
      for (const { line, product } of products) {
        db.prepare(
          'INSERT INTO invoice_lines(invoice_id,product_id,product_name,category,quantity,unit_price,total_line) VALUES(?,?,?,?,?,?,?)',
        ).run(
          id,
          product.id,
          product.name,
          product.category,
          line.quantity,
          product.price,
          line.quantity * product.price,
        );
        db.prepare(
          'UPDATE products SET stock_quantity=stock_quantity-?,updated_at=? WHERE id=?',
        ).run(line.quantity, now(), product.id);
        db.prepare(
          'INSERT INTO stock_movements(product_id,quantity,reason,reference_id) VALUES(?,?,?,?)',
        ).run(product.id, -line.quantity, 'sale', id);
        checkStock(product.id);
      }
      return invoiceDetail(id);
    })(),
  invoices: ({ from = '', to = '', search = '' } = {}) =>
    db
      .prepare(
        `SELECT i.id,i.invoice_date invoiceDate,i.total_amount totalAmount,i.discount,u.first_name||' '||u.last_name seller FROM invoices i JOIN users u ON u.id=i.employee_id WHERE (?='' OR date(i.invoice_date)>=date(?)) AND (?='' OR date(i.invoice_date)<=date(?)) AND (?='' OR i.id LIKE ? OR u.username LIKE ?) ORDER BY i.invoice_date DESC`,
      )
      .all(from, from, to, to, search, `%${search}%`, `%${search}%`),
  invoice: (id: string) => invoiceDetail(id),
  deleteInvoice: ({ id, userId }: { id: string; userId: number }) => {
    db.transaction(() => {
      const lines = db
        .prepare('SELECT product_id productId,quantity FROM invoice_lines WHERE invoice_id=?')
        .all(id) as any[];
      for (const l of lines)
        if (l.productId)
          db.prepare('UPDATE products SET stock_quantity=stock_quantity+? WHERE id=?').run(
            l.quantity,
            l.productId,
          );
      db.prepare('DELETE FROM invoices WHERE id=?').run(id);
      audit(userId, 'delete', 'invoice', id);
    })();
  },
  attendance: ({ employeeId, present }: { employeeId: number; present: boolean }) => {
    const open = db
      .prepare(
        'SELECT id FROM attendances WHERE employee_id=? AND end_time IS NULL ORDER BY id DESC LIMIT 1',
      )
      .get(employeeId) as any;
    if (present) {
      if (open) throw new Error('SERVICE_ALREADY_OPEN');
      db.prepare('INSERT INTO attendances(employee_id,start_time) VALUES(?,?)').run(
        employeeId,
        now(),
      );
    } else {
      if (!open) throw new Error('NO_OPEN_SERVICE');
      db.prepare('UPDATE attendances SET end_time=? WHERE id=?').run(now(), open.id);
    }
  },
  messages: ({ userId, role }: { userId: number; role: string }) =>
    db
      .prepare(
        `SELECT m.*,u.first_name||' '||u.last_name sender FROM messages m LEFT JOIN users u ON u.id=m.sender_id WHERE (?='admin' AND m.recipient_type='admin') OR (?='employee' AND (m.recipient_type='all' OR m.recipient_id=?)) ORDER BY m.created_at DESC`,
      )
      .all(role, role, userId),
  sendMessage: ({
    senderId,
    recipientType,
    recipientId,
    subject,
    content,
  }: {
    senderId: number;
    recipientType: string;
    recipientId?: number;
    subject: string;
    content: string;
  }) => {
    if (!subject.trim() || !content.trim()) throw new Error('INVALID_MESSAGE');
    return db
      .prepare(
        'INSERT INTO messages(sender_id,recipient_type,recipient_id,subject,content) VALUES(?,?,?,?,?)',
      )
      .run(senderId, recipientType, recipientId ?? null, subject.trim(), content.trim())
      .lastInsertRowid;
  },
  markMessage: ({ id, isRead }: { id: number; isRead: boolean }) =>
    db.prepare('UPDATE messages SET is_read=? WHERE id=?').run(isRead ? 1 : 0, id),
  deleteMessage: (id: number) => db.prepare('DELETE FROM messages WHERE id=?').run(id),
  notifications: () => db.prepare('SELECT * FROM notifications ORDER BY created_at DESC').all(),
  dashboard: () => ({
    products: (db.prepare('SELECT COUNT(*) n FROM products WHERE deleted_at IS NULL').get() as any)
      .n,
    lowStock: (
      db
        .prepare(
          'SELECT COUNT(*) n FROM products WHERE deleted_at IS NULL AND stock_quantity<=min_stock_threshold',
        )
        .get() as any
    ).n,
    salesToday: (
      db
        .prepare("SELECT COUNT(*) n FROM invoices WHERE date(invoice_date)=date('now','localtime')")
        .get() as any
    ).n,
    revenueToday: (
      db
        .prepare(
          "SELECT COALESCE(SUM(total_amount),0) n FROM invoices WHERE date(invoice_date)=date('now','localtime')",
        )
        .get() as any
    ).n,
    revenueMonth: (
      db
        .prepare(
          "SELECT COALESCE(SUM(total_amount),0) n FROM invoices WHERE strftime('%Y-%m',invoice_date)=strftime('%Y-%m','now','localtime')",
        )
        .get() as any
    ).n,
    salesChart: db
      .prepare(
        "SELECT date(invoice_date) label,SUM(total_amount) value FROM invoices WHERE invoice_date>=datetime('now','-30 day') GROUP BY date(invoice_date) ORDER BY label",
      )
      .all(),
    attendanceChart: db
      .prepare(
        "SELECT u.first_name||' '||u.last_name label,ROUND(SUM((julianday(a.end_time)-julianday(a.start_time))*24),2) value FROM attendances a JOIN users u ON u.id=a.employee_id WHERE a.end_time IS NOT NULL GROUP BY a.employee_id ORDER BY value DESC",
      )
      .all(),
  }),
  settings: () =>
    Object.fromEntries(
      (db.prepare('SELECT key,value FROM settings').all() as any[]).map((x) => [x.key, x.value]),
    ),
  saveSettings: (values: Record<string, string>) =>
    db.transaction(() => {
      const stmt = db.prepare(
        'INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value',
      );
      for (const [k, v] of Object.entries(values)) stmt.run(k, v);
    })(),
  testEmail: async () => {
    const settings = settingsObject();
    await sendEmail(
      smtpConfig(settings),
      settings.email,
      'Test SMTP STORE',
      'La configuration SMTP de STORE fonctionne correctement.',
    );
    return true;
  },
  backup: () => createBackup(),
  importProducts: (filePath: string) => {
    const rows = fs
      .readFileSync(filePath, 'utf8')
      .replace(/^\uFEFF/, '')
      .split(/\r?\n/)
      .filter(Boolean);
    if (rows.length < 2) throw new Error('EMPTY_CSV');
    const headers = parseCsvLine(rows[0]).map((value) => value.trim());
    const required = ['name', 'category', 'price', 'stockQuantity', 'minStockThreshold'];
    if (required.some((key) => !headers.includes(key))) throw new Error('INVALID_CSV_HEADERS');
    return db.transaction(() =>
      rows.slice(1).reduce((count, row) => {
        const values = parseCsvLine(row);
        const item = Object.fromEntries(headers.map((key, index) => [key, values[index] ?? '']));
        const input = {
          name: item.name,
          hashtag: item.hashtag,
          category: item.category,
          description: item.description,
          price: Number(item.price),
          stockQuantity: Number(item.stockQuantity),
          minStockThreshold: Number(item.minStockThreshold),
        };
        validateProduct(input);
        const existing = db
          .prepare('SELECT id FROM products WHERE name=? AND deleted_at IS NULL')
          .get(input.name) as { id: number } | undefined;
        api.saveProduct({ ...input, id: existing?.id });
        return count + 1;
      }, 0),
    )();
  },
  restoreBackup: (filePath: string) => {
    if (!/\.(db|sqlite)$/i.test(filePath)) throw new Error('INVALID_BACKUP');
    createBackup();
    db.close();
    fs.copyFileSync(filePath, databaseFile);
    db = new Database(databaseFile);
    db.pragma('journal_mode = WAL');
    db.exec(schema);
    return true;
  },
  reset: ({ adminId, password }: { adminId: number; password: string }) => {
    if (!api.verifyAdmin({ id: adminId, password })) throw new Error('INVALID_CREDENTIALS');
    createBackup();
    db.transaction(() => {
      for (const table of [
        'invoice_lines',
        'invoices',
        'attendances',
        'messages',
        'notifications',
        'stock_movements',
        'products',
        'audit_logs',
        'settings',
        'users',
      ])
        db.prepare(`DELETE FROM ${table}`).run();
    })();
    return true;
  },
};

function stripPassword(u: any) {
  const safe = { ...u };
  delete safe.password_hash;
  return safe;
}
function publicUser(id: number) {
  return stripPassword(db.prepare('SELECT * FROM users WHERE id=?').get(id));
}
function enforceSingleAdmin(id: number, role: string) {
  if (role === 'admin')
    db.prepare("UPDATE users SET role='employee' WHERE role='admin' AND id<>?").run(id);
}
function temporaryPassword() {
  return `Store-${Math.random().toString(36).slice(2, 8)}!`;
}
function invoiceId() {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return `FACT-${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
}
function invoiceDetail(id: string) {
  return {
    invoice: db
      .prepare(
        `SELECT i.*,u.first_name||' '||u.last_name seller,u.initials FROM invoices i JOIN users u ON u.id=i.employee_id WHERE i.id=?`,
      )
      .get(id),
    lines: db.prepare('SELECT * FROM invoice_lines WHERE invoice_id=?').all(id),
  };
}
function checkStock(id: number) {
  const p = db.prepare('SELECT * FROM products WHERE id=?').get(id) as any;
  if (!p) return;
  if (p.stock_quantity <= p.min_stock_threshold) {
    const exists = db
      .prepare(
        "SELECT 1 FROM notifications WHERE product_id=? AND type='stock_alert' AND resolved_at IS NULL",
      )
      .get(id);
    if (!exists) {
      db.prepare(
        "INSERT INTO notifications(type,product_id,message) VALUES('stock_alert',?,?)",
      ).run(id, `${p.name}: stock ${p.stock_quantity}, seuil ${p.min_stock_threshold}`);
      const settings = settingsObject();
      if (settings.email && settings.smtpHost)
        void sendEmail(
          smtpConfig(settings),
          settings.email,
          `Alerte stock: ${p.name}`,
          `${p.name}: stock actuel ${p.stock_quantity}, seuil ${p.min_stock_threshold}`,
        ).catch(() => undefined);
    }
  } else
    db.prepare(
      'UPDATE notifications SET resolved_at=? WHERE product_id=? AND resolved_at IS NULL',
    ).run(now(), id);
}
function audit(userId: number, action: string, entity: string, entityId: string) {
  db.prepare('INSERT INTO audit_logs(user_id,action,entity,entity_id) VALUES(?,?,?,?)').run(
    userId,
    action,
    entity,
    entityId,
  );
}
function backupDir() {
  const d = path.join(app.getPath('userData'), 'backups');
  fs.mkdirSync(d, { recursive: true });
  return d;
}
function createBackup() {
  const name = `store-${new Date().toISOString().replace(/[:.]/g, '-')}.db`;
  const target = path.join(backupDir(), name);
  db.backup(target);
  return target;
}
function dailyBackup() {
  const dir = backupDir();
  const files = fs
    .readdirSync(dir)
    .filter((f) => f.endsWith('.db'))
    .sort()
    .reverse();
  const today = new Date().toISOString().slice(0, 10);
  if (!files.some((f) => f.includes(today))) createBackup();
  for (const f of files.slice(7)) fs.rmSync(path.join(dir, f));
}
function parseCsvLine(line: string) {
  const values: string[] = [];
  let current = '';
  let quoted = false;
  for (let index = 0; index < line.length; index++) {
    const char = line[index];
    if (char === '"' && quoted && line[index + 1] === '"') {
      current += '"';
      index++;
    } else if (char === '"') quoted = !quoted;
    else if (char === ',' && !quoted) {
      values.push(current);
      current = '';
    } else current += char;
  }
  values.push(current);
  return values;
}
function settingsObject() {
  return Object.fromEntries(
    (db.prepare('SELECT key,value FROM settings').all() as { key: string; value: string }[]).map(
      (item) => [item.key, item.value],
    ),
  ) as Record<string, string>;
}
function smtpConfig(settings: Record<string, string>) {
  return {
    host: settings.smtpHost || '',
    port: Number(settings.smtpPort || 587),
    secure: settings.smtpSecure === 'true',
    user: settings.smtpUser || '',
    password: settings.smtpPassword || '',
    from: settings.smtpFrom || settings.smtpUser || '',
  };
}
