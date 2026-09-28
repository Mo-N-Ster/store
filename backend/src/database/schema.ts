export const schema = `
PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS schema_migrations (
  version INTEGER PRIMARY KEY, name TEXT NOT NULL,
  applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL, email TEXT UNIQUE,
  password_hash TEXT NOT NULL, role TEXT NOT NULL DEFAULT 'employee' CHECK(role IN ('owner','manager','employee')),
  first_name TEXT NOT NULL, last_name TEXT NOT NULL, initials TEXT NOT NULL, phone TEXT, hire_date TEXT,
  active INTEGER NOT NULL DEFAULT 1, security_question TEXT, security_answer_hash TEXT, photo TEXT,
  failed_login_attempts INTEGER NOT NULL DEFAULT 0, locked_until TEXT, last_login_at TEXT,
  failed_recovery_attempts INTEGER NOT NULL DEFAULT 0, recovery_locked_until TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
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
CREATE TABLE IF NOT EXISTS products (
  id INTEGER PRIMARY KEY AUTOINCREMENT, reference TEXT UNIQUE, name TEXT NOT NULL, hashtag TEXT, category TEXT NOT NULL,
  description TEXT DEFAULT '', price REAL NOT NULL CHECK(price >= 0), stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK(stock_quantity >= 0),
  min_stock_threshold INTEGER NOT NULL DEFAULT 0 CHECK(min_stock_threshold >= 0), created_at TEXT DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT DEFAULT CURRENT_TIMESTAMP, deleted_at TEXT, image_ref TEXT
);
CREATE TABLE IF NOT EXISTS inventory_counts (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  reference TEXT NOT NULL UNIQUE,
  status TEXT NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','VALIDATED','CANCELLED')),
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
  status TEXT NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','VALIDATED','CANCELLED')),
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
CREATE TABLE IF NOT EXISTS invoices (
  id TEXT PRIMARY KEY, employee_id INTEGER NOT NULL REFERENCES users(id), invoice_date TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  cash_session_id INTEGER REFERENCES cash_sessions(id),
  idempotency_key TEXT UNIQUE,
  subtotal REAL NOT NULL, total_amount REAL NOT NULL, discount REAL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'validated' CHECK(status IN ('validated','cancelled')),
  cancelled_by INTEGER REFERENCES users(id), cancelled_at TEXT, cancellation_reason TEXT,
  store_name TEXT,store_address TEXT,store_phone TEXT,store_email TEXT,currency TEXT
);
CREATE TABLE IF NOT EXISTS invoice_lines (
  id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_id TEXT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  product_id INTEGER REFERENCES products(id), product_name TEXT NOT NULL, category TEXT NOT NULL,
  quantity INTEGER NOT NULL CHECK(quantity > 0), unit_price REAL NOT NULL,
  unit_cost REAL, total_line REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS payments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  invoice_id TEXT NOT NULL UNIQUE REFERENCES invoices(id) ON DELETE CASCADE,
  cash_session_id INTEGER NOT NULL REFERENCES cash_sessions(id),
  method TEXT NOT NULL DEFAULT 'CASH' CHECK(method IN ('CASH')),
  amount REAL NOT NULL CHECK(amount >= 0),
  amount_received REAL NOT NULL CHECK(amount_received >= 0),
  change_amount REAL NOT NULL DEFAULT 0 CHECK(change_amount >= 0),
  status TEXT NOT NULL DEFAULT 'CAPTURED' CHECK(status IN ('CAPTURED','REFUNDED')),
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS attendances (
  id INTEGER PRIMARY KEY AUTOINCREMENT, employee_id INTEGER NOT NULL REFERENCES users(id),
  start_time TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, end_time TEXT,
  session_ref TEXT UNIQUE, source TEXT NOT NULL DEFAULT 'AUTHENTICATION',
  status TEXT NOT NULL DEFAULT 'VALID' CHECK(status IN ('VALID','CORRECTED','INTERRUPTED')),
  original_start_time TEXT, original_end_time TEXT, correction_reason TEXT,
  corrected_by INTEGER REFERENCES users(id), corrected_at TEXT
);
CREATE TABLE IF NOT EXISTS messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT, sender_id INTEGER REFERENCES users(id), recipient_type TEXT NOT NULL,
  recipient_id INTEGER REFERENCES users(id), subject TEXT NOT NULL, content TEXT NOT NULL, type TEXT DEFAULT 'message',
  request_id TEXT UNIQUE,
  is_read INTEGER DEFAULT 0, created_at TEXT DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS message_reads (
  message_id INTEGER NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  read_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(message_id,user_id)
);
CREATE TABLE IF NOT EXISTS message_deletions (
  message_id INTEGER NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  deleted_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(message_id,user_id)
);
CREATE TABLE IF NOT EXISTS notifications (
  id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, product_id INTEGER REFERENCES products(id),
  message TEXT NOT NULL, is_read INTEGER DEFAULT 0, resolved_at TEXT, created_at TEXT DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS stock_movements (
  id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER NOT NULL REFERENCES products(id),
  quantity INTEGER NOT NULL, reason TEXT NOT NULL, reference_id TEXT, unit_price REAL NOT NULL DEFAULT 0,
  created_at TEXT DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS product_price_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER NOT NULL REFERENCES products(id),
  price REAL NOT NULL CHECK(price >= 0), recorded_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS email_report_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  recipient TEXT NOT NULL,
  subject TEXT NOT NULL,
  filename TEXT NOT NULL,
  message_text TEXT NOT NULL DEFAULT '',
  attachment BLOB,
  status TEXT NOT NULL DEFAULT 'pending',
  attempts INTEGER NOT NULL DEFAULT 0,
  next_attempt_at TEXT,
  last_error TEXT,
  sent_at TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS audit_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, action TEXT NOT NULL, entity TEXT NOT NULL,
  entity_id TEXT, details TEXT, outcome TEXT NOT NULL DEFAULT 'SUCCESS',
  created_at TEXT DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_products_search ON products(name, category, hashtag);
CREATE UNIQUE INDEX IF NOT EXISTS idx_products_reference ON products(reference) WHERE reference IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_inventory_status ON inventory_counts(status,created_at);
CREATE INDEX IF NOT EXISTS idx_purchases_status_date ON purchases(status,created_at);
CREATE INDEX IF NOT EXISTS idx_purchase_items_purchase ON purchase_items(purchase_id,product_id);
CREATE INDEX IF NOT EXISTS idx_employees_status ON employees(status, last_name, first_name);
CREATE INDEX IF NOT EXISTS idx_invoices_date ON invoices(invoice_date);
CREATE UNIQUE INDEX IF NOT EXISTS idx_cash_session_open_employee
  ON cash_sessions(employee_id) WHERE status='OPEN';
CREATE INDEX IF NOT EXISTS idx_payments_cash_session ON payments(cash_session_id,created_at);
CREATE INDEX IF NOT EXISTS idx_attendances_employee ON attendances(employee_id, start_time);
CREATE INDEX IF NOT EXISTS idx_message_reads_user ON message_reads(user_id, message_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_date ON stock_movements(created_at, product_id);
CREATE INDEX IF NOT EXISTS idx_price_history_date ON product_price_history(recorded_at, product_id);
CREATE INDEX IF NOT EXISTS idx_email_queue_status ON email_report_logs(status,next_attempt_at,created_at);
`;
