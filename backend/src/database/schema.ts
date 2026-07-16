export const schema = `
PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL, email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL, role TEXT NOT NULL DEFAULT 'employee' CHECK(role IN ('admin','employee')),
  first_name TEXT NOT NULL, last_name TEXT NOT NULL, initials TEXT NOT NULL, phone TEXT, hire_date TEXT,
  active INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS products (
  id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, hashtag TEXT, category TEXT NOT NULL,
  description TEXT DEFAULT '', price REAL NOT NULL CHECK(price >= 0), stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK(stock_quantity >= 0),
  min_stock_threshold INTEGER NOT NULL DEFAULT 0 CHECK(min_stock_threshold >= 0), created_at TEXT DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT DEFAULT CURRENT_TIMESTAMP, deleted_at TEXT
);
CREATE TABLE IF NOT EXISTS invoices (
  id TEXT PRIMARY KEY, employee_id INTEGER NOT NULL REFERENCES users(id), invoice_date TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  subtotal REAL NOT NULL, total_amount REAL NOT NULL, discount REAL DEFAULT 0, status TEXT DEFAULT 'validated'
);
CREATE TABLE IF NOT EXISTS invoice_lines (
  id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_id TEXT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  product_id INTEGER REFERENCES products(id), product_name TEXT NOT NULL, category TEXT NOT NULL,
  quantity INTEGER NOT NULL CHECK(quantity > 0), unit_price REAL NOT NULL, total_line REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS attendances (
  id INTEGER PRIMARY KEY AUTOINCREMENT, employee_id INTEGER NOT NULL REFERENCES users(id),
  start_time TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, end_time TEXT
);
CREATE TABLE IF NOT EXISTS messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT, sender_id INTEGER REFERENCES users(id), recipient_type TEXT NOT NULL,
  recipient_id INTEGER REFERENCES users(id), subject TEXT NOT NULL, content TEXT NOT NULL, type TEXT DEFAULT 'message',
  is_read INTEGER DEFAULT 0, created_at TEXT DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS notifications (
  id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, product_id INTEGER REFERENCES products(id),
  message TEXT NOT NULL, is_read INTEGER DEFAULT 0, resolved_at TEXT, created_at TEXT DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS stock_movements (
  id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER NOT NULL REFERENCES products(id),
  quantity INTEGER NOT NULL, reason TEXT NOT NULL, reference_id TEXT, created_at TEXT DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS audit_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, action TEXT NOT NULL, entity TEXT NOT NULL,
  entity_id TEXT, details TEXT, created_at TEXT DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_products_search ON products(name, category, hashtag);
CREATE INDEX IF NOT EXISTS idx_invoices_date ON invoices(invoice_date);
CREATE INDEX IF NOT EXISTS idx_attendances_employee ON attendances(employee_id, start_time);
`;
