/* global console, process */
import Database from 'better-sqlite3';
import bcrypt from 'bcryptjs';
import fs from 'node:fs';
import path from 'node:path';

const [mode, profileArgument] = process.argv.slice(2);
if (!['create', 'verify'].includes(mode) || !profileArgument) {
  throw new Error('Usage: node scripts/phase-i1-upgrade-fixture.mjs <create|verify> <isolated-profile>');
}

const profile = path.resolve(profileArgument);
if (!profile.toLowerCase().includes('store-phase-i1')) {
  throw new Error(`Refusing non-Phase-I.1 profile: ${profile}`);
}
const databaseFile = path.join(profile, 'store.db');
const snapshotFile = path.join(profile, 'pre-upgrade-snapshot.json');

const snapshot = (database) => ({
  users: database.prepare('SELECT id,username,role,active FROM users ORDER BY id').all(),
  product: database.prepare('SELECT id,name,stock_quantity,price FROM products WHERE id=10').get(),
  movement: database.prepare('SELECT id,product_id,quantity,reason,reference_id FROM stock_movements WHERE id=20').get(),
  invoice: database.prepare('SELECT id,employee_id,total_amount,status FROM invoices WHERE id=?').get('INV-I1-0001'),
  invoiceLine: database.prepare('SELECT product_name,quantity,unit_price,total_line FROM invoice_lines WHERE id=30').get(),
  attendance: database.prepare('SELECT employee_id,start_time,end_time FROM attendances WHERE id=40').get(),
  message: database.prepare('SELECT subject,content FROM messages WHERE id=50').get(),
  settings: database.prepare("SELECT key,value FROM settings WHERE key IN ('storeName','currency') ORDER BY key").all(),
});

if (mode === 'create') {
  fs.mkdirSync(profile, { recursive: true });
  if (fs.existsSync(databaseFile)) throw new Error(`Refusing to replace existing fixture: ${databaseFile}`);
  const database = new Database(databaseFile);
  database.pragma('foreign_keys = ON');
  database.exec(`
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
      created_at TEXT DEFAULT CURRENT_TIMESTAMP, updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
      deleted_at TEXT
    );
    CREATE TABLE stock_movements (
      id INTEGER PRIMARY KEY, product_id INTEGER NOT NULL REFERENCES products(id),
      quantity INTEGER NOT NULL, reason TEXT NOT NULL, reference_id TEXT,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP
    );
    CREATE TABLE invoices (
      id TEXT PRIMARY KEY, employee_id INTEGER NOT NULL REFERENCES users(id),
      invoice_date TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, subtotal REAL NOT NULL,
      total_amount REAL NOT NULL, discount REAL DEFAULT 0, status TEXT DEFAULT 'validated'
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
      message TEXT NOT NULL, is_read INTEGER DEFAULT 0, resolved_at TEXT,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP
    );
    CREATE TABLE email_report_logs (
      id INTEGER PRIMARY KEY, recipient TEXT NOT NULL, subject TEXT NOT NULL,
      filename TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'pending',
      created_at TEXT DEFAULT CURRENT_TIMESTAMP
    );
  `);
  const insertUser = database.prepare(`INSERT INTO users(
    id,username,email,password_hash,role,first_name,last_name,initials,hire_date,active,created_at
  ) VALUES(?,?,?,?,?,?,?,?,?,1,?)`);
  insertUser.run(1, 'owner-i1', 'owner@fixture.invalid', bcrypt.hashSync('Owner-I1-Only!', 10), 'owner', 'Awa', 'Owner', 'AO', '2024-01-01', '2024-01-01T08:00:00Z');
  insertUser.run(2, 'manager-i1', 'manager@fixture.invalid', bcrypt.hashSync('Manager-I1-Only!', 10), 'manager', 'Moussa', 'Manager', 'MM', '2024-02-01', '2024-02-01T08:00:00Z');
  insertUser.run(3, 'employee-i1', 'employee@fixture.invalid', bcrypt.hashSync('Employee-I1-Only!', 10), 'employee', 'Fatou', 'Cashier', 'FC', '2024-03-01', '2024-03-01T08:00:00Z');
  database.exec(`
    INSERT INTO settings VALUES('storeName','STORE I1 Fixture'),('currency','XOF');
    INSERT INTO products VALUES(10,'Poivre I1','#poivre','Epices','Fixture',1250,37,5,'2024-01-01','2024-01-02',NULL);
    INSERT INTO stock_movements VALUES(20,10,37,'initial','FIXTURE-STOCK','2024-01-02T09:00:00Z');
    INSERT INTO invoices VALUES('INV-I1-0001',3,'2024-04-01T10:00:00Z',2500,2500,0,'validated');
    INSERT INTO invoice_lines VALUES(30,'INV-I1-0001',10,'Poivre I1','Epices',2,1250,2500);
    INSERT INTO attendances VALUES(40,3,'2024-04-01T08:00:00Z','2024-04-01T17:00:00Z');
    INSERT INTO messages VALUES(50,1,'user',2,'Fixture message','Preserve me','message',0,'2024-04-01T11:00:00Z');
  `);
  const before = snapshot(database);
  fs.writeFileSync(snapshotFile, `${JSON.stringify(before, null, 2)}\n`, { flag: 'wx' });
  fs.mkdirSync(path.join(profile, 'backups'), { recursive: true });
  await database.backup(path.join(profile, 'backups', 'representative-2.0.1-pre-upgrade.db'));
  database.close();
  console.log(JSON.stringify({ profile, databaseFile, snapshot: before }, null, 2));
} else {
  const expected = JSON.parse(fs.readFileSync(snapshotFile, 'utf8'));
  const database = new Database(databaseFile, { readonly: true, fileMustExist: true });
  const after = snapshot(database);
  const result = {
    profile,
    preserved: JSON.stringify(after) === JSON.stringify(expected),
    integrity: database.pragma('integrity_check'),
    foreignKeyCheck: database.pragma('foreign_key_check'),
    migrations: database.prepare('SELECT MAX(version) latest,COUNT(*) count FROM schema_migrations').get(),
    roles: database.prepare(`SELECT users.username,roles.code role FROM user_roles
      JOIN users ON users.id=user_roles.user_id JOIN roles ON roles.id=user_roles.role_id
      ORDER BY users.id`).all(),
    migrationBackups: fs.existsSync(path.join(profile, 'backups', 'before-migrations'))
      ? fs.readdirSync(path.join(profile, 'backups', 'before-migrations'))
      : [],
    after,
  };
  database.close();
  console.log(JSON.stringify(result, null, 2));
  if (!result.preserved || result.integrity[0]?.integrity_check !== 'ok' || result.foreignKeyCheck.length) process.exitCode = 1;
}
