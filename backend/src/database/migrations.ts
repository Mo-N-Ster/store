import type Database from 'better-sqlite3';
import {
  defaultPermissions,
  legacyRoleToSystemRole,
  permissionActions,
  permissionModules,
  systemRoles,
} from '../domain/rbac/permissionMatrix.js';

export type DatabaseMigration = {
  version: number;
  name: string;
  up: (database: Database.Database) => void;
};

const migrations: DatabaseMigration[] = [
  {
    version: 1,
    name: 'reporting-foundation',
    up: (database) => {
      const movementColumns = database.pragma('table_info(stock_movements)') as { name: string }[];
      if (!movementColumns.some((column) => column.name === 'unit_price'))
        database.exec('ALTER TABLE stock_movements ADD COLUMN unit_price REAL NOT NULL DEFAULT 0');

      database.exec(`
        CREATE TABLE IF NOT EXISTS product_price_history (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          product_id INTEGER NOT NULL REFERENCES products(id),
          price REAL NOT NULL CHECK(price >= 0),
          recorded_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
        CREATE INDEX IF NOT EXISTS idx_stock_movements_date
          ON stock_movements(created_at, product_id);
        CREATE INDEX IF NOT EXISTS idx_price_history_date
          ON product_price_history(recorded_at, product_id);
        UPDATE stock_movements
          SET unit_price=COALESCE(
            (SELECT price FROM products WHERE products.id=stock_movements.product_id),
            0
          )
          WHERE unit_price=0;
        INSERT INTO product_price_history(product_id,price,recorded_at)
          SELECT p.id,p.price,COALESCE(p.created_at,CURRENT_TIMESTAMP)
          FROM products p
          WHERE NOT EXISTS (
            SELECT 1 FROM product_price_history history WHERE history.product_id=p.id
          );
      `);
    },
  },
  {
    version: 2,
    name: 'rbac-foundation',
    up: (database) => {
      database.exec(`
        CREATE TABLE IF NOT EXISTS roles (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          code TEXT NOT NULL UNIQUE,
          name TEXT NOT NULL,
          is_system INTEGER NOT NULL DEFAULT 1 CHECK(is_system IN (0,1)),
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
        CREATE TABLE IF NOT EXISTS permissions (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          module TEXT NOT NULL,
          action TEXT NOT NULL,
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          UNIQUE(module,action)
        );
        CREATE TABLE IF NOT EXISTS role_permissions (
          role_id INTEGER NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
          permission_id INTEGER NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
          PRIMARY KEY(role_id,permission_id)
        );
        CREATE TABLE IF NOT EXISTS user_roles (
          user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
          role_id INTEGER NOT NULL REFERENCES roles(id),
          assigned_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
        CREATE INDEX IF NOT EXISTS idx_permissions_module_action
          ON permissions(module,action);
        CREATE INDEX IF NOT EXISTS idx_user_roles_role
          ON user_roles(role_id,user_id);
      `);

      const insertRole = database.prepare(
        'INSERT OR IGNORE INTO roles(code,name,is_system) VALUES(?,?,1)',
      );
      for (const role of systemRoles) insertRole.run(role, role);

      const insertPermission = database.prepare(
        'INSERT OR IGNORE INTO permissions(module,action) VALUES(?,?)',
      );
      for (const module of permissionModules)
        for (const action of permissionActions) insertPermission.run(module, action);

      const assignPermission = database.prepare(`
        INSERT OR IGNORE INTO role_permissions(role_id,permission_id)
        SELECT roles.id,permissions.id FROM roles,permissions
        WHERE roles.code=? AND permissions.module=? AND permissions.action=?
      `);
      for (const role of systemRoles)
        for (const permission of defaultPermissions(role)) {
          const [module, action] = permission.split(':');
          assignPermission.run(role, module, action);
        }

      const assignRole = database.prepare(`
        INSERT INTO user_roles(user_id,role_id)
        SELECT ?,id FROM roles WHERE code=?
        ON CONFLICT(user_id) DO UPDATE SET role_id=excluded.role_id,assigned_at=CURRENT_TIMESTAMP
      `);
      const users = database.prepare('SELECT id,role FROM users').all() as {
        id: number;
        role: string;
      }[];
      for (const user of users) assignRole.run(user.id, legacyRoleToSystemRole(user.role));

      database.exec(`
        CREATE TRIGGER IF NOT EXISTS trg_users_rbac_insert
        AFTER INSERT ON users
        BEGIN
          INSERT INTO user_roles(user_id,role_id)
          SELECT NEW.id,id FROM roles
          WHERE code=CASE NEW.role
            WHEN 'owner' THEN 'ADMIN'
            WHEN 'manager' THEN 'MANAGER'
            ELSE 'CASHIER'
          END
          ON CONFLICT(user_id) DO UPDATE SET
            role_id=excluded.role_id,
            assigned_at=CURRENT_TIMESTAMP;
        END;
        CREATE TRIGGER IF NOT EXISTS trg_users_rbac_update
        AFTER UPDATE OF role ON users
        BEGIN
          INSERT INTO user_roles(user_id,role_id)
          SELECT NEW.id,id FROM roles
          WHERE code=CASE NEW.role
            WHEN 'owner' THEN 'ADMIN'
            WHEN 'manager' THEN 'MANAGER'
            ELSE 'CASHIER'
          END
          ON CONFLICT(user_id) DO UPDATE SET
            role_id=excluded.role_id,
            assigned_at=CURRENT_TIMESTAMP;
        END;
      `);
    },
  },
  {
    version: 3,
    name: 'authentication-hardening',
    up: (database) => {
      const userColumns = new Set(
        (database.pragma('table_info(users)') as { name: string }[]).map(({ name }) => name),
      );
      if (!userColumns.has('failed_login_attempts'))
        database.exec(
          'ALTER TABLE users ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0',
        );
      if (!userColumns.has('locked_until'))
        database.exec('ALTER TABLE users ADD COLUMN locked_until TEXT');
      if (!userColumns.has('last_login_at'))
        database.exec('ALTER TABLE users ADD COLUMN last_login_at TEXT');

      const auditColumns = new Set(
        (database.pragma('table_info(audit_logs)') as { name: string }[]).map(({ name }) => name),
      );
      if (!auditColumns.has('outcome'))
        database.exec(
          "ALTER TABLE audit_logs ADD COLUMN outcome TEXT NOT NULL DEFAULT 'SUCCESS'",
        );

      database.exec(`
        INSERT OR IGNORE INTO settings(key,value) VALUES('authMaxAttempts','5');
        INSERT OR IGNORE INTO settings(key,value) VALUES('authLockMinutes','15');
        CREATE INDEX IF NOT EXISTS idx_users_locked_until ON users(locked_until);
        CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at);
      `);
    },
  },
  {
    version: 4,
    name: 'employee-identity-separation',
    up: (database) => {
      database.exec(`
        CREATE TABLE IF NOT EXISTS employees (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          employee_code TEXT NOT NULL UNIQUE,
          user_id INTEGER UNIQUE REFERENCES users(id) ON DELETE SET NULL,
          first_name TEXT NOT NULL,
          last_name TEXT NOT NULL,
          phone TEXT,
          email TEXT,
          address TEXT,
          status TEXT NOT NULL DEFAULT 'ACTIVE'
            CHECK(status IN ('ACTIVE','ABSENT','SUSPENDED','RESIGNED','ARCHIVED')),
          hire_date TEXT,
          end_date TEXT,
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
        CREATE INDEX IF NOT EXISTS idx_employees_status
          ON employees(status,last_name,first_name);
        INSERT OR IGNORE INTO employees(
          employee_code,user_id,first_name,last_name,phone,email,status,hire_date,created_at
        )
        SELECT
          'EMP-'||printf('%06d',users.id),users.id,users.first_name,users.last_name,
          users.phone,users.email,
          CASE WHEN users.active=1 THEN 'ACTIVE' ELSE 'SUSPENDED' END,
          users.hire_date,users.created_at
        FROM users;

        CREATE TRIGGER IF NOT EXISTS trg_users_employee_insert
        AFTER INSERT ON users
        BEGIN
          INSERT OR IGNORE INTO employees(
            employee_code,user_id,first_name,last_name,phone,email,status,hire_date,created_at
          ) VALUES(
            'EMP-'||printf('%06d',NEW.id),NEW.id,NEW.first_name,NEW.last_name,
            NEW.phone,NEW.email,
            CASE WHEN NEW.active=1 THEN 'ACTIVE' ELSE 'SUSPENDED' END,
            NEW.hire_date,NEW.created_at
          );
        END;
        CREATE TRIGGER IF NOT EXISTS trg_users_employee_update
        AFTER UPDATE OF first_name,last_name,phone,email,hire_date,active ON users
        BEGIN
          UPDATE employees SET
            first_name=NEW.first_name,
            last_name=NEW.last_name,
            phone=NEW.phone,
            email=NEW.email,
            hire_date=NEW.hire_date,
            status=CASE WHEN NEW.active=1 THEN 'ACTIVE' ELSE 'SUSPENDED' END,
            updated_at=CURRENT_TIMESTAMP
          WHERE user_id=NEW.id;
        END;
      `);
    },
  },
  {
    version: 5,
    name: 'authenticated-presence-sessions',
    up: (database) => {
      const columns = new Set(
        (database.pragma('table_info(attendances)') as { name: string }[]).map(({ name }) => name),
      );
      const additions = [
        ['session_ref', 'TEXT'],
        ['source', "TEXT NOT NULL DEFAULT 'AUTHENTICATION'"],
        ['status', "TEXT NOT NULL DEFAULT 'VALID'"],
        ['original_start_time', 'TEXT'],
        ['original_end_time', 'TEXT'],
        ['correction_reason', 'TEXT'],
        ['corrected_by', 'INTEGER REFERENCES users(id)'],
        ['corrected_at', 'TEXT'],
      ] as const;
      for (const [name, definition] of additions)
        if (!columns.has(name))
          database.exec(`ALTER TABLE attendances ADD COLUMN ${name} ${definition}`);
      database.exec(`
        UPDATE attendances SET
          session_ref=COALESCE(session_ref,'LEGACY-'||id),
          source=COALESCE(source,'LEGACY'),
          status=COALESCE(status,'VALID');
        CREATE UNIQUE INDEX IF NOT EXISTS idx_attendances_session_ref
          ON attendances(session_ref) WHERE session_ref IS NOT NULL;
      `);
    },
  },
  {
    version: 6,
    name: 'stock-and-inventory-foundation',
    up: (database) => {
      const productColumns = new Set(
        (database.pragma('table_info(products)') as { name: string }[]).map(({ name }) => name),
      );
      if (!productColumns.has('reference')) database.exec('ALTER TABLE products ADD COLUMN reference TEXT');
      database.exec(`
        UPDATE products SET reference='PROD-'||printf('%06d',id)
        WHERE reference IS NULL OR trim(reference)='';
        CREATE UNIQUE INDEX IF NOT EXISTS idx_products_reference
          ON products(reference) WHERE reference IS NOT NULL;
        CREATE TABLE IF NOT EXISTS inventory_counts (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          reference TEXT NOT NULL UNIQUE,
          status TEXT NOT NULL DEFAULT 'DRAFT'
            CHECK(status IN ('DRAFT','VALIDATED','CANCELLED')),
          note TEXT,
          created_by INTEGER NOT NULL REFERENCES users(id),
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          validated_by INTEGER REFERENCES users(id),
          validated_at TEXT
        );
        CREATE TABLE IF NOT EXISTS inventory_count_lines (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          inventory_id INTEGER NOT NULL REFERENCES inventory_counts(id) ON DELETE CASCADE,
          product_id INTEGER NOT NULL REFERENCES products(id),
          expected_quantity INTEGER NOT NULL CHECK(expected_quantity >= 0),
          counted_quantity INTEGER NOT NULL CHECK(counted_quantity >= 0),
          UNIQUE(inventory_id,product_id)
        );
        CREATE INDEX IF NOT EXISTS idx_inventory_status
          ON inventory_counts(status,created_at);
        CREATE TRIGGER IF NOT EXISTS trg_products_reference
        AFTER INSERT ON products
        WHEN NEW.reference IS NULL OR trim(NEW.reference)=''
        BEGIN
          UPDATE products SET reference='PROD-'||printf('%06d',NEW.id) WHERE id=NEW.id;
        END;
      `);
    },
  },
  {
    version: 7,
    name: 'purchases-foundation',
    up: (database) => {
      database.exec(`
        CREATE TABLE IF NOT EXISTS suppliers (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          name TEXT NOT NULL UNIQUE,
          phone TEXT,
          email TEXT,
          address TEXT,
          active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)),
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
        CREATE TABLE IF NOT EXISTS purchases (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          reference TEXT NOT NULL UNIQUE,
          idempotency_key TEXT UNIQUE,
          supplier_id INTEGER REFERENCES suppliers(id),
          supplier_invoice TEXT,
          status TEXT NOT NULL DEFAULT 'DRAFT'
            CHECK(status IN ('DRAFT','VALIDATED','CANCELLED')),
          note TEXT,
          total_amount REAL NOT NULL DEFAULT 0 CHECK(total_amount >= 0),
          created_by INTEGER NOT NULL REFERENCES users(id),
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          validated_by INTEGER REFERENCES users(id),
          validated_at TEXT,
          cancelled_by INTEGER REFERENCES users(id),
          cancelled_at TEXT,
          cancellation_reason TEXT
        );
        CREATE TABLE IF NOT EXISTS purchase_items (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          purchase_id INTEGER NOT NULL REFERENCES purchases(id) ON DELETE CASCADE,
          product_id INTEGER NOT NULL REFERENCES products(id),
          quantity INTEGER NOT NULL CHECK(quantity > 0),
          unit_cost REAL NOT NULL CHECK(unit_cost >= 0),
          total_line REAL NOT NULL CHECK(total_line >= 0),
          UNIQUE(purchase_id,product_id)
        );
        CREATE INDEX IF NOT EXISTS idx_purchases_status_date
          ON purchases(status,created_at);
        CREATE INDEX IF NOT EXISTS idx_purchase_items_purchase
          ON purchase_items(purchase_id,product_id);
      `);
    },
  },
  {
    version: 8,
    name: 'cash-sessions-and-payments',
    up: (database) => {
      database.exec(`
        CREATE TABLE IF NOT EXISTS cash_sessions (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          reference TEXT NOT NULL UNIQUE,
          employee_id INTEGER NOT NULL REFERENCES users(id),
          status TEXT NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','CLOSED')),
          opening_amount REAL NOT NULL DEFAULT 0 CHECK(opening_amount >= 0),
          opened_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          closed_at TEXT,
          closing_amount REAL CHECK(closing_amount >= 0),
          expected_amount REAL,
          difference REAL,
          closed_by INTEGER REFERENCES users(id)
        );
        CREATE UNIQUE INDEX IF NOT EXISTS idx_cash_session_open_employee
          ON cash_sessions(employee_id) WHERE status='OPEN';
      `);
      const invoiceColumns = new Set(
        (database.pragma('table_info(invoices)') as { name: string }[]).map(({ name }) => name),
      );
      if (!invoiceColumns.has('cash_session_id'))
        database.exec('ALTER TABLE invoices ADD COLUMN cash_session_id INTEGER REFERENCES cash_sessions(id)');
      database.exec(`
        CREATE TABLE IF NOT EXISTS payments (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          invoice_id TEXT NOT NULL UNIQUE REFERENCES invoices(id) ON DELETE CASCADE,
          cash_session_id INTEGER NOT NULL REFERENCES cash_sessions(id),
          method TEXT NOT NULL DEFAULT 'CASH' CHECK(method IN ('CASH')),
          amount REAL NOT NULL CHECK(amount >= 0),
          amount_received REAL NOT NULL CHECK(amount_received >= 0),
          change_amount REAL NOT NULL DEFAULT 0 CHECK(change_amount >= 0),
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
        CREATE INDEX IF NOT EXISTS idx_payments_cash_session
          ON payments(cash_session_id,created_at);
      `);
    },
  },
  {
    version: 9,
    name: 'pos-idempotency-and-cancellation',
    up: (database) => {
      const invoiceColumns = new Set(
        (database.pragma('table_info(invoices)') as { name: string }[]).map(({ name }) => name),
      );
      const invoiceAdditions = [
        ['idempotency_key', 'TEXT'],
        ['cancelled_by', 'INTEGER REFERENCES users(id)'],
        ['cancelled_at', 'TEXT'],
        ['cancellation_reason', 'TEXT'],
      ] as const;
      for (const [name, definition] of invoiceAdditions)
        if (!invoiceColumns.has(name))
          database.exec(`ALTER TABLE invoices ADD COLUMN ${name} ${definition}`);
      const paymentColumns = new Set(
        (database.pragma('table_info(payments)') as { name: string }[]).map(({ name }) => name),
      );
      if (!paymentColumns.has('status'))
        database.exec("ALTER TABLE payments ADD COLUMN status TEXT NOT NULL DEFAULT 'CAPTURED'");
      database.exec(`
        CREATE UNIQUE INDEX IF NOT EXISTS idx_invoices_idempotency
          ON invoices(idempotency_key) WHERE idempotency_key IS NOT NULL;
        CREATE INDEX IF NOT EXISTS idx_invoices_status_date ON invoices(status,invoice_date);
      `);
    },
  },
  {
    version: 10,
    name: 'password-recovery-protection',
    up: (database) => {
      const columns = new Set(
        (database.pragma('table_info(users)') as { name: string }[]).map(({ name }) => name),
      );
      if (!columns.has('failed_recovery_attempts'))
        database.exec(
          'ALTER TABLE users ADD COLUMN failed_recovery_attempts INTEGER NOT NULL DEFAULT 0',
        );
      if (!columns.has('recovery_locked_until'))
        database.exec('ALTER TABLE users ADD COLUMN recovery_locked_until TEXT');
    },
  },
  {
    version: 11,
    name: 'invoice-business-snapshot',
    up: (database) => {
      const columns = new Set(
        (database.pragma('table_info(invoices)') as { name: string }[]).map(({ name }) => name),
      );
      for (const [name, definition] of [
        ['store_name', 'TEXT'],
        ['store_address', 'TEXT'],
        ['store_phone', 'TEXT'],
        ['store_email', 'TEXT'],
        ['currency', 'TEXT'],
      ] as const)
        if (!columns.has(name)) database.exec(`ALTER TABLE invoices ADD COLUMN ${name} ${definition}`);
    },
  },
  {
    version: 12,
    name: 'invoice-cost-snapshot',
    up: (database) => {
      const columns = new Set(
        (database.pragma('table_info(invoice_lines)') as { name: string }[]).map(({ name }) => name),
      );
      if (columns.size && !columns.has('unit_cost'))
        database.exec('ALTER TABLE invoice_lines ADD COLUMN unit_cost REAL');
    },
  },
  {
    version: 13,
    name: 'offline-email-queue',
    up: (database) => {
      const columns = new Set(
        (database.pragma('table_info(email_report_logs)') as { name: string }[]).map(({ name }) => name),
      );
      if (columns.size) {
        for (const [name, definition] of [
          ['message_text', "TEXT NOT NULL DEFAULT ''"],
          ['attachment', 'BLOB'],
          ['attempts', 'INTEGER NOT NULL DEFAULT 0'],
          ['next_attempt_at', 'TEXT'],
          ['last_error', 'TEXT'],
          ['sent_at', 'TEXT'],
        ] as const)
          if (!columns.has(name))
            database.exec(`ALTER TABLE email_report_logs ADD COLUMN ${name} ${definition}`);
        database.exec(`
          UPDATE email_report_logs SET sent_at=COALESCE(sent_at,created_at) WHERE status='sent';
          CREATE INDEX IF NOT EXISTS idx_email_queue_status
            ON email_report_logs(status,next_attempt_at,created_at);
        `);
      }
      const messageColumns = new Set(
        (database.pragma('table_info(messages)') as { name: string }[]).map(({ name }) => name),
      );
      if (messageColumns.size && !messageColumns.has('request_id'))
        database.exec('ALTER TABLE messages ADD COLUMN request_id TEXT');
      if (messageColumns.size)
        database.exec(
          'CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_request_id ON messages(request_id) WHERE request_id IS NOT NULL',
        );
    },
  },
  {
    version: 14,
    name: 'article-primary-image',
    up: (database) => {
      const columns = new Set(
        (database.pragma('table_info(products)') as { name: string }[]).map(({ name }) => name),
      );
      if (columns.size && !columns.has('image_ref'))
        database.exec('ALTER TABLE products ADD COLUMN image_ref TEXT');
    },
  },
  {
    version: 15,
    name: 'employee-profile-photo',
    up: (database) => {
      const columns = database.pragma('table_info(users)') as { name: string }[];
      if (columns.length && !columns.some(({ name }) => name === 'photo')) database.exec('ALTER TABLE users ADD COLUMN photo TEXT');
    },
  },
  {
    version: 16,
    name: 'individual-permission-denials',
    up: (database) => {
      database.exec(`CREATE TABLE IF NOT EXISTS user_permission_denials (
        user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        permission_id INTEGER NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
        PRIMARY KEY(user_id,permission_id)
      )`);
    },
  },
  {
    version: 17,
    name: 'audit-session-cash-snapshot',
    up: (database) => {
      const columns = new Set((database.pragma('table_info(audit_logs)') as { name: string }[]).map((column) => column.name));
      for (const [name, type] of [['responsible_id', 'INTEGER'], ['responsible_name', 'TEXT'], ['cash_reference', 'TEXT'], ['cash_amount', 'REAL'], ['cash_currency', 'TEXT']]) {
        if (!columns.has(name)) database.exec(`ALTER TABLE audit_logs ADD COLUMN ${name} ${type}`);
      }
    },
  },
  {
    version: 18,
    name: 'sale-idempotency-command',
    up: (database) => {
      database.exec('ALTER TABLE invoices ADD COLUMN idempotency_request TEXT');
    },
  },
];

export function hasPendingDatabaseMigrations(database: Database.Database) {
  const table = database
    .prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name='schema_migrations'")
    .get();
  if (!table) return migrations.length > 0;
  const applied = new Set(
    (database.prepare('SELECT version FROM schema_migrations').all() as { version: number }[]).map(
      ({ version }) => version,
    ),
  );
  return migrations.some(({ version }) => !applied.has(version));
}

export function applyDatabaseMigrations(database: Database.Database) {
  database.exec(`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      version INTEGER PRIMARY KEY,
      name TEXT NOT NULL,
      applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    )
  `);

  const applied = new Set(
    (database.prepare('SELECT version FROM schema_migrations').all() as { version: number }[]).map(
      ({ version }) => version,
    ),
  );

  for (const migration of migrations) {
    if (applied.has(migration.version)) continue;
    database.transaction(() => {
      migration.up(database);
      database
        .prepare('INSERT INTO schema_migrations(version,name) VALUES(?,?)')
        .run(migration.version, migration.name);
    })();
  }
}

export const latestSchemaVersion = migrations.at(-1)?.version ?? 0;
