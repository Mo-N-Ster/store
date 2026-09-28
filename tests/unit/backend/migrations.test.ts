import Database from 'better-sqlite3';
import { describe, expect, it } from 'vitest';
import { applyDatabaseMigrations, latestSchemaVersion } from '../../../backend/src/database/migrations';
import { schema } from '../../../backend/src/database/schema';

describe('database migrations', () => {
  it('records the current schema version and remains idempotent', () => {
    const database = new Database(':memory:');
    try {
      database.exec(schema);
      applyDatabaseMigrations(database);
      applyDatabaseMigrations(database);

      const rows = database
        .prepare('SELECT version,name FROM schema_migrations ORDER BY version')
        .all() as { version: number; name: string }[];

      expect(rows).toHaveLength(latestSchemaVersion);
      expect(rows.at(-1)).toEqual({
        version: latestSchemaVersion,
        name: 'sale-idempotency-command',
      });
      const invoiceLineColumns = database.prepare('PRAGMA table_info(invoice_lines)').all() as Array<{
        name: string;
      }>;
      expect(invoiceLineColumns.map((column) => column.name)).toContain('unit_cost');
      const emailColumns = database.prepare('PRAGMA table_info(email_report_logs)').all() as Array<{
        name: string;
      }>;
      expect(emailColumns.map((column) => column.name)).toEqual(
        expect.arrayContaining(['message_text', 'attachment', 'attempts', 'next_attempt_at', 'last_error', 'sent_at']),
      );
      const messageColumns = database.prepare('PRAGMA table_info(messages)').all() as Array<{
        name: string;
      }>;
      expect(messageColumns.map((column) => column.name)).toContain('request_id');
      const productColumns = database.prepare('PRAGMA table_info(products)').all() as Array<{ name: string }>;
      expect(productColumns.map((column) => column.name)).toContain('image_ref');
    } finally {
      database.close();
    }
  });

  it('maps legacy roles and seeds the initial permission matrix', () => {
    const database = new Database(':memory:');
    try {
      database.exec(schema);
      database
        .prepare(`INSERT INTO users(
          username,password_hash,role,first_name,last_name,initials
        ) VALUES(?,?,?,?,?,?)`)
        .run('owner-test', 'hash', 'owner', 'Owner', 'Test', 'OT');

      applyDatabaseMigrations(database);

      const assignment = database
        .prepare(`SELECT roles.code FROM user_roles JOIN roles ON roles.id=user_roles.role_id
          JOIN users ON users.id=user_roles.user_id WHERE users.username=?`)
        .get('owner-test') as { code: string };
      const adminPermissionCount = database
        .prepare(`SELECT COUNT(*) count FROM role_permissions rp
          JOIN roles r ON r.id=rp.role_id WHERE r.code='ADMIN'`)
        .get() as { count: number };

      expect(assignment.code).toBe('ADMIN');
      expect(adminPermissionCount.count).toBeGreaterThan(0);
      expect(
        database
          .prepare('SELECT employee_code employeeCode,status FROM employees WHERE user_id=1')
          .get(),
      ).toEqual({ employeeCode: 'EMP-000001', status: 'ACTIVE' });
    } finally {
      database.close();
    }
  });

  it('upgrades a legacy stock movement table without losing its data', () => {
    const database = new Database(':memory:');
    try {
      database.exec(`
        PRAGMA foreign_keys = ON;
        CREATE TABLE users (
          id INTEGER PRIMARY KEY,
          username TEXT NOT NULL,
          password_hash TEXT NOT NULL,
          role TEXT NOT NULL,
          first_name TEXT NOT NULL DEFAULT '',
          last_name TEXT NOT NULL DEFAULT '',
          phone TEXT,
          email TEXT,
          hire_date TEXT,
          active INTEGER NOT NULL DEFAULT 1,
          created_at TEXT DEFAULT CURRENT_TIMESTAMP
        );
        CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE audit_logs (
          id INTEGER PRIMARY KEY,
          user_id INTEGER,
          action TEXT NOT NULL,
          entity TEXT NOT NULL,
          entity_id TEXT,
          details TEXT,
          created_at TEXT DEFAULT CURRENT_TIMESTAMP
        );
        CREATE TABLE attendances (
          id INTEGER PRIMARY KEY,
          employee_id INTEGER NOT NULL REFERENCES users(id),
          start_time TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          end_time TEXT
        );
        CREATE TABLE invoices (
          id TEXT PRIMARY KEY,
          employee_id INTEGER NOT NULL REFERENCES users(id),
          invoice_date TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          subtotal REAL NOT NULL,
          total_amount REAL NOT NULL,
          discount REAL DEFAULT 0,
          status TEXT DEFAULT 'validated'
        );
        CREATE TABLE products (
          id INTEGER PRIMARY KEY,
          name TEXT NOT NULL,
          hashtag TEXT,
          category TEXT NOT NULL DEFAULT 'Divers',
          price REAL NOT NULL,
          stock_quantity INTEGER NOT NULL DEFAULT 0,
          min_stock_threshold INTEGER NOT NULL DEFAULT 0,
          updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
          deleted_at TEXT,
          created_at TEXT DEFAULT CURRENT_TIMESTAMP
        );
        CREATE TABLE stock_movements (
          id INTEGER PRIMARY KEY,
          product_id INTEGER NOT NULL REFERENCES products(id),
          quantity INTEGER NOT NULL,
          reason TEXT NOT NULL,
          reference_id TEXT,
          created_at TEXT DEFAULT CURRENT_TIMESTAMP
        );
        INSERT INTO products(id,name,price) VALUES(1,'Test',25);
        INSERT INTO stock_movements(id,product_id,quantity,reason) VALUES(1,1,4,'initial');
      `);

      applyDatabaseMigrations(database);
      expect(() => database.exec(schema)).not.toThrow();

      const movement = database
        .prepare('SELECT quantity,unit_price unitPrice FROM stock_movements WHERE id=1')
        .get() as { quantity: number; unitPrice: number };
      expect(movement).toEqual({ quantity: 4, unitPrice: 25 });
      expect(database.prepare('SELECT COUNT(*) count FROM product_price_history').get()).toEqual({
        count: 1,
      });
      expect(database.prepare('SELECT reference FROM products WHERE id=1').get()).toEqual({
        reference: 'PROD-000001',
      });
      expect(
        database
          .prepare("SELECT COUNT(*) count FROM sqlite_master WHERE type='table' AND name IN ('suppliers','purchases','purchase_items')")
          .get(),
      ).toEqual({ count: 3 });
      expect(
        database
          .prepare("SELECT COUNT(*) count FROM sqlite_master WHERE type='table' AND name IN ('cash_sessions','payments')")
          .get(),
      ).toEqual({ count: 2 });
      const invoiceColumns = database.prepare('PRAGMA table_info(invoices)').all() as Array<{
        name: string;
      }>;
      expect(invoiceColumns.map((column) => column.name)).toEqual(
        expect.arrayContaining([
          'idempotency_key',
          'cancelled_by',
          'cancelled_at',
          'cancellation_reason',
          'store_name',
          'store_address',
          'store_phone',
          'store_email',
          'currency',
        ]),
      );
      const userColumns = database.prepare('PRAGMA table_info(users)').all() as Array<{
        name: string;
      }>;
      expect(userColumns.map((column) => column.name)).toEqual(
        expect.arrayContaining(['failed_recovery_attempts', 'recovery_locked_until']),
      );
    } finally {
      database.close();
    }
  });

  it('preserves a representative legacy STORE profile across every migration', () => {
    const database = new Database(':memory:');
    try {
      database.exec(`
        PRAGMA foreign_keys = ON;
        CREATE TABLE users (
          id INTEGER PRIMARY KEY, username TEXT NOT NULL UNIQUE, email TEXT UNIQUE,
          password_hash TEXT NOT NULL, role TEXT NOT NULL, first_name TEXT NOT NULL,
          last_name TEXT NOT NULL, initials TEXT NOT NULL, phone TEXT, hire_date TEXT,
          active INTEGER NOT NULL DEFAULT 1, security_question TEXT,
          security_answer_hash TEXT, created_at TEXT DEFAULT CURRENT_TIMESTAMP
        );
        CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE audit_logs (
          id INTEGER PRIMARY KEY, user_id INTEGER, action TEXT NOT NULL,
          entity TEXT NOT NULL, entity_id TEXT, details TEXT,
          created_at TEXT DEFAULT CURRENT_TIMESTAMP
        );
        CREATE TABLE products (
          id INTEGER PRIMARY KEY, name TEXT NOT NULL, hashtag TEXT,
          category TEXT NOT NULL DEFAULT 'Divers', description TEXT DEFAULT '',
          price REAL NOT NULL, stock_quantity INTEGER NOT NULL DEFAULT 0,
          min_stock_threshold INTEGER NOT NULL DEFAULT 0,
          created_at TEXT DEFAULT CURRENT_TIMESTAMP,
          updated_at TEXT DEFAULT CURRENT_TIMESTAMP, deleted_at TEXT
        );
        CREATE TABLE stock_movements (
          id INTEGER PRIMARY KEY, product_id INTEGER NOT NULL REFERENCES products(id),
          quantity INTEGER NOT NULL, reason TEXT NOT NULL, reference_id TEXT,
          created_at TEXT DEFAULT CURRENT_TIMESTAMP
        );
        CREATE TABLE invoices (
          id TEXT PRIMARY KEY, employee_id INTEGER NOT NULL REFERENCES users(id),
          invoice_date TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          subtotal REAL NOT NULL, total_amount REAL NOT NULL, discount REAL DEFAULT 0,
          status TEXT DEFAULT 'validated'
        );
        CREATE TABLE invoice_lines (
          id INTEGER PRIMARY KEY, invoice_id TEXT NOT NULL REFERENCES invoices(id),
          product_id INTEGER REFERENCES products(id), product_name TEXT NOT NULL,
          category TEXT NOT NULL, quantity INTEGER NOT NULL, unit_price REAL NOT NULL,
          total_line REAL NOT NULL
        );
        CREATE TABLE attendances (
          id INTEGER PRIMARY KEY, employee_id INTEGER NOT NULL REFERENCES users(id),
          start_time TEXT NOT NULL, end_time TEXT
        );
        CREATE TABLE messages (
          id INTEGER PRIMARY KEY, sender_id INTEGER REFERENCES users(id),
          recipient_type TEXT NOT NULL, recipient_id INTEGER REFERENCES users(id),
          subject TEXT NOT NULL, content TEXT NOT NULL, type TEXT DEFAULT 'message',
          is_read INTEGER DEFAULT 0, created_at TEXT DEFAULT CURRENT_TIMESTAMP
        );
        CREATE TABLE notifications (
          id INTEGER PRIMARY KEY, type TEXT NOT NULL, product_id INTEGER REFERENCES products(id),
          message TEXT NOT NULL, is_read INTEGER DEFAULT 0,
          resolved_at TEXT, created_at TEXT DEFAULT CURRENT_TIMESTAMP
        );
        CREATE TABLE email_report_logs (
          id INTEGER PRIMARY KEY, recipient TEXT NOT NULL, subject TEXT NOT NULL,
          filename TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'pending',
          created_at TEXT DEFAULT CURRENT_TIMESTAMP
        );

        INSERT INTO users VALUES
          (1,'owner-i1','owner@fixture.invalid','owner-hash','owner','Awa','Owner','AO',NULL,'2024-01-01',1,'City','answer-hash','2024-01-01T08:00:00Z'),
          (2,'manager-i1','manager@fixture.invalid','manager-hash','manager','Moussa','Manager','MM',NULL,'2024-02-01',1,NULL,NULL,'2024-02-01T08:00:00Z'),
          (3,'employee-i1','employee@fixture.invalid','employee-hash','employee','Fatou','Cashier','FC',NULL,'2024-03-01',1,NULL,NULL,'2024-03-01T08:00:00Z');
        INSERT INTO settings VALUES('storeName','STORE I1 Fixture'),('currency','XOF');
        INSERT INTO products VALUES(10,'Poivre I1','#poivre','Epices','Fixture',1250,37,5,'2024-01-01','2024-01-02',NULL);
        INSERT INTO stock_movements VALUES(20,10,37,'initial','FIXTURE-STOCK','2024-01-02T09:00:00Z');
        INSERT INTO invoices VALUES('INV-I1-0001',3,'2024-04-01T10:00:00Z',2500,2500,0,'validated');
        INSERT INTO invoice_lines VALUES(30,'INV-I1-0001',10,'Poivre I1','Epices',2,1250,2500);
        INSERT INTO attendances VALUES(40,3,'2024-04-01T08:00:00Z','2024-04-01T17:00:00Z');
        INSERT INTO messages VALUES(50,1,'user',2,'Fixture message','Preserve me','message',0,'2024-04-01T11:00:00Z');
      `);

      const before = {
        users: database.prepare('SELECT COUNT(*) count FROM users').get(),
        product: database.prepare('SELECT name,stock_quantity stock FROM products WHERE id=10').get(),
        invoice: database.prepare('SELECT total_amount total FROM invoices WHERE id=?').get('INV-I1-0001'),
        message: database.prepare('SELECT subject,content FROM messages WHERE id=50').get(),
      };

      applyDatabaseMigrations(database);
      database.exec(schema);

      expect(database.pragma('integrity_check')).toEqual([{ integrity_check: 'ok' }]);
      expect(database.pragma('foreign_key_check')).toEqual([]);
      expect(database.prepare('SELECT COUNT(*) count FROM users').get()).toEqual(before.users);
      expect(database.prepare('SELECT name,stock_quantity stock FROM products WHERE id=10').get()).toEqual(before.product);
      expect(database.prepare('SELECT image_ref imageRef FROM products WHERE id=10').get()).toEqual({ imageRef: null });
      expect(database.prepare('SELECT total_amount total FROM invoices WHERE id=?').get('INV-I1-0001')).toEqual(before.invoice);
      expect(database.prepare('SELECT subject,content FROM messages WHERE id=50').get()).toEqual(before.message);
      expect(database.prepare('SELECT reference FROM products WHERE id=10').get()).toEqual({ reference: 'PROD-000010' });
      expect(database.prepare('SELECT unit_price unitPrice FROM stock_movements WHERE id=20').get()).toEqual({ unitPrice: 1250 });
      expect(database.prepare('SELECT session_ref sessionRef,status FROM attendances WHERE id=40').get()).toEqual({ sessionRef: 'LEGACY-40', status: 'VALID' });
      expect(database.prepare(`SELECT users.username,roles.code role FROM user_roles
        JOIN users ON users.id=user_roles.user_id JOIN roles ON roles.id=user_roles.role_id
        ORDER BY users.id`).all()).toEqual([
          { username: 'owner-i1', role: 'ADMIN' },
          { username: 'manager-i1', role: 'MANAGER' },
          { username: 'employee-i1', role: 'CASHIER' },
        ]);
      expect(database.prepare('SELECT COUNT(*) count FROM suppliers').get()).toEqual({ count: 0 });
      expect(database.prepare('SELECT COUNT(*) count FROM purchases').get()).toEqual({ count: 0 });
      expect(database.prepare('SELECT COUNT(*) count FROM inventory_counts').get()).toEqual({ count: 0 });
      expect(database.prepare('SELECT COUNT(*) count FROM schema_migrations').get()).toEqual({ count: latestSchemaVersion });
    } finally {
      database.close();
    }
  });
});
