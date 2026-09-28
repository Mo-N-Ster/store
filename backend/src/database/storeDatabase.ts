import Database from 'better-sqlite3';
import bcrypt from 'bcryptjs';
import { app, safeStorage } from 'electron';
import fs from 'node:fs';
import path from 'node:path';
import { randomBytes } from 'node:crypto';
import { randomUUID } from 'node:crypto';
import { schema } from './schema.js';
import { logTechnical } from '../services/technicalLogger.js';
import {
  applyDatabaseMigrations,
  hasPendingDatabaseMigrations,
  latestSchemaVersion,
} from './migrations.js';
import type { UserInput } from '../domain/user/user.types.js';
import { publicIdentity } from '../domain/user/publicIdentity.js';
import { validateHistoryDeletion, validateHistoryType } from '../domain/rbac/historyOperation.js';
import { validateUser } from '../domain/user/user.validators.js';
import type { ProductInput } from '../domain/product/product.types.js';
import { validateProduct } from '../domain/product/product.validators.js';
import type { SaleLineInput } from '../domain/sale/sale.types.js';
import { canonicalSale } from '../domain/sale/canonicalSale.js';
import { sendEmail } from '../services/emailService.js';
import { readStoreData } from '../services/stockPdfService.js';
import { isLoginLocked, loginPolicy, nextFailedLogin } from '../domain/auth/loginPolicy.js';
import { canEmployeeAuthenticate, isEmployeeStatus } from '../domain/employee/employeeStatus.js';
import { validateAttendanceCorrection } from '../domain/attendance/attendancePolicy.js';
import {
  performExplicitAttendance,
  type ExplicitAttendanceInput,
} from '../domain/attendance/explicitAttendance.js';
import { validateInventoryQuantity, validateStockAdjustment } from '../domain/stock/stockPolicy.js';
import { canEditPurchase, validatePurchaseItem } from '../domain/purchase/purchasePolicy.js';
import { validateUserPhoto } from '../domain/user/userPhoto.js';
import { permissionSnapshot, savePermissionDenials } from '../domain/rbac/userPermissions.js';
import { readAudit } from '../domain/system/auditReader.js';
import { validateSettings } from '../domain/system/settingsPolicy.js';
import { recoverReset, resetWithMedia } from '../domain/system/resetRecovery.js';
import { writeAudit } from '../domain/system/auditWriter.js';
import { foldIdentity } from '../domain/auth/identity.js';
import { finishPendingRestore, hasPendingRestore, markPendingRestore, recoverPendingRestore } from '../domain/backup/restoreRecovery.js';
import { calculateCashDifference, validateMoneyAmount } from '../domain/cash/cashPolicy.js';
import { validateBackupDatabase } from '../domain/backup/backupValidation.js';
import { nextEmailRetry } from '../domain/email/emailRetryPolicy.js';
import { storageHealth } from '../domain/system/storagePolicy.js';
import { assertAdministrativePasswordTarget as assertPasswordTargetPolicy } from '../domain/auth/adminPasswordPolicy.js';
import {
  articleImageDataUrl,
  cleanupStaleArticleStaging,
  commitStagedArticleImage,
  assertArticleMediaRef,
  managedArticlePath,
} from '../domain/media/articleMedia.js';
import {
  createBackupBundle,
  inspectAndStageBackupBundle,
  mediaReferencesFromManifest,
} from '../domain/backup/backupBundle.js';

let db: Database.Database;
let databaseFile = '';
let destructiveOperation = false;
const now = () => new Date().toISOString();
const initials = (first: string, last: string) => `${first[0] ?? ''}${last[0] ?? ''}`.toUpperCase();
const invalidAuthenticationHash = bcrypt.hashSync('STORE_INVALID_AUTHENTICATION_TARGET', 10);

export async function initDatabase() {
  const dir = app.getPath('userData');
  fs.mkdirSync(dir, { recursive: true });
  recoverPendingRestore(dir);
  databaseFile = path.join(dir, 'store.db');
  db = new Database(databaseFile);
  configureDatabase();
  const existingTables = (
    db
      .prepare(
        "SELECT COUNT(*) count FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
      )
      .get() as {
      count: number;
    }
  ).count;
  if (existingTables > 0 && hasPendingDatabaseMigrations(db)) await createMigrationBackup();
  // A legacy database must receive its ALTER TABLE migrations before the current
  // schema creates indexes that refer to the newly added columns. A fresh database
  // needs the base tables first so the same migrations can be recorded safely.
  if (existingTables === 0) {
    db.exec(schema);
    applyDatabaseMigrations(db);
  } else {
    applyDatabaseMigrations(db);
    db.exec(schema);
  }
  recoverReset(db, dir);
  cleanupArticleMedia(() => cleanupStaleArticleStaging(articleMediaDir()), 'startup-staging');
  db.prepare("UPDATE email_report_logs SET status='pending' WHERE status='sending'").run();
  migrateSmtpSecret();
  recordHeartbeat();
  await ensureDailyBackup();
}

export function closeDatabase() {
  if (!db?.open) return;
  try {
    db.pragma('wal_checkpoint(TRUNCATE)');
  } finally {
    db.close();
  }
}

export function recordHeartbeat() {
  if (!db?.open) return;
  db.prepare(
    "INSERT INTO settings(key,value) VALUES('lastHeartbeat',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
  ).run(now());
}

export function permissionsForUser(userId: number) {
  return permissionSnapshot(db, userId).effective;
}

export function sessionAuthority(userId: number) {
  return db.prepare('SELECT id,role,active FROM users WHERE id=?').get(userId) as { id: number; role: 'owner' | 'manager' | 'employee'; active: number } | undefined;
}

export const api = {
  auditLogs: (filters: Parameters<typeof readAudit>[1]) => readAudit(db, filters),
  userPermissions: ({ userId }: { userId: number }) => permissionSnapshot(db, userId),
  saveUserPermissions: (input: Parameters<typeof savePermissionDenials>[1]) => savePermissionDenials(db, input),
  needsSetup: () =>
    (
      db.prepare('SELECT COUNT(*) count FROM users WHERE role = ?').get('owner') as {
        count: number;
      }
    ).count === 0,
  setupAdmin: (input: UserInput) => {
    if (!api.needsSetup()) throw new Error('SETUP_ALREADY_COMPLETED');
    validateUser({ ...input, role: 'owner' }, true);
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
        'owner',
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
    const normalizedIdentifier = typeof identifier === 'string' ? identifier.trim() : '';
    const user = findAuthenticationUser(normalizedIdentifier, role);
    const currentTime = new Date();
    const passwordMatches = bcrypt.compareSync(
      typeof password === 'string' ? password : '',
      user?.password_hash || invalidAuthenticationHash,
    );
    const employmentAllowsLogin =
      !user?.employment_status ||
      (isEmployeeStatus(user.employment_status) && canEmployeeAuthenticate(user.employment_status));
    if (
      !user ||
      !user.active ||
      !employmentAllowsLogin ||
      isLoginLocked(user.locked_until, currentTime) ||
      !passwordMatches
    ) {
      if (user) {
        if (!isLoginLocked(user.locked_until, currentTime)) {
          const failure = nextFailedLogin(
            Number(user.failed_login_attempts || 0),
            loginPolicy(settingsObject()),
            currentTime,
          );
          db.prepare('UPDATE users SET failed_login_attempts=?,locked_until=? WHERE id=?').run(
            failure.failedAttempts,
            failure.lockedUntil,
            user.id,
          );
        }
        audit(user.id, 'login_failed', 'session', String(user.id), 'FAILURE');
      } else audit(null, 'login_failed', 'session', '', 'FAILURE');
      throw new Error('INVALID_CREDENTIALS');
    }
    db.prepare(
      'UPDATE users SET failed_login_attempts=0,locked_until=NULL,last_login_at=? WHERE id=?',
    ).run(now(), user.id);
    audit(user.id, 'login', 'session', String(user.id));
    return publicIdentity(user);
  },
  switchUser: ({
    currentUserId,
    identifier,
    password,
  }: {
    currentUserId: number;
    identifier: string;
    password: string;
  }) => {
    const openCash = db
      .prepare("SELECT id FROM cash_sessions WHERE employee_id=? AND status='OPEN'")
      .get(currentUserId);
    if (openCash) throw new Error('CASH_SESSION_OPEN');
    const normalizedIdentifier = typeof identifier === 'string' ? identifier.trim() : '';
    const user = findAuthenticationUser(normalizedIdentifier);
    const currentTime = new Date();
    const passwordMatches = bcrypt.compareSync(
      typeof password === 'string' ? password : '',
      user?.password_hash || invalidAuthenticationHash,
    );
    const employmentAllowsLogin =
      !user?.employment_status ||
      (isEmployeeStatus(user.employment_status) && canEmployeeAuthenticate(user.employment_status));
    if (
      !user ||
      !user.active ||
      !employmentAllowsLogin ||
      isLoginLocked(user.locked_until, currentTime) ||
      !passwordMatches
    ) {
      if (user && !isLoginLocked(user.locked_until, currentTime)) {
        const failure = nextFailedLogin(
          Number(user.failed_login_attempts || 0),
          loginPolicy(settingsObject()),
          currentTime,
        );
        db.prepare('UPDATE users SET failed_login_attempts=?,locked_until=? WHERE id=?').run(
          failure.failedAttempts,
          failure.lockedUntil,
          user.id,
        );
      }
      audit(
        user?.id || null,
        'user_switch_failed',
        'session',
        user ? String(user.id) : '',
        'FAILURE',
      );
      throw new Error('INVALID_CREDENTIALS');
    }
    db.prepare(
      'UPDATE users SET failed_login_attempts=0,locked_until=NULL,last_login_at=? WHERE id=?',
    ).run(now(), user.id);
    audit(
      currentUserId,
      user.id === currentUserId ? 'user_switch_same' : 'user_switch',
      'session',
      String(user.id),
      'SUCCESS',
      { targetUserId: user.id },
    );
    return publicIdentity(user);
  },
  verifyAdmin: ({ id, password }: { id: number; password: string }) => {
    const user = db
      .prepare("SELECT * FROM users WHERE id=? AND role IN ('owner','manager') AND active=1")
      .get(id) as any;
    return Boolean(user && bcrypt.compareSync(password, user.password_hash));
  },
  users: () =>
    db
      .prepare(
        `SELECT users.id,users.username,users.email,users.role,
          users.first_name firstName,users.last_name lastName,users.initials,users.phone,users.photo,
          users.hire_date hireDate,users.active,users.security_question securityQuestion,
          employees.id employeeId,employees.employee_code employeeCode,
          employees.status employmentStatus,employees.address,
          employees.end_date endDate
        FROM users LEFT JOIN employees ON employees.user_id=users.id
        ORDER BY users.active DESC,users.last_name`,
      )
      .all(),
  saveUser: (input: UserInput & { id?: number }) => db.transaction(() => {
    validateUser(input, false);
    const photo = input.photo === undefined ? undefined : validateUserPhoto(input.photo);
    const role = input.role ?? 'employee';
    if (input.password && input.password.length < 8) throw new Error('WEAK_PASSWORD');
    if (!['owner', 'manager', 'employee'].includes(role)) throw new Error('INVALID_USER');
    if (role === 'owner' && (!input.id || (db.prepare('SELECT role FROM users WHERE id=?').get(input.id) as { role: string } | undefined)?.role !== 'owner')) throw new Error('LAST_OWNER_REQUIRED');
    const duplicateUser = db
      .prepare(
        `SELECT id FROM users WHERE id<>COALESCE(?,0) AND (
          (identity_fold(first_name)=identity_fold(?) AND identity_fold(last_name)=identity_fold(?))
          OR identity_fold(username)=identity_fold(?)
          OR (?<>'' AND identity_fold(COALESCE(email,''))=identity_fold(?))
        )`,
      )
      .get(
        input.id ?? null,
        input.firstName,
        input.lastName,
        input.username.trim(),
        input.email?.trim() || '',
        input.email?.trim() || '',
      );
    if (duplicateUser) throw new Error('DUPLICATE_USER');
    if (
      !input.id &&
      role !== 'employee' &&
      (!input.securityQuestion?.trim() || !input.securityAnswer?.trim())
    )
      throw new Error('SECURITY_QUESTION_REQUIRED');
    if (input.id) {
      const existingUser = db.prepare('SELECT role,active FROM users WHERE id=?').get(input.id) as
        { role: string; active: number } | undefined;
      if (!existingUser) throw new Error('USER_NOT_FOUND');
      if (existingUser.role === 'owner' && (role !== 'owner' || input.active === false))
        throw new Error('LAST_OWNER_REQUIRED');
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
      if (role !== 'employee' && input.securityQuestion && input.securityAnswer)
        db.prepare('UPDATE users SET security_question=?,security_answer_hash=? WHERE id=?').run(
          input.securityQuestion.trim(),
          bcrypt.hashSync(input.securityAnswer.trim().toLowerCase(), 10),
          input.id,
        );
      enforceSingleAdmin(input.id, role);
      if (photo !== undefined) db.prepare('UPDATE users SET photo=? WHERE id=?').run(photo, input.id);
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
        role !== 'employee' ? input.securityQuestion?.trim() : null,
        role !== 'employee' && input.securityAnswer
          ? bcrypt.hashSync(input.securityAnswer.trim().toLowerCase(), 10)
          : null,
      );
    enforceSingleAdmin(Number(result.lastInsertRowid), role);
    if (photo !== undefined) db.prepare('UPDATE users SET photo=? WHERE id=?').run(photo, result.lastInsertRowid);
    return { user: publicUser(Number(result.lastInsertRowid)), temporaryPassword: password };
  })(),

  resetPassword: (id: number) => {
    const target = db.prepare('SELECT role FROM users WHERE id=?').get(id) as
      { role: string } | undefined;
    if (!target || target.role !== 'employee') throw new Error('SECURITY_QUESTION_REQUIRED');
    const password = temporaryPassword();
    db.prepare(
      'UPDATE users SET password_hash=?,failed_login_attempts=0,locked_until=NULL WHERE id=?',
    ).run(bcrypt.hashSync(password, 10), id);
    return password;
  },
  generateUserPassword: ({
    id,
    actorId,
    actorRole,
  }: {
    id: number;
    actorId: number;
    actorRole: string;
  }) => {
    assertAdministrativePasswordTarget(id, actorId, actorRole);
    const password = temporaryPassword();
    db.prepare(
      'UPDATE users SET password_hash=?,failed_login_attempts=0,locked_until=NULL WHERE id=?',
    ).run(bcrypt.hashSync(password, 10), id);
    audit(actorId, 'password_reset_automatic', 'user', String(id));
    return password;
  },
  setUserPassword: ({
    id,
    newPassword,
    actorId,
    actorRole,
  }: {
    id: number;
    newPassword: string;
    actorId: number;
    actorRole: string;
  }) => {
    assertAdministrativePasswordTarget(id, actorId, actorRole);
    if (typeof newPassword !== 'string' || newPassword.length < 8) throw new Error('WEAK_PASSWORD');
    db.prepare(
      'UPDATE users SET password_hash=?,failed_login_attempts=0,locked_until=NULL WHERE id=?',
    ).run(bcrypt.hashSync(newPassword, 10), id);
    audit(actorId, 'password_reset_manual', 'user', String(id));
    return true;
  },
  securityQuestion: (id: number) => {
    const row = db
      .prepare(
        "SELECT security_question question FROM users WHERE id=? AND role IN ('owner','manager')",
      )
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
    if (typeof newPassword !== 'string' || newPassword.length < 8) throw new Error('WEAK_PASSWORD');
    if (typeof answer !== 'string' || !answer.trim()) throw new Error('INVALID_SECURITY_ANSWER');
    const row = db
      .prepare(
        `SELECT security_answer_hash hash,failed_recovery_attempts attempts,
          recovery_locked_until recoveryLockedUntil
         FROM users WHERE id=? AND role IN ('owner','manager') AND active=1`,
      )
      .get(id) as
      { hash: string; attempts: number; recoveryLockedUntil: string | null } | undefined;
    const currentTime = new Date();
    if (row && isLoginLocked(row.recoveryLockedUntil, currentTime)) {
      audit(id, 'password_recovery_locked', 'user', String(id), 'FAILURE');
      throw new Error('INVALID_SECURITY_ANSWER');
    }
    if (!row?.hash || !bcrypt.compareSync(answer.trim().toLowerCase(), row.hash)) {
      if (row) {
        const failure = nextFailedLogin(row.attempts, loginPolicy(settingsObject()), currentTime);
        db.prepare(
          'UPDATE users SET failed_recovery_attempts=?,recovery_locked_until=? WHERE id=?',
        ).run(failure.failedAttempts, failure.lockedUntil, id);
      }
      audit(id, 'password_recovery_failed', 'user', String(id), 'FAILURE');
      throw new Error('INVALID_SECURITY_ANSWER');
    }
    db.prepare(
      `UPDATE users SET password_hash=?,failed_login_attempts=0,locked_until=NULL,
        failed_recovery_attempts=0,recovery_locked_until=NULL WHERE id=?`,
    ).run(bcrypt.hashSync(newPassword, 10), id);
    audit(id, 'password_recovered', 'user', String(id));
    return true;
  },
  forgotPasswordQuestion: (identifier: string) => {
    const user = findAuthenticationUser(identifier, 'manager');
    const row = user?.active && !isLoginLocked(user.recovery_locked_until, new Date()) ? { id: user.id, question: user.security_question } : undefined;
    if (!row?.question) throw new Error('QUESTION_NOT_CONFIGURED');
    return row;
  },
  recoverPassword: ({
    id,
    answer,
    newPassword,
  }: {
    id: number;
    answer: string;
    newPassword: string;
  }) => api.resetManagerPassword({ id, answer, newPassword }),
  products: ({ search = '', category = '' } = {}) =>
    db
      .prepare(
        `SELECT id,reference,name,hashtag,category,description,price,stock_quantity stockQuantity,min_stock_threshold minStockThreshold,image_ref imageRef
    FROM products WHERE deleted_at IS NULL AND (?='' OR name LIKE ? OR category LIKE ? OR hashtag LIKE ?) AND (?='' OR category=?) ORDER BY name`,
      )
      .all(search, `%${search}%`, `%${search}%`, `%${search}%`, category, category),
  articleImage: (reference: string) => articleImageDataUrl(reference, articleMediaDir()),
  saveProduct: (input: ProductInput & { id?: number }) => {
    validateProduct(input);
    const duplicateProduct = db
      .prepare(
        `SELECT id FROM products WHERE deleted_at IS NULL AND id<>COALESCE(?,0) AND (lower(name)=lower(?) OR (?<>'' AND lower(COALESCE(hashtag,''))=lower(?)))`,
      )
      .get(
        input.id ?? null,
        input.name.trim(),
        input.hashtag?.trim() || '',
        input.hashtag?.trim() || '',
      );
    if (duplicateProduct) throw new Error('DUPLICATE_PRODUCT');
    const committed = input.imageToken
      ? commitStagedArticleImage(input.imageToken, articleMediaDir())
      : null;
    let persisted = false;
    try {
      const result = db.transaction(() => {
        if (input.id) {
          const old = db
            .prepare(
              'SELECT stock_quantity stock,price,image_ref imageRef FROM products WHERE id=?',
            )
            .get(input.id) as { stock: number; price: number; imageRef: string | null } | undefined;
          if (!old) throw new Error('NOT_FOUND');
          const imageRef = committed?.reference ?? (input.removeImage ? null : old.imageRef);
          db.prepare(
            `UPDATE products SET name=?,hashtag=?,category=?,description=?,price=?,stock_quantity=?,min_stock_threshold=?,image_ref=?,updated_at=? WHERE id=?`,
          ).run(
            input.name,
            input.hashtag ?? '',
            input.category,
            input.description ?? '',
            input.price,
            input.stockQuantity,
            input.minStockThreshold,
            imageRef,
            now(),
            input.id,
          );
          const delta = input.stockQuantity - old.stock;
          if (delta)
            db.prepare(
              'INSERT INTO stock_movements(product_id,quantity,reason,unit_price) VALUES(?,?,?,?)',
            ).run(input.id, delta, 'adjustment', input.price);
          if (old.price !== input.price)
            db.prepare('INSERT INTO product_price_history(product_id,price) VALUES(?,?)').run(
              input.id,
              input.price,
            );
          checkStock(input.id);
          return { id: input.id, oldImageRef: old.imageRef, imageRef };
        }
        const row = db
          .prepare(
            `INSERT INTO products(name,hashtag,category,description,price,stock_quantity,min_stock_threshold,image_ref) VALUES(?,?,?,?,?,?,?,?)`,
          )
          .run(
            input.name,
            input.hashtag ?? '',
            input.category,
            input.description ?? '',
            input.price,
            input.stockQuantity,
            input.minStockThreshold,
            committed?.reference ?? null,
          );
        const id = Number(row.lastInsertRowid);
        db.prepare('INSERT INTO product_price_history(product_id,price) VALUES(?,?)').run(
          id,
          input.price,
        );
        if (input.stockQuantity)
          db.prepare(
            'INSERT INTO stock_movements(product_id,quantity,reason,unit_price) VALUES(?,?,?,?)',
          ).run(id, input.stockQuantity, 'initial', input.price);
        checkStock(id);
        return { id, oldImageRef: null, imageRef: committed?.reference ?? null };
      })();
      persisted = true;
      if (committed) cleanupArticleMedia(() => fs.rmSync(managedArticlePath(articleMediaDir(), committed.reference, true), { force: true }), 'staging');
      if (result.oldImageRef && result.oldImageRef !== result.imageRef)
        cleanupArticleMedia(() => removeUnreferencedArticleImage(result.oldImageRef!), 'obsolete');
      return result.id;
    } catch (error) {
      if (committed && !persisted)
        cleanupArticleMedia(() => removeUnreferencedArticleImage(committed.reference), 'rollback');
      throw error;
    }
  },
  deleteProduct: ({ id, userId }: { id: number; userId: number }) => {
    db.transaction(() => {
      const product = db
        .prepare('SELECT stock_quantity stockQuantity,price FROM products WHERE id=?')
        .get(id) as { stockQuantity: number; price: number } | undefined;
      if (!product) return;
      if (product.stockQuantity > 0)
        db.prepare(
          'INSERT INTO stock_movements(product_id,quantity,reason,unit_price) VALUES(?,?,?,?)',
        ).run(id, -product.stockQuantity, 'product_deletion', product.price);
      db.prepare('UPDATE products SET deleted_at=?,stock_quantity=0 WHERE id=?').run(now(), id);
      audit(userId, 'delete', 'product', String(id));
    })();
  },
  createInvoice: ({
    employeeId,
    lines,
    discount = 0,
    amountReceived,
    idempotencyKey,
  }: {
    employeeId: number;
    lines: SaleLineInput[];
    discount?: number;
    amountReceived?: number;
    idempotencyKey?: string;
  }) =>
    db.transaction(() => {
      if (idempotencyKey !== undefined && typeof idempotencyKey !== 'string')
        throw new Error('VALIDATION_ERROR');
      const requestKey = idempotencyKey?.trim() || null;
      if (requestKey && (requestKey.length > 128 || !/^[A-Za-z0-9_-]+$/.test(requestKey)))
        throw new Error('VALIDATION_ERROR');
      const command = canonicalSale(lines, discount, amountReceived);
      const cashSession = db
        .prepare("SELECT id FROM cash_sessions WHERE employee_id=? AND status='OPEN'")
        .get(employeeId) as { id: number } | undefined;
      if (!cashSession) throw new Error('CASH_SESSION_REQUIRED');
      if (requestKey) {
        const existing = db
          .prepare('SELECT id,employee_id,cash_session_id,status,idempotency_request FROM invoices WHERE idempotency_key=?')
          .get(requestKey) as { id: string; employee_id: number; cash_session_id: number; status: string; idempotency_request: string | null } | undefined;
        if (existing) {
          if (existing.employee_id !== employeeId || existing.cash_session_id !== cashSession.id ||
              existing.status !== 'validated' || existing.idempotency_request !== command)
            throw new Error('CONFLICT');
          return invoiceDetail(existing.id);
        }
      }
      if (!lines.length || discount < 0) throw new Error('INVALID_INVOICE');
      if (!Number.isFinite(discount)) throw new Error('INVALID_INVOICE');
      const products = lines.map((line) => ({
        line,
        product: db
          .prepare('SELECT * FROM products WHERE id=? AND deleted_at IS NULL')
          .get(line.productId) as any,
      }));
      for (const { line, product } of products)
        if (
          !product ||
          !Number.isInteger(line.quantity) ||
          line.quantity <= 0 ||
          product.stock_quantity < line.quantity
        )
          throw new Error('INSUFFICIENT_STOCK');
      const storeSettings = settingsObject();
      const subtotal = validateMoneyAmount(
        products.reduce((sum, x) => sum + x.line.quantity * x.product.price, 0),
      );
      const discountsEnabled = storeSettings.discountsEnabled !== 'false';
      const applied = validateMoneyAmount(discountsEnabled ? Math.min(discount, subtotal) : 0);
      const total = validateMoneyAmount(subtotal - applied);
      const received = validateMoneyAmount(amountReceived ?? total);
      if (received < total) throw new Error('VALIDATION_ERROR');
      const change = Math.round((received - total) * 100) / 100;
      const base = invoiceId();
      let id = base;
      let n = 1;
      while (db.prepare('SELECT 1 FROM invoices WHERE id=?').get(id)) id = `${base}-${n++}`;
      db.prepare(
        `INSERT INTO invoices(
          id,employee_id,cash_session_id,idempotency_key,invoice_date,subtotal,total_amount,discount,
          store_name,store_address,store_phone,store_email,currency,idempotency_request
        ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)`,
      ).run(
        id,
        employeeId,
        cashSession.id,
        requestKey,
        now(),
        subtotal,
        total,
        applied,
        storeSettings.storeName || 'STORE',
        storeSettings.address || '',
        storeSettings.phone || '',
        storeSettings.email || '',
        storeSettings.currency || 'EUR',
        command,
      );
      for (const { line, product } of products) {
        const cost = db
          .prepare(
            `SELECT unit_price unitCost FROM stock_movements
             WHERE product_id=? AND quantity>0 AND unit_price>=0 AND reason IN ('purchase','initial')
             ORDER BY created_at DESC,id DESC LIMIT 1`,
          )
          .get(product.id) as { unitCost: number } | undefined;
        const stockUpdate = db
          .prepare(
            `UPDATE products SET stock_quantity=stock_quantity-?,updated_at=?
           WHERE id=? AND deleted_at IS NULL AND stock_quantity>=?`,
          )
          .run(line.quantity, now(), product.id, line.quantity);
        if (stockUpdate.changes !== 1) throw new Error('INSUFFICIENT_STOCK');
        db.prepare(
          `INSERT INTO invoice_lines(
            invoice_id,product_id,product_name,category,quantity,unit_price,unit_cost,total_line
          ) VALUES(?,?,?,?,?,?,?,?)`,
        ).run(
          id,
          product.id,
          product.name,
          product.category,
          line.quantity,
          product.price,
          cost?.unitCost ?? product.price,
          line.quantity * product.price,
        );
        db.prepare(
          'INSERT INTO stock_movements(product_id,quantity,reason,reference_id,unit_price) VALUES(?,?,?,?,?)',
        ).run(product.id, -line.quantity, 'sale', id, product.price);
        checkStock(product.id);
      }
      db.prepare(
        `INSERT INTO payments(
          invoice_id,cash_session_id,method,amount,amount_received,change_amount
        ) VALUES(?,?,'CASH',?,?,?)`,
      ).run(id, cashSession.id, total, received, change);
      return invoiceDetail(id);
    })(),
  invoices: ({
    from = '',
    to = '',
    search = '',
    productId = 0,
    category = '',
  }: {
    from?: string;
    to?: string;
    search?: string;
    productId?: number;
    category?: string;
  } = {}) => {
    if (!Number.isInteger(productId) || productId < 0 || category.length > 120)
      throw new Error('VALIDATION_ERROR');
    return db
      .prepare(
        `SELECT i.id,i.invoice_date invoiceDate,i.total_amount totalAmount,i.discount,i.status,
          i.cancellation_reason cancellationReason,u.first_name||' '||u.last_name seller
         FROM invoices i JOIN users u ON u.id=i.employee_id
         WHERE (?='' OR date(i.invoice_date)>=date(?)) AND (?='' OR date(i.invoice_date)<=date(?))
           AND (?='' OR i.id LIKE ? OR u.username LIKE ?)
           AND (?=0 OR EXISTS (SELECT 1 FROM invoice_lines il WHERE il.invoice_id=i.id AND il.product_id=?))
           AND (?='' OR EXISTS (SELECT 1 FROM invoice_lines il WHERE il.invoice_id=i.id AND il.category=?))
         ORDER BY i.invoice_date DESC`,
      )
      .all(
        from,
        from,
        to,
        to,
        search,
        `%${search}%`,
        `%${search}%`,
        productId,
        productId,
        category,
        category,
      );
  },
  invoice: (id: string) => invoiceDetail(id),
  currentCashSession: ({ employeeId }: { employeeId: number }) =>
    db
      .prepare(
        `SELECT cash_sessions.*,
        cash_sessions.opening_amount+
          COALESCE((SELECT SUM(amount) FROM payments WHERE cash_session_id=cash_sessions.id AND status='CAPTURED'),0)
          expectedAmount
       FROM cash_sessions WHERE employee_id=? AND status='OPEN'`,
      )
      .get(employeeId),
  openCashSession: ({ employeeId, openingAmount }: { employeeId: number; openingAmount: number }) =>
    db.transaction(() => {
      const amount = validateMoneyAmount(openingAmount);
      const existing = db
        .prepare("SELECT * FROM cash_sessions WHERE employee_id=? AND status='OPEN'")
        .get(employeeId);
      if (existing) return existing;
      const reference = `CAISSE-${new Date().toISOString().replace(/\D/g, '').slice(0, 14)}-${randomBytes(3).toString('hex').toUpperCase()}`;
      const result = db
        .prepare(
          `INSERT INTO cash_sessions(reference,employee_id,status,opening_amount)
         VALUES(?,?,'OPEN',?)`,
        )
        .run(reference, employeeId, amount);
      const id = Number(result.lastInsertRowid);
      audit(employeeId, 'cash_session_opened', 'cash_session', String(id), 'SUCCESS', {
        openingAmount: amount,
      });
      return db.prepare('SELECT * FROM cash_sessions WHERE id=?').get(id);
    })(),
  closeCashSession: ({
    employeeId,
    countedAmount,
  }: {
    employeeId: number;
    countedAmount: number;
  }) =>
    db.transaction(() => {
      const counted = validateMoneyAmount(countedAmount);
      const session = db
        .prepare(
          `SELECT cash_sessions.id,cash_sessions.opening_amount openingAmount,
          cash_sessions.opening_amount+
            COALESCE((SELECT SUM(amount) FROM payments WHERE cash_session_id=cash_sessions.id AND status='CAPTURED'),0)
            expectedAmount
         FROM cash_sessions WHERE employee_id=? AND status='OPEN'`,
        )
        .get(employeeId) as
        { id: number; openingAmount: number; expectedAmount: number } | undefined;
      if (!session) throw new Error('CASH_SESSION_REQUIRED');
      const difference = calculateCashDifference(session.expectedAmount, counted);
      db.prepare(
        `UPDATE cash_sessions SET status='CLOSED',closed_at=?,closing_amount=?,
          expected_amount=?,difference=?,closed_by=? WHERE id=? AND status='OPEN'`,
      ).run(now(), counted, session.expectedAmount, difference, employeeId, session.id);
      audit(employeeId, 'cash_session_closed', 'cash_session', String(session.id), 'SUCCESS', {
        expectedAmount: session.expectedAmount,
        countedAmount: counted,
        difference,
      });
      return { ...session, countedAmount: counted, difference };
    })(),
  deleteInvoice: ({ id, userId, reason }: { id: string; userId: number; reason: string }) =>
    db.transaction(() => {
      const cancellationReason = reason?.trim();
      if (!cancellationReason || cancellationReason.length < 3) throw new Error('VALIDATION_ERROR');
      const invoice = db.prepare('SELECT status FROM invoices WHERE id=?').get(id) as
        { status: string } | undefined;
      if (!invoice) throw new Error('NOT_FOUND');
      if (invoice.status === 'cancelled') return invoiceDetail(id);
      const lines = db
        .prepare(
          'SELECT product_id productId,quantity,unit_price unitPrice FROM invoice_lines WHERE invoice_id=?',
        )
        .all(id) as any[];
      for (const line of lines)
        if (line.productId) {
          db.prepare('UPDATE products SET stock_quantity=stock_quantity+? WHERE id=?').run(
            line.quantity,
            line.productId,
          );
          db.prepare(
            'INSERT INTO stock_movements(product_id,quantity,reason,reference_id,unit_price) VALUES(?,?,?,?,?)',
          ).run(line.productId, line.quantity, 'invoice_reversal', id, line.unitPrice);
          checkStock(line.productId);
        }
      db.prepare(
        "UPDATE payments SET status='REFUNDED' WHERE invoice_id=? AND status='CAPTURED'",
      ).run(id);
      db.prepare(
        "UPDATE invoices SET status='cancelled',cancelled_by=?,cancelled_at=?,cancellation_reason=? WHERE id=? AND status='validated'",
      ).run(userId, now(), cancellationReason, id);
      audit(userId, 'invoice_cancelled', 'invoice', id, 'SUCCESS', { reason: cancellationReason });
      return invoiceDetail(id);
    })(),
  history: ({
    type,
    from = '',
    to = '',
  }: {
    type: 'sales' | 'purchases' | 'personnel';
    from?: string;
    to?: string;
  }) => {
    validateHistoryType(type);
    const args = [from, from, to, to];
    if (type === 'purchases')
      return db
        .prepare(
          `SELECT sm.id,sm.created_at date,p.name product,p.category,sm.reason,
            sm.quantity,sm.unit_price unitPrice,ROUND(sm.quantity*sm.unit_price,2) total
          FROM stock_movements sm JOIN products p ON p.id=sm.product_id
          WHERE sm.quantity>0 AND (?='' OR date(sm.created_at)>=date(?))
            AND (?='' OR date(sm.created_at)<=date(?))
          ORDER BY sm.created_at DESC`,
        )
        .all(...args);
    if (type === 'personnel')
      return db
        .prepare(
          `SELECT a.id,a.start_time startTime,a.end_time endTime,
            u.first_name||' '||u.last_name employee,u.role,a.status,
            a.session_ref sessionReference,a.correction_reason correctionReason,
            ROUND((julianday(COALESCE(a.end_time,CURRENT_TIMESTAMP))-julianday(a.start_time))*24,2) hours
          FROM attendances a JOIN users u ON u.id=a.employee_id
          WHERE (?='' OR date(a.start_time)>=date(?)) AND (?='' OR date(a.start_time)<=date(?))
          ORDER BY a.start_time DESC`,
        )
        .all(...args);
    return api.invoices({ from, to, search: '' });
  },
  deleteHistory: (input: unknown) => {
    const { type, ids } = validateHistoryDeletion(input);
    const userId = (input as { userId: number }).userId;
    return db.transaction(() => {
      if (!ids.length) return 0;
      const placeholders = ids.map(() => '?').join(',');
      // Fixed backend resource; no discriminator is interpolated into SQL.
      const result = db.prepare(`DELETE FROM stock_movements WHERE id IN (${placeholders})`).run(...ids);
      audit(userId, 'delete_history', type, ids.join(','));
      return result.changes;
    })();
  },
  clockAttendance: (input: ExplicitAttendanceInput) => {
    try {
      const result = performExplicitAttendance(db, input, now(), settingsObject());
      audit(
        input.facilitatedBy,
        result.action === 'CLOCK_IN' ? 'attendance_clock_in' : 'attendance_clock_out',
        'attendance',
        String(result.attendanceId),
        'SUCCESS',
        { subjectId: result.subjectId },
      );
      return true;
    } catch (error) {
      if (error instanceof Error && error.message === 'INVALID_CREDENTIALS')
        audit(
          input.facilitatedBy || null,
          'attendance_credential_failed',
          'user',
          String(input.employeeId),
          'FAILURE',
        );
      throw error;
    }
  },
  attendanceSheet: ({
    date = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10),
    employeeId,
    role,
    state,
  }: { date?: string; employeeId?: number; role?: string; state?: string } = {}) => {
    if (
      !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
      (role && !['owner', 'manager', 'employee'].includes(role)) ||
      (state && !['PRESENT', 'COMPLETED', 'ABSENT'].includes(state))
    )
      throw new Error('VALIDATION_ERROR');
    const users = db
      .prepare(
        `SELECT u.id,u.username,u.first_name firstName,u.last_name lastName,u.role
      FROM users u LEFT JOIN employees e ON e.user_id=u.id
      WHERE u.active=1 AND COALESCE(e.status,'ACTIVE') NOT IN ('RESIGNED','ARCHIVED')
        AND (? IS NULL OR u.id=?) AND (?='' OR u.role=?) ORDER BY u.last_name,u.first_name`,
      )
      .all(employeeId ?? null, employeeId ?? null, role || '', role || '') as any[];
    const records = db
      .prepare(
        `SELECT id,employee_id employeeId,start_time startTime,end_time endTime,status,source
      FROM attendances WHERE date(start_time,'localtime')=? OR end_time IS NULL ORDER BY start_time`,
      )
      .all(date) as any[];
    return users
      .map((user) => {
        const rows = records.filter((row) => row.employeeId === user.id);
        const open = rows.find((row) => !row.endTime);
        const first = rows[0];
        const last = rows.at(-1);
        const durationMinutes = rows.reduce(
          (sum, row) =>
            row.endTime
              ? sum +
                Math.max(
                  0,
                  Math.round((Date.parse(row.endTime) - Date.parse(row.startTime)) / 60000),
                )
              : sum,
          0,
        );
        const attendanceState = open ? 'PRESENT' : rows.length ? 'COMPLETED' : 'ABSENT';
        return {
          ...user,
          attendanceId: open?.id ?? last?.id ?? null,
          startTime: first?.startTime ?? null,
          endTime: open ? null : (last?.endTime ?? null),
          durationMinutes,
          state: attendanceState,
          status: last?.status ?? null,
          source: last?.source ?? null,
        };
      })
      .filter((row) => !state || row.state === state);
  },
  attendanceHistory: ({
    from = '',
    to = '',
    employeeId,
    role = '',
    state = '',
  }: { from?: string; to?: string; employeeId?: number; role?: string; state?: string } = {}) => {
    if (
      (from && !/^\d{4}-\d{2}-\d{2}$/.test(from)) ||
      (to && !/^\d{4}-\d{2}-\d{2}$/.test(to)) ||
      (role && !['owner', 'manager', 'employee'].includes(role)) ||
      (state && !['OPEN', 'CLOSED', 'CORRECTED', 'INTERRUPTED'].includes(state))
    )
      throw new Error('VALIDATION_ERROR');
    return db
      .prepare(
        `SELECT a.id,a.employee_id employeeId,u.username,u.first_name firstName,u.last_name lastName,u.role,
      a.start_time startTime,a.end_time endTime,a.status,a.source,a.correction_reason correctionReason,a.corrected_at correctedAt,
      ROUND((julianday(a.end_time)-julianday(a.start_time))*24,2) hours
      FROM attendances a JOIN users u ON u.id=a.employee_id
      WHERE (?='' OR date(a.start_time,'localtime')>=?) AND (?='' OR date(a.start_time,'localtime')<=?)
        AND (? IS NULL OR u.id=?) AND (?='' OR u.role=?)
        AND (?='' OR CASE WHEN a.end_time IS NULL THEN 'OPEN' WHEN a.status='CORRECTED' THEN 'CORRECTED' WHEN a.status='INTERRUPTED' THEN 'INTERRUPTED' ELSE 'CLOSED' END=?)
      ORDER BY a.start_time DESC LIMIT 1000`,
      )
      .all(from, from, to, to, employeeId ?? null, employeeId ?? null, role, role, state, state);
  },
  correctAttendance: ({
    id,
    startTime,
    endTime,
    reason,
    correctedBy,
  }: {
    id: number;
    startTime: string;
    endTime: string;
    reason: string;
    correctedBy: number;
  }) =>
    db.transaction(() => {
      const next = validateAttendanceCorrection({ startTime, endTime, reason });
      const previous = db
        .prepare('SELECT start_time startTime,end_time endTime FROM attendances WHERE id=?')
        .get(id) as { startTime: string; endTime: string | null } | undefined;
      if (!previous) throw new Error('NOT_FOUND');
      db.prepare(
        `UPDATE attendances SET
          original_start_time=COALESCE(original_start_time,start_time),
          original_end_time=COALESCE(original_end_time,end_time),
          start_time=?,end_time=?,status='CORRECTED',correction_reason=?,
          corrected_by=?,corrected_at=? WHERE id=?`,
      ).run(next.startTime, next.endTime, next.reason, correctedBy, now(), id);
      audit(correctedBy, 'attendance_corrected', 'attendance', String(id), 'SUCCESS', {
        before: previous,
        after: next,
        reason: next.reason,
      });
      return true;
    })(),
  attendanceStatuses: ({ employeeId }: { employeeId?: number } = {}) =>
    db
      .prepare(
        `SELECT u.id,CASE WHEN EXISTS(SELECT 1 FROM attendances a WHERE a.employee_id=u.id AND a.end_time IS NULL) THEN 1 ELSE 0 END present
         FROM users u WHERE u.active=1 AND (? IS NULL OR u.id=?)`,
      )
      .all(employeeId ?? null, employeeId ?? null),
  adjustStock: ({
    productId,
    newQuantity,
    reason,
    userId,
  }: {
    productId: number;
    newQuantity: number;
    reason: string;
    userId: number;
  }) =>
    db.transaction(() => {
      const validated = validateStockAdjustment({ newQuantity, reason });
      const product = db
        .prepare(
          'SELECT stock_quantity quantity,price FROM products WHERE id=? AND deleted_at IS NULL',
        )
        .get(productId) as { quantity: number; price: number } | undefined;
      if (!product) throw new Error('NOT_FOUND');
      const delta = validated.newQuantity - product.quantity;
      if (!delta) return true;
      db.prepare('UPDATE products SET stock_quantity=?,updated_at=? WHERE id=?').run(
        validated.newQuantity,
        now(),
        productId,
      );
      db.prepare(
        `INSERT INTO stock_movements(product_id,quantity,reason,reference_id,unit_price)
         VALUES(?,?,?,?,?)`,
      ).run(productId, delta, `adjustment:${validated.reason}`, `ADJ-${Date.now()}`, product.price);
      audit(userId, 'stock_adjusted', 'product', String(productId), 'SUCCESS', {
        before: product.quantity,
        after: validated.newQuantity,
        reason: validated.reason,
      });
      checkStock(productId);
      return true;
    })(),
  stockMovements: ({
    from = '',
    to = '',
    productId = 0,
    category = '',
    reason = '',
    limit = 250,
  }: { from?: string; to?: string; productId?: number; category?: string; reason?: string; limit?: number } = {}) => {
    const datePattern = /^\d{4}-\d{2}-\d{2}$/;
    const reasons = [
      'sale',
      'invoice_reversal',
      'purchase',
      'purchase_cancellation',
      'inventory',
      'adjustment',
      'initial',
      'product_deletion',
    ];
    if (
      (from && !datePattern.test(from)) ||
      (to && !datePattern.test(to)) ||
      (from && to && from > to)
    )
      throw new Error('VALIDATION_ERROR');
    if (!Number.isInteger(productId) || productId < 0 || (reason && !reasons.includes(reason)))
      throw new Error('VALIDATION_ERROR');
    if (typeof category !== 'string' || category.length > 120) throw new Error('VALIDATION_ERROR');
    const boundedLimit = Math.min(500, Math.max(1, Number.isInteger(limit) ? limit : 250));
    return db
      .prepare(
        `SELECT sm.id,p.id productId,p.name product,p.category,sm.quantity,
        CASE WHEN sm.reason LIKE 'adjustment:%' THEN 'adjustment' ELSE sm.reason END reason,
        CASE WHEN sm.reason LIKE 'adjustment:%' THEN substr(sm.reason,length('adjustment:')+1) END adjustmentReason,
        sm.reference_id referenceId,sm.unit_price unitPrice,sm.created_at createdAt,
        CASE WHEN sm.reason='sale' THEN i.id
             WHEN sm.reason IN ('purchase','purchase_cancellation') THEN pu.reference
             WHEN sm.reason='inventory' THEN ic.reference
             ELSE sm.reference_id END sourceReference,
        CASE WHEN sm.reason='sale' THEN 'SALE'
             WHEN sm.reason IN ('purchase','purchase_cancellation') THEN 'PURCHASE'
             WHEN sm.reason='inventory' THEN 'INVENTORY'
             WHEN sm.reason LIKE 'adjustment:%' OR sm.reason='adjustment' THEN 'ADJUSTMENT'
             ELSE 'OTHER' END sourceType
       FROM stock_movements sm JOIN products p ON p.id=sm.product_id
       LEFT JOIN invoices i ON sm.reason='sale' AND i.id=sm.reference_id
       LEFT JOIN purchases pu ON sm.reason IN ('purchase','purchase_cancellation') AND CAST(pu.id AS TEXT)=sm.reference_id
       LEFT JOIN inventory_counts ic ON sm.reason='inventory' AND CAST(ic.id AS TEXT)=sm.reference_id
       WHERE (?='' OR date(sm.created_at)>=date(?)) AND (?='' OR date(sm.created_at)<=date(?))
         AND (?=0 OR sm.product_id=?) AND (?='' OR sm.reason=? OR sm.reason LIKE ?)
         AND (?='' OR p.category=?)
       ORDER BY sm.created_at DESC,sm.id DESC LIMIT ?`,
      )
      .all(from, from, to, to, productId, productId, reason, reason, `${reason}:%`, category, category, boundedLimit);
  },
  startInventory: ({ userId, note = '' }: { userId: number; note?: string }) =>
    db.transaction(() => {
      const reference = `INV-${new Date().toISOString().replace(/\D/g, '').slice(0, 14)}-${randomBytes(3).toString('hex').toUpperCase()}`;
      const result = db
        .prepare(
          `INSERT INTO inventory_counts(reference,status,note,created_by)
           VALUES(?,'DRAFT',?,?)`,
        )
        .run(reference, note.trim(), userId);
      const inventoryId = Number(result.lastInsertRowid);
      db.prepare(
        `INSERT INTO inventory_count_lines(
          inventory_id,product_id,expected_quantity,counted_quantity
        ) SELECT ?,id,stock_quantity,stock_quantity FROM products WHERE deleted_at IS NULL`,
      ).run(inventoryId);
      audit(userId, 'inventory_started', 'inventory', String(inventoryId));
      return { id: inventoryId, reference };
    })(),
  recordInventoryLine: ({
    inventoryId,
    productId,
    countedQuantity,
  }: {
    inventoryId: number;
    productId: number;
    countedQuantity: number;
  }) => {
    const quantity = validateInventoryQuantity(countedQuantity);
    const result = db
      .prepare(
        `UPDATE inventory_count_lines SET counted_quantity=?
       WHERE inventory_id=? AND product_id=?
         AND EXISTS(SELECT 1 FROM inventory_counts WHERE id=? AND status='DRAFT')`,
      )
      .run(quantity, inventoryId, productId, inventoryId);
    if (result.changes !== 1) throw new Error('CONFLICT');
    return true;
  },
  validateInventory: ({ inventoryId, userId }: { inventoryId: number; userId: number }) =>
    db.transaction(() => {
      const inventory = db
        .prepare('SELECT status FROM inventory_counts WHERE id=?')
        .get(inventoryId) as { status: string } | undefined;
      if (!inventory) throw new Error('NOT_FOUND');
      if (inventory.status !== 'DRAFT') throw new Error('CONFLICT');
      const lines = db
        .prepare(
          `SELECT lines.product_id productId,lines.counted_quantity countedQuantity,
            products.stock_quantity currentQuantity,products.price
           FROM inventory_count_lines lines
           JOIN products ON products.id=lines.product_id
           WHERE lines.inventory_id=?`,
        )
        .all(inventoryId) as {
        productId: number;
        countedQuantity: number;
        currentQuantity: number;
        price: number;
      }[];
      for (const line of lines) {
        const delta = line.countedQuantity - line.currentQuantity;
        if (!delta) continue;
        db.prepare('UPDATE products SET stock_quantity=?,updated_at=? WHERE id=?').run(
          line.countedQuantity,
          now(),
          line.productId,
        );
        db.prepare(
          `INSERT INTO stock_movements(product_id,quantity,reason,reference_id,unit_price)
           VALUES(?,?,'inventory',?,?)`,
        ).run(line.productId, delta, String(inventoryId), line.price);
        checkStock(line.productId);
      }
      db.prepare(
        "UPDATE inventory_counts SET status='VALIDATED',validated_by=?,validated_at=? WHERE id=?",
      ).run(userId, now(), inventoryId);
      audit(userId, 'inventory_validated', 'inventory', String(inventoryId));
      return true;
    })(),
  suppliers: () => db.prepare('SELECT * FROM suppliers WHERE active=1 ORDER BY name').all(),
  saveSupplier: ({
    id,
    name,
    phone = '',
    email = '',
    address = '',
    userId,
  }: {
    id?: number;
    name: string;
    phone?: string;
    email?: string;
    address?: string;
    userId: number;
  }) => {
    if (!name?.trim()) throw new Error('VALIDATION_ERROR');
    if (id) {
      const result = db
        .prepare(`UPDATE suppliers SET name=?,phone=?,email=?,address=?,updated_at=? WHERE id=?`)
        .run(name.trim(), phone.trim(), email.trim().toLowerCase(), address.trim(), now(), id);
      if (!result.changes) throw new Error('NOT_FOUND');
      audit(userId, 'supplier_updated', 'supplier', String(id));
      return id;
    }
    const result = db
      .prepare('INSERT INTO suppliers(name,phone,email,address) VALUES(?,?,?,?)')
      .run(name.trim(), phone.trim(), email.trim().toLowerCase(), address.trim());
    const supplierId = Number(result.lastInsertRowid);
    audit(userId, 'supplier_created', 'supplier', String(supplierId));
    return supplierId;
  },
  purchases: ({
    status = '',
    supplierId = 0,
    productId = 0,
    from = '',
    to = '',
  }: {
    status?: string;
    supplierId?: number;
    productId?: number;
    from?: string;
    to?: string;
  } = {}) => {
    const statuses = ['', 'DRAFT', 'VALIDATED', 'CANCELLED'];
    if (
      !statuses.includes(status) ||
      !Number.isInteger(supplierId) ||
      supplierId < 0 ||
      !Number.isInteger(productId) ||
      productId < 0 ||
      (from && !/^\d{4}-\d{2}-\d{2}$/.test(from)) ||
      (to && !/^\d{4}-\d{2}-\d{2}$/.test(to)) ||
      (from && to && from > to)
    )
      throw new Error('VALIDATION_ERROR');
    return db
      .prepare(
        `SELECT purchases.*,suppliers.name supplier,
        COALESCE(purchases.validated_at,purchases.cancelled_at,purchases.created_at) effective_date
       FROM purchases LEFT JOIN suppliers ON suppliers.id=purchases.supplier_id
       WHERE (?='' OR purchases.status=?) AND (?=0 OR purchases.supplier_id=?)
         AND (?=0 OR EXISTS (SELECT 1 FROM purchase_items pi WHERE pi.purchase_id=purchases.id AND pi.product_id=?))
         AND (?='' OR date(COALESCE(purchases.validated_at,purchases.cancelled_at,purchases.created_at))>=date(?))
         AND (?='' OR date(COALESCE(purchases.validated_at,purchases.cancelled_at,purchases.created_at))<=date(?))
       ORDER BY effective_date DESC,purchases.id DESC`,
      )
      .all(status, status, supplierId, supplierId, productId, productId, from, from, to, to);
  },
  purchaseDetail: (purchaseId: number) => {
    if (!Number.isInteger(purchaseId) || purchaseId <= 0) throw new Error('VALIDATION_ERROR');
    const purchase = db
      .prepare(
        `SELECT p.*,s.name supplier,creator.first_name||' '||creator.last_name createdBy,
        validator.first_name||' '||validator.last_name validatedBy,
        canceller.first_name||' '||canceller.last_name cancelledBy
       FROM purchases p LEFT JOIN suppliers s ON s.id=p.supplier_id
       JOIN users creator ON creator.id=p.created_by
       LEFT JOIN users validator ON validator.id=p.validated_by
       LEFT JOIN users canceller ON canceller.id=p.cancelled_by WHERE p.id=?`,
      )
      .get(purchaseId) as any;
    if (!purchase) throw new Error('NOT_FOUND');
    return {
      ...purchase,
      items: db
        .prepare(
          `SELECT pi.*,p.name product,p.category FROM purchase_items pi JOIN products p ON p.id=pi.product_id WHERE pi.purchase_id=? ORDER BY p.name`,
        )
        .all(purchaseId),
      movements: db
        .prepare(
          `SELECT sm.id,sm.product_id productId,p.name product,sm.quantity,sm.reason,sm.created_at createdAt FROM stock_movements sm JOIN products p ON p.id=sm.product_id WHERE sm.reason IN ('purchase','purchase_cancellation') AND sm.reference_id=? ORDER BY sm.created_at,sm.id`,
        )
        .all(String(purchaseId)),
    };
  },
  createPurchase: ({
    supplierId,
    supplierInvoice = '',
    note = '',
    idempotencyKey,
    userId,
  }: {
    supplierId?: number;
    supplierInvoice?: string;
    note?: string;
    idempotencyKey?: string;
    userId: number;
  }) =>
    db.transaction(() => {
      if (idempotencyKey) {
        const existing = db
          .prepare('SELECT id,reference,status FROM purchases WHERE idempotency_key=?')
          .get(idempotencyKey);
        if (existing) return existing;
      }
      if (
        supplierId &&
        !db.prepare('SELECT 1 FROM suppliers WHERE id=? AND active=1').get(supplierId)
      )
        throw new Error('NOT_FOUND');
      const reference = `ACH-${new Date().toISOString().replace(/\D/g, '').slice(0, 14)}-${randomBytes(3).toString('hex').toUpperCase()}`;
      const result = db
        .prepare(
          `INSERT INTO purchases(
          reference,idempotency_key,supplier_id,supplier_invoice,status,note,created_by
        ) VALUES(?,?,?,?,'DRAFT',?,?)`,
        )
        .run(
          reference,
          idempotencyKey || null,
          supplierId || null,
          supplierInvoice.trim(),
          note.trim(),
          userId,
        );
      const id = Number(result.lastInsertRowid);
      audit(userId, 'purchase_created', 'purchase', String(id));
      return { id, reference, status: 'DRAFT' };
    })(),
  savePurchaseItem: ({
    purchaseId,
    productId,
    quantity,
    unitCost,
  }: {
    purchaseId: number;
    productId: number;
    quantity: number;
    unitCost: number;
  }) =>
    db.transaction(() => {
      validatePurchaseItem({ quantity, unitCost });
      const purchase = db.prepare('SELECT status FROM purchases WHERE id=?').get(purchaseId) as
        { status: string } | undefined;
      if (!purchase) throw new Error('NOT_FOUND');
      if (!canEditPurchase(purchase.status)) throw new Error('CONFLICT');
      if (!db.prepare('SELECT 1 FROM products WHERE id=? AND deleted_at IS NULL').get(productId))
        throw new Error('NOT_FOUND');
      db.prepare(
        `INSERT INTO purchase_items(purchase_id,product_id,quantity,unit_cost,total_line)
         VALUES(?,?,?,?,?)
         ON CONFLICT(purchase_id,product_id) DO UPDATE SET
           quantity=excluded.quantity,unit_cost=excluded.unit_cost,total_line=excluded.total_line`,
      ).run(purchaseId, productId, quantity, unitCost, quantity * unitCost);
      updatePurchaseTotal(purchaseId);
      return true;
    })(),
  validatePurchase: ({ purchaseId, userId }: { purchaseId: number; userId: number }) =>
    db.transaction(() => {
      const purchase = db.prepare('SELECT status FROM purchases WHERE id=?').get(purchaseId) as
        { status: string } | undefined;
      if (!purchase) throw new Error('NOT_FOUND');
      if (purchase.status === 'VALIDATED') return true;
      if (!canEditPurchase(purchase.status)) throw new Error('CONFLICT');
      const items = db
        .prepare('SELECT * FROM purchase_items WHERE purchase_id=?')
        .all(purchaseId) as any[];
      if (!items.length) throw new Error('VALIDATION_ERROR');
      for (const item of items) {
        db.prepare(
          'UPDATE products SET stock_quantity=stock_quantity+?,updated_at=? WHERE id=?',
        ).run(item.quantity, now(), item.product_id);
        db.prepare(
          `INSERT INTO stock_movements(product_id,quantity,reason,reference_id,unit_price)
           VALUES(?,?,'purchase',?,?)`,
        ).run(item.product_id, item.quantity, String(purchaseId), item.unit_cost);
        checkStock(item.product_id);
      }
      db.prepare(
        "UPDATE purchases SET status='VALIDATED',validated_by=?,validated_at=? WHERE id=? AND status='DRAFT'",
      ).run(userId, now(), purchaseId);
      audit(userId, 'purchase_validated', 'purchase', String(purchaseId));
      return true;
    })(),
  cancelPurchase: ({
    purchaseId,
    reason,
    userId,
  }: {
    purchaseId: number;
    reason: string;
    userId: number;
  }) =>
    db.transaction(() => {
      if (reason.trim().length < 3) throw new Error('VALIDATION_ERROR');
      const purchase = db.prepare('SELECT status FROM purchases WHERE id=?').get(purchaseId) as
        { status: string } | undefined;
      if (!purchase) throw new Error('NOT_FOUND');
      if (purchase.status === 'CANCELLED') return true;
      if (purchase.status === 'VALIDATED') {
        const items = db
          .prepare('SELECT * FROM purchase_items WHERE purchase_id=?')
          .all(purchaseId) as any[];
        for (const item of items) {
          const result = db
            .prepare(
              `UPDATE products SET stock_quantity=stock_quantity-?,updated_at=?
             WHERE id=? AND stock_quantity>=?`,
            )
            .run(item.quantity, now(), item.product_id, item.quantity);
          if (result.changes !== 1) throw new Error('INSUFFICIENT_STOCK');
          db.prepare(
            `INSERT INTO stock_movements(product_id,quantity,reason,reference_id,unit_price)
             VALUES(?,?,'purchase_cancellation',?,?)`,
          ).run(item.product_id, -item.quantity, String(purchaseId), item.unit_cost);
          checkStock(item.product_id);
        }
      }
      db.prepare(
        `UPDATE purchases SET status='CANCELLED',cancelled_by=?,cancelled_at=?,cancellation_reason=?
         WHERE id=?`,
      ).run(userId, now(), reason.trim(), purchaseId);
      audit(userId, 'purchase_cancelled', 'purchase', String(purchaseId), 'SUCCESS', {
        previousStatus: purchase.status,
        reason: reason.trim(),
      });
      return true;
    })(),
  messages: ({ userId, role }: { userId: number; role: string }) =>
    db
      .prepare(
        `SELECT m.*,
          sender.first_name||' '||sender.last_name sender,
          recipient.first_name||' '||recipient.last_name recipient,
          CASE WHEN m.sender_id=? THEN 1 ELSE 0 END sent,
          CASE WHEN m.sender_id=? OR EXISTS(SELECT 1 FROM message_reads mr WHERE mr.message_id=m.id AND mr.user_id=?) THEN 1 ELSE 0 END is_read
        FROM messages m
        LEFT JOIN users sender ON sender.id=m.sender_id
        LEFT JOIN users recipient ON recipient.id=m.recipient_id
        WHERE (
          m.sender_id=?
          OR m.recipient_type='all'
          OR m.recipient_id=?
          OR (?<>'employee' AND m.recipient_type='admin')
        ) AND NOT EXISTS(
          SELECT 1 FROM message_deletions md WHERE md.message_id=m.id AND md.user_id=?
        )
        ORDER BY m.created_at DESC`,
      )
      .all(userId, userId, userId, userId, userId, role, userId),
  messageRecipients: () =>
    db
      .prepare(
        `SELECT id,username,role,first_name firstName,last_name lastName,initials
       FROM users WHERE active=1 ORDER BY first_name,last_name`,
      )
      .all(),
  sendMessage: ({
    senderId,
    recipientType,
    recipientId,
    subject,
    content,
    requestId,
  }: {
    senderId: number;
    recipientType: string;
    recipientId?: number;
    subject: string;
    content: string;
    requestId?: string;
  }) => {
    if (
      typeof subject !== 'string' ||
      typeof content !== 'string' ||
      !subject.trim() ||
      !content.trim() ||
      subject.length > 200 ||
      content.length > 10_000
    )
      throw new Error('INVALID_MESSAGE');
    const request = requestId?.trim() || null;
    if (request && !/^[A-Za-z0-9_-]{8,128}$/.test(request)) throw new Error('INVALID_MESSAGE');
    if (request) {
      const existing = db.prepare('SELECT id FROM messages WHERE request_id=?').get(request) as
        { id: number } | undefined;
      if (existing) return existing.id;
    }
    if (!['all', 'user'].includes(recipientType)) throw new Error('INVALID_RECIPIENT');
    if (recipientType === 'user') {
      const recipient = db.prepare('SELECT 1 FROM users WHERE id=? AND active=1').get(recipientId);
      if (!recipient) throw new Error('INVALID_RECIPIENT');
    }
    return db
      .prepare(
        `INSERT INTO messages(
          sender_id,recipient_type,recipient_id,subject,content,request_id
        ) VALUES(?,?,?,?,?,?)`,
      )
      .run(
        senderId,
        recipientType,
        recipientType === 'user' ? recipientId : null,
        subject.trim(),
        content.trim(),
        request,
      ).lastInsertRowid;
  },
  markMessage: ({ id, userId, isRead }: { id: number; userId: number; isRead: boolean }) => {
    if (isRead)
      return db
        .prepare('INSERT OR IGNORE INTO message_reads(message_id,user_id) VALUES(?,?)')
        .run(id, userId);
    return db.prepare('DELETE FROM message_reads WHERE message_id=? AND user_id=?').run(id, userId);
  },
  deleteMessage: ({ id, userId }: { id: number; userId: number }) =>
    db
      .prepare('INSERT OR IGNORE INTO message_deletions(message_id,user_id) VALUES(?,?)')
      .run(id, userId),
  notifications: () =>
    db
      .prepare(
        `SELECT n.*,p.name productName,p.stock_quantity currentStock,p.min_stock_threshold threshold FROM notifications n LEFT JOIN products p ON p.id=n.product_id ORDER BY n.created_at DESC`,
      )
      .all(),
  deleteNotifications: (ids: number[]) => {
    if (!ids.length) return 0;
    return db
      .prepare(`DELETE FROM notifications WHERE id IN (${ids.map(() => '?').join(',')})`)
      .run(...ids).changes;
  },
  emailReportLogs: () =>
    db
      .prepare(
        `SELECT id,recipient,subject,filename,status,attempts,next_attempt_at,last_error,sent_at,created_at
       FROM email_report_logs ORDER BY created_at DESC`,
      )
      .all(),
  retryEmailQueue: async () => {
    db.prepare(
      "UPDATE email_report_logs SET status='pending',attempts=0,next_attempt_at=NULL,last_error=NULL WHERE status IN ('pending','failed')",
    ).run();
    return processEmailQueue(10);
  },
  deleteEmailReportLogs: (ids: number[]) => {
    if (!ids.length) return 0;
    return db
      .prepare(`DELETE FROM email_report_logs WHERE id IN (${ids.map(() => '?').join(',')})`)
      .run(...ids).changes;
  },
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
        .prepare(
          "SELECT COUNT(*) n FROM invoices WHERE status='validated' AND date(invoice_date)=date('now','localtime')",
        )
        .get() as any
    ).n,
    revenueToday: (
      db
        .prepare(
          "SELECT COALESCE(SUM(total_amount),0) n FROM invoices WHERE status='validated' AND date(invoice_date)=date('now','localtime')",
        )
        .get() as any
    ).n,
    revenueMonth: (
      db
        .prepare(
          "SELECT COALESCE(SUM(total_amount),0) n FROM invoices WHERE status='validated' AND strftime('%Y-%m',invoice_date)=strftime('%Y-%m','now','localtime')",
        )
        .get() as any
    ).n,
    revenuePreviousMonth: (
      db
        .prepare(
          `SELECT COALESCE(SUM(total_amount),0) n FROM invoices
           WHERE status='validated'
             AND invoice_date>=datetime('now','localtime','start of month','-1 month')
             AND invoice_date<datetime(
               'now','localtime','start of month','-1 month',
               printf('+%d days',MIN(
                 CAST(strftime('%d','now','localtime') AS INTEGER),
                 CAST(strftime('%d',date('now','localtime','start of month','-1 day')) AS INTEGER)
               ))
             )`,
        )
        .get() as any
    ).n,
    salesChart: db
      .prepare(
        "SELECT date(invoice_date) label,SUM(total_amount) value FROM invoices WHERE status='validated' AND invoice_date>=datetime('now','-30 day') GROUP BY date(invoice_date) ORDER BY label",
      )
      .all(),
    attendanceChart: db
      .prepare(
        "SELECT u.first_name||' '||u.last_name label,ROUND(SUM((julianday(a.end_time)-julianday(a.start_time))*24),2) value FROM attendances a JOIN users u ON u.id=a.employee_id WHERE a.end_time IS NOT NULL GROUP BY a.employee_id ORDER BY value DESC",
      )
      .all(),
  }),
  reports: (
    input: {
      from?: string;
      to?: string;
      grain?: 'day' | 'week' | 'month';
      productId?: number;
      category?: string;
    } = {},
  ) => reportingData(input),
  settings: () => {
    const values = Object.fromEntries(
      (db.prepare('SELECT key,value FROM settings').all() as any[]).map((x) => [x.key, x.value]),
    ) as Record<string, string>;
    delete values.smtpPassword;
    delete values.smtpPasswordEncrypted;
    values.smtpPasswordConfigured = smtpPassword() ? 'true' : 'false';
    return values;
  },
  preferences: () => {
    const values = settingsObject();
    return {
      currency: values.currency || 'EUR',
      discountsEnabled: values.discountsEnabled !== 'false' ? 'true' : 'false',
    };
  },
  saveSettings: (values: Record<string, string>) =>
    db.transaction(() => {
      validateSettings(values);
      if (values.smtpPort !== undefined) {
        const port = Number(values.smtpPort);
        if (!Number.isInteger(port) || port < 1 || port > 65_535)
          throw new Error('VALIDATION_ERROR');
      }
      const stmt = db.prepare(
        'INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value',
      );
      for (const [k, v] of Object.entries(values)) {
        if (k === 'smtpPassword') {
          if (v) storeSmtpPassword(v);
          continue;
        }
        if (k === 'smtpPasswordEncrypted' || k === 'smtpPasswordConfigured') continue;
        stmt.run(k, String(v).slice(0, 10_000));
      }
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
  backups: () =>
    fs
      .readdirSync(backupDir())
      .filter((name) => name.endsWith('.store-backup') || name.endsWith('.db'))
      .map((name) => {
        const filePath = path.join(backupDir(), name);
        const stat = fs.statSync(filePath);
        return { name, path: filePath, size: stat.size, modifiedAt: stat.mtime.toISOString() };
      })
      .sort((a, b) => b.modifiedAt.localeCompare(a.modifiedAt)),
  systemDiagnostics: () => {
    const databaseStat = fs.statSync(databaseFile);
    const backups = fs
      .readdirSync(backupDir())
      .filter((name) => name.endsWith('.store-backup') || name.endsWith('.db'));
    const integrity = db.pragma('quick_check') as { quick_check: string }[];
    const pendingEmails = (
      db
        .prepare(
          "SELECT COUNT(*) count FROM email_report_logs WHERE status IN ('pending','sending','failed')",
        )
        .get() as {
        count: number;
      }
    ).count;
    const lastHeartbeat = db
      .prepare("SELECT value FROM settings WHERE key='lastHeartbeat'")
      .get() as { value: string } | undefined;
    let availableDiskBytes: number | null = null;
    try {
      const disk = fs.statfsSync(path.dirname(databaseFile));
      availableDiskBytes = disk.bavail * disk.bsize;
    } catch {
      /* unavailable on some older systems */
    }
    return {
      appVersion: app.getVersion(),
      schemaVersion: latestSchemaVersion,
      dataDirectory: app.getPath('userData'),
      databasePath: databaseFile,
      databaseSize: databaseStat.size,
      availableDiskBytes,
      storageHealth: storageHealth(availableDiskBytes),
      backupCount: backups.length,
      pendingEmails,
      lastHeartbeat: lastHeartbeat?.value || null,
      integrity: integrity[0]?.quick_check === 'ok' ? 'ok' : 'error',
    };
  },
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
  importProductsPdf: async (filePath: string) => {
    if (!/\.pdf$/i.test(filePath)) throw new Error('INVALID_PDF');
    const stat = fs.statSync(filePath);
    if (stat.size > 25_000_000) throw new Error('PDF_TOO_LARGE');
    let payload: any;
    try {
      payload = await readStoreData(fs.readFileSync(filePath));
    } catch {
      throw new Error('INVALID_STORE_PDF');
    }
    if (
      payload?.kind !== 'stocks' ||
      payload?.version !== 1 ||
      !Array.isArray(payload.products) ||
      payload.products.length > 10_000
    )
      throw new Error('INVALID_STORE_PDF');
    return db.transaction(() =>
      payload.products.reduce((count: number, item: unknown) => {
        if (!item || typeof item !== 'object') throw new Error('INVALID_STORE_PDF');
        const source = item as Record<string, unknown>;
        const input = {
          name: String(source.name || ''),
          hashtag: String(source.hashtag || ''),
          category: String(source.category || ''),
          description: String(source.description || ''),
          price: Number(source.price),
          stockQuantity: Number(source.stockQuantity),
          minStockThreshold: Number(source.minStockThreshold),
        };
        validateProduct(input);
        const existing = db
          .prepare(
            `SELECT id FROM products WHERE deleted_at IS NULL
             AND (lower(name)=lower(?) OR (?<>'' AND lower(hashtag)=lower(?))) LIMIT 1`,
          )
          .get(input.name, input.hashtag, input.hashtag) as { id: number } | undefined;
        api.saveProduct({ ...input, id: existing?.id });
        return count + 1;
      }, 0),
    )();
  },
  restoreBackup: async (filePath: string) => {
    if (destructiveOperation) throw new Error('CONFLICT');
    if (!/\.(store-backup|db|sqlite)$/i.test(filePath)) throw new Error('INVALID_BACKUP');
    const restoreRoot = path.join(app.getPath('userData'), 'restore-staging', randomUUID());
    fs.mkdirSync(restoreRoot, { recursive: true });
    destructiveOperation = true;
    let stagedDatabase: string;
    let stagedMedia: string | null = null;
    let requiredMedia: string[] = [];
    try {
      if (/\.store-backup$/i.test(filePath)) {
        const staged = inspectAndStageBackupBundle(filePath, restoreRoot);
        stagedDatabase = staged.databasePath;
        stagedMedia = staged.mediaPath;
        requiredMedia = mediaReferencesFromManifest(staged.manifest);
      } else {
        stagedDatabase = path.join(restoreRoot, 'store.sqlite');
        fs.copyFileSync(filePath, stagedDatabase);
      }
      const candidate = new Database(stagedDatabase, { readonly: true, fileMustExist: true });
      try {
        validateBackupDatabase(candidate);
        const columns = candidate.pragma('table_info(products)') as { name: string }[];
        if (columns.some(({ name }) => name === 'image_ref')) {
          const references = (
            candidate
              .prepare(
                'SELECT DISTINCT image_ref reference FROM products WHERE image_ref IS NOT NULL',
              )
              .all() as { reference: string }[]
          ).map(({ reference }) => reference);
          if (references.some((reference) => !requiredMedia.includes(reference)))
            throw new Error('INVALID_BACKUP');
        }
        if ((candidate.pragma('foreign_key_check') as unknown[]).length)
          throw new Error('INVALID_BACKUP');
      } finally {
        candidate.close();
      }
      await createBackup('pre-restore');
      const rollbackRoot = path.join(restoreRoot, 'rollback');
      fs.mkdirSync(rollbackRoot, { recursive: true });
      await db.backup(path.join(rollbackRoot, 'store.sqlite'));
      if (fs.existsSync(articleMediaDir()))
        fs.cpSync(articleMediaDir(), path.join(rollbackRoot, 'media'), { recursive: true });
      markPendingRestore(app.getPath('userData'), path.basename(restoreRoot));
      db.close();
      removeDatabaseSidecars();
      fs.copyFileSync(stagedDatabase, databaseFile);
      fs.rmSync(articleMediaDir(), { recursive: true, force: true });
      if (stagedMedia && fs.existsSync(stagedMedia))
        fs.cpSync(stagedMedia, articleMediaDir(), { recursive: true });
      else fs.mkdirSync(articleMediaDir(), { recursive: true });
      db = new Database(databaseFile);
      configureDatabase();
      applyDatabaseMigrations(db);
      db.exec(schema);
      validateBackupDatabase(db);
      if ((db.pragma('foreign_key_check') as unknown[]).length) throw new Error('INVALID_BACKUP');
      finishPendingRestore(app.getPath('userData'));
    } catch (error) {
      if (hasPendingRestore(app.getPath('userData'))) {
        if (db?.open) db.close();
        recoverPendingRestore(app.getPath('userData'));
        db = new Database(databaseFile);
        configureDatabase();
      }
      throw error;
    } finally {
      destructiveOperation = false;
      if (!hasPendingRestore(app.getPath('userData'))) {
        try { fs.rmSync(restoreRoot, { recursive: true, force: true }); } catch { /* inert staging cleanup may be retried later */ }
      }
    }
    return true;
  },
  reset: async ({ adminId, password }: { adminId: number; password: string }) => {
    if (destructiveOperation) throw new Error('CONFLICT');
    const owner = db
      .prepare("SELECT password_hash hash FROM users WHERE id=? AND role='owner'")
      .get(adminId) as { hash: string } | undefined;
    if (!owner || !bcrypt.compareSync(password, owner.hash)) throw new Error('OWNER_REQUIRED');
    destructiveOperation = true;
    try {
    await createBackup('pre-reset');
    resetWithMedia(db, app.getPath('userData'), () => {
      for (const table of [
        'message_reads',
        'message_deletions',
        'payments',
        'invoice_lines',
        'invoices',
        'cash_sessions',
        'attendances',
        'messages',
        'notifications',
        'purchase_items',
        'purchases',
        'suppliers',
        'inventory_count_lines',
        'inventory_counts',
        'stock_movements',
        'product_price_history',
        'email_report_logs',
        'products',
        'audit_logs',
        'settings',
        'employees',
        'users',
      ])
        db.prepare(`DELETE FROM ${table}`).run();
    });
    return true;
    } finally { destructiveOperation = false; }
  },
};

export async function sendReportEmail(input: {
  to: string;
  subject: string;
  text: string;
  filename: string;
  pdf: Buffer;
}) {
  const id = enqueueEmail({ ...input, attachment: input.pdf });
  await deliverQueuedEmail(id);
  return db
    .prepare(
      'SELECT id,status,attempts,next_attempt_at nextAttemptAt FROM email_report_logs WHERE id=?',
    )
    .get(id);
}

function enqueueEmail(input: {
  to: string;
  subject: string;
  text: string;
  filename?: string;
  attachment?: Buffer;
}) {
  if (!input.to.trim() || !input.subject.trim()) throw new Error('INVALID_RECIPIENT');
  const result = db
    .prepare(
      `INSERT INTO email_report_logs(
      recipient,subject,filename,message_text,attachment,status
    ) VALUES(?,?,?,?,?,'pending')`,
    )
    .run(
      input.to.trim(),
      input.subject.trim(),
      input.filename?.trim() || '',
      input.text.slice(0, 100_000),
      input.attachment ?? null,
    );
  return Number(result.lastInsertRowid);
}

async function deliverQueuedEmail(id: number) {
  const claimed = db
    .prepare(
      `UPDATE email_report_logs SET status='sending'
     WHERE id=? AND status IN ('pending','failed')`,
    )
    .run(id);
  if (claimed.changes !== 1) return false;
  const item = db
    .prepare(
      `SELECT id,recipient,subject,filename,message_text messageText,attachment,attempts
     FROM email_report_logs WHERE id=?`,
    )
    .get(id) as
    | {
        id: number;
        recipient: string;
        subject: string;
        filename: string;
        messageText: string;
        attachment: Buffer | null;
        attempts: number;
      }
    | undefined;
  if (!item) return false;
  try {
    const attachments = item.attachment
      ? [{ filename: item.filename, content: item.attachment, contentType: 'application/pdf' }]
      : [];
    await sendEmail(
      smtpConfig(settingsObject()),
      item.recipient,
      item.subject,
      item.messageText,
      attachments,
    );
    db.prepare(
      "UPDATE email_report_logs SET status='sent',sent_at=?,last_error=NULL,next_attempt_at=NULL WHERE id=?",
    ).run(now(), id);
    return true;
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : 'SMTP_FAILED';
    const retry = nextEmailRetry(item.attempts, errorMessage);
    const message = errorMessage.slice(0, 500);
    db.prepare(
      `UPDATE email_report_logs SET status=?,attempts=?,next_attempt_at=?,last_error=? WHERE id=?`,
    ).run(retry.status, retry.attempts, retry.nextAttemptAt, message, id);
    return false;
  }
}

export async function processEmailQueue(limit = 3) {
  if (!db?.open) return { processed: 0, sent: 0 };
  const items = db
    .prepare(
      `SELECT id FROM email_report_logs
     WHERE status='pending' AND (next_attempt_at IS NULL OR next_attempt_at<=?)
     ORDER BY created_at LIMIT ?`,
    )
    .all(now(), Math.max(1, Math.min(20, limit))) as { id: number }[];
  let sent = 0;
  for (const item of items) if (await deliverQueuedEmail(item.id)) sent++;
  return { processed: items.length, sent };
}

function publicUser(id: number) {
  return publicIdentity(db.prepare(`SELECT id,username,email,role,first_name,last_name,
    initials,phone,hire_date,active,photo FROM users WHERE id=?`).get(id) as Record<string, unknown>);
}
function enforceSingleAdmin(id: number, role: string) {
  if (role === 'owner')
    db.prepare("UPDATE users SET role='manager' WHERE role='owner' AND id<>?").run(id);
}
function temporaryPassword() {
  return `Store-${randomBytes(9).toString('base64url')}!`;
}
function invoiceId() {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return `FACT-${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
}
function invoiceDetail(id: string) {
  const currentSettings = settingsObject();
  const invoice = db
    .prepare(
      `SELECT i.*,u.first_name||' '||u.last_name seller,u.initials FROM invoices i JOIN users u ON u.id=i.employee_id WHERE i.id=?`,
    )
    .get(id) as any;
  if (invoice) {
    invoice.store_name ||= currentSettings.storeName || 'STORE';
    invoice.store_address ??= currentSettings.address || '';
    invoice.store_phone ??= currentSettings.phone || '';
    invoice.store_email ??= currentSettings.email || '';
    invoice.currency ||= currentSettings.currency || 'EUR';
  }
  return {
    invoice,
    lines: db.prepare('SELECT * FROM invoice_lines WHERE invoice_id=?').all(id),
    payment: db.prepare('SELECT * FROM payments WHERE invoice_id=?').get(id),
  };
}
function updatePurchaseTotal(purchaseId: number) {
  db.prepare(
    `UPDATE purchases SET total_amount=(
      SELECT COALESCE(SUM(total_line),0) FROM purchase_items WHERE purchase_id=?
    ) WHERE id=?`,
  ).run(purchaseId, purchaseId);
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
      const owner = db
        .prepare("SELECT email FROM users WHERE role='owner' AND active=1 LIMIT 1")
        .get() as { email: string | null } | undefined;
      if (owner?.email)
        enqueueEmail({
          to: owner.email,
          subject: `Alerte stock: ${p.name}`,
          text: `${p.name}: stock actuel ${p.stock_quantity}, seuil ${p.min_stock_threshold}`,
        });
    }
  } else
    db.prepare(
      'UPDATE notifications SET resolved_at=? WHERE product_id=? AND resolved_at IS NULL',
    ).run(now(), id);
}
function audit(
  userId: number | null,
  action: string,
  entity: string,
  entityId: string,
  outcome = 'SUCCESS',
  details?: unknown,
) {
  writeAudit(db, userId, action, entity, entityId, outcome, details);
}
function configureDatabase() {
  db.function('identity_fold', { deterministic: true }, foldIdentity);
  db.pragma('journal_mode = WAL');
  db.pragma('synchronous = FULL');
  db.pragma('busy_timeout = 5000');
  db.pragma('foreign_keys = ON');
}
function findAuthenticationUser(identifier: string, role?: string): any {
  const identity = foldIdentity(identifier);
  if (!identity) return undefined;
  const rows = db.prepare(`SELECT users.*,employees.status employment_status
    FROM users LEFT JOIN employees ON employees.user_id=users.id
    WHERE (identity_fold(users.username)=? OR identity_fold(users.email)=?
      OR identity_fold(users.first_name||' '||users.last_name)=?
      OR identity_fold(users.last_name||' '||users.first_name)=?)
      AND (?='' OR (?='manager' AND users.role IN ('owner','manager')) OR users.role=?) LIMIT 2`)
    .all(identity, identity, identity, identity, role ?? '', role ?? '', role ?? '');
  return rows.length === 1 ? rows[0] : undefined;
}
function articleMediaDir() {
  return path.join(app.getPath('userData'), 'media', 'articles');
}
function removeUnreferencedArticleImage(reference: string) {
  assertArticleMediaRef(reference);
  const used = db.prepare('SELECT 1 FROM products WHERE image_ref=? LIMIT 1').get(reference);
  if (!used) fs.rmSync(managedArticlePath(articleMediaDir(), reference), { force: true });
}
function cleanupArticleMedia(cleanup: () => void, phase: string) {
  try { cleanup(); }
  catch {
    // Do not log raw filesystem errors (absolute paths). Unreferenced managed
    // copies are recoverable orphans, never a reason to undo committed media.
    logTechnical('article-media-cleanup-deferred', undefined, { phase });
  }
}
function assertAdministrativePasswordTarget(id: number, actorId: number, actorRole: string) {
  const target = db.prepare('SELECT role,active FROM users WHERE id=?').get(id) as
    { role: string; active: number } | undefined;
  assertPasswordTargetPolicy(target, { id: actorId, role: actorRole });
}
function backupDir() {
  const d = path.join(app.getPath('userData'), 'backups');
  fs.mkdirSync(d, { recursive: true });
  return d;
}
function migrationBackupDir() {
  const directory = path.join(backupDir(), 'before-migrations');
  fs.mkdirSync(directory, { recursive: true });
  return directory;
}
async function createMigrationBackup() {
  const columns = db.pragma('table_info(products)') as { name: string }[];
  if (columns.some(({ name }) => name === 'image_ref'))
    return createBackup('manual', migrationBackupDir(), 'before-migration');
  const target = path.join(
    migrationBackupDir(),
    `before-migration-${new Date().toISOString().replace(/[:.]/g, '-')}.db`,
  );
  const temporary = `${target}.tmp`;
  await db.backup(temporary);
  const check = new Database(temporary, { readonly: true, fileMustExist: true });
  try {
    const result = check.pragma('quick_check') as { quick_check: string }[];
    if (result[0]?.quick_check !== 'ok') throw new Error('BACKUP_FAILED');
  } finally {
    check.close();
  }
  fs.renameSync(temporary, target);
  return target;
}
type BackupKind = 'manual' | 'auto' | 'pre-restore' | 'pre-reset';
async function createBackup(
  kind: BackupKind = 'manual',
  directory = backupDir(),
  prefix: string = kind,
) {
  fs.mkdirSync(directory, { recursive: true });
  const name = `${prefix}-store-${new Date().toISOString().replace(/[:.]/g, '-')}-${randomBytes(3).toString('hex')}.store-backup`;
  const target = path.join(directory, name);
  const snapshot = `${target}.sqlite.tmp`;
  await db.backup(snapshot);
  const check = new Database(snapshot, { readonly: true });
  let references: string[];
  try {
    validateBackupDatabase(check);
    references = (
      check
        .prepare('SELECT DISTINCT image_ref reference FROM products WHERE image_ref IS NOT NULL')
        .all() as { reference: string }[]
    ).map(({ reference }) => reference);
  } finally {
    check.close();
  }
  try {
    return createBackupBundle({
      databaseSnapshot: snapshot,
      mediaRoot: articleMediaDir(),
      imageReferences: references,
      target,
      applicationVersion: app.getVersion(),
      kind,
    });
  } catch {
    fs.rmSync(target, { force: true });
    fs.rmSync(`${target}.tmp`, { force: true });
    throw new Error('BACKUP_FAILED');
  } finally {
    fs.rmSync(snapshot, { force: true });
  }
}
export async function ensureDailyBackup() {
  if (!db?.open) return null;
  // A first-run database has no owner yet and cannot be a viable restore source.
  // Keep setup reachable; start automatic backups once initialization is complete.
  if (!db.prepare("SELECT 1 FROM users WHERE role='owner' AND active=1").get()) return null;
  const dir = backupDir();
  let files = fs
    .readdirSync(dir)
    .filter(
      (f) => f.startsWith('auto-store-') && (f.endsWith('.store-backup') || f.endsWith('.db')),
    )
    .sort()
    .reverse();
  const today = new Date().toISOString().slice(0, 10);
  let created: string | null = null;
  if (!files.some((f) => f.includes(today))) created = await createBackup('auto');
  files = fs
    .readdirSync(dir)
    .filter(
      (f) => f.startsWith('auto-store-') && (f.endsWith('.store-backup') || f.endsWith('.db')),
    )
    .sort()
    .reverse();
  for (const f of files.slice(7)) fs.rmSync(path.join(dir, f));
  for (const f of fs.readdirSync(dir).filter((name) => name.endsWith('.tmp'))) {
    const temporary = path.join(dir, f);
    const age = Date.now() - fs.statSync(temporary).mtimeMs;
    if (age > 24 * 60 * 60 * 1000) fs.rmSync(temporary);
  }
  return created;
}
function removeDatabaseSidecars() {
  for (const suffix of ['-wal', '-shm']) {
    const sidecar = `${databaseFile}${suffix}`;
    if (fs.existsSync(sidecar)) fs.rmSync(sidecar);
  }
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
    password: smtpPassword(),
    from: settings.smtpFrom || settings.smtpUser || '',
  };
}

function storeSmtpPassword(password: string) {
  if (safeStorage.isEncryptionAvailable()) {
    const encrypted = safeStorage.encryptString(password).toString('base64');
    db.prepare(
      "INSERT INTO settings(key,value) VALUES('smtpPasswordEncrypted',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
    ).run(encrypted);
    db.prepare("DELETE FROM settings WHERE key='smtpPassword'").run();
  } else {
    // Fonctionnement hors ligne garanti sur les systèmes sans coffre-fort disponible.
    db.prepare(
      "INSERT INTO settings(key,value) VALUES('smtpPassword',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
    ).run(password);
  }
}
function smtpPassword() {
  const encrypted = db
    .prepare("SELECT value FROM settings WHERE key='smtpPasswordEncrypted'")
    .get() as { value: string } | undefined;
  if (encrypted && safeStorage.isEncryptionAvailable()) {
    try {
      return safeStorage.decryptString(Buffer.from(encrypted.value, 'base64'));
    } catch {
      return '';
    }
  }
  return (
    (
      db.prepare("SELECT value FROM settings WHERE key='smtpPassword'").get() as
        { value: string } | undefined
    )?.value || ''
  );
}
function migrateSmtpSecret() {
  const plain = db.prepare("SELECT value FROM settings WHERE key='smtpPassword'").get() as
    { value: string } | undefined;
  if (plain?.value && safeStorage.isEncryptionAvailable()) storeSmtpPassword(plain.value);
}

function reportingData({
  from = '',
  to = '',
  grain = 'day',
  productId = 0,
  category = '',
}: {
  from?: string;
  to?: string;
  grain?: 'day' | 'week' | 'month';
  productId?: number;
  category?: string;
}) {
  const bucket = (field: string) =>
    grain === 'month'
      ? `strftime('%Y-%m',${field})`
      : grain === 'week'
        ? `strftime('%Y-W%W',${field})`
        : `date(${field})`;
  const movementArgs = [from, from, to, to, productId, productId, category, category];
  const salesArgs = [from, from, to, to, productId, productId, category, category];
  const priceArgs = [from, from, to, to, productId, productId, category, category];

  const movements = db
    .prepare(
      `SELECT ${bucket('sm.created_at')} period,p.id productId,p.name product,p.category,sm.reason,
        SUM(CASE WHEN sm.quantity>0 THEN sm.quantity ELSE 0 END) entries,
        SUM(CASE WHEN sm.quantity<0 THEN ABS(sm.quantity) ELSE 0 END) exits,
        ROUND(AVG(COALESCE(NULLIF(sm.unit_price,0),p.price)),2) unitPrice,
        ROUND(SUM(ABS(sm.quantity)*COALESCE(NULLIF(sm.unit_price,0),p.price)),2) totalValue,
        ROUND(SUM(CASE WHEN sm.quantity>0 THEN sm.quantity*COALESCE(NULLIF(sm.unit_price,0),p.price) ELSE 0 END),2) entryValue,
        ROUND(SUM(CASE WHEN sm.quantity<0 THEN ABS(sm.quantity)*COALESCE(NULLIF(sm.unit_price,0),p.price) ELSE 0 END),2) exitValue
      FROM stock_movements sm JOIN products p ON p.id=sm.product_id
      WHERE (?='' OR date(sm.created_at)>=date(?)) AND (?='' OR date(sm.created_at)<=date(?))
        AND (?=0 OR p.id=?) AND (?='' OR p.category=?)
      GROUP BY period,p.id,p.name,p.category,sm.reason
      ORDER BY period DESC,p.name,sm.reason`,
    )
    .all(...movementArgs);

  const topProducts = db
    .prepare(
      `SELECT ${bucket('i.invoice_date')} period,p.id productId,il.product_name product,il.category,
        SUM(il.quantity) quantity,
        ROUND(SUM(CASE WHEN i.subtotal>0 THEN il.total_line*i.total_amount/i.subtotal ELSE 0 END),2) revenue
      FROM invoice_lines il JOIN invoices i ON i.id=il.invoice_id
      LEFT JOIN products p ON p.id=il.product_id
      WHERE i.status='validated' AND (?='' OR date(i.invoice_date)>=date(?)) AND (?='' OR date(i.invoice_date)<=date(?))
        AND (?=0 OR p.id=?) AND (?='' OR il.category=?)
      GROUP BY period,p.id,il.product_name,il.category
      ORDER BY period DESC,quantity DESC,revenue DESC LIMIT 200`,
    )
    .all(...salesArgs);

  const topCategories = db
    .prepare(
      `SELECT ${bucket('i.invoice_date')} period,il.category,
        SUM(il.quantity) quantity,
        ROUND(SUM(CASE WHEN i.subtotal>0 THEN il.total_line*i.total_amount/i.subtotal ELSE 0 END),2) revenue
      FROM invoice_lines il JOIN invoices i ON i.id=il.invoice_id
      LEFT JOIN products p ON p.id=il.product_id
      WHERE i.status='validated' AND (?='' OR date(i.invoice_date)>=date(?)) AND (?='' OR date(i.invoice_date)<=date(?))
        AND (?=0 OR p.id=?) AND (?='' OR il.category=?)
      GROUP BY period,il.category ORDER BY period DESC,quantity DESC,revenue DESC`,
    )
    .all(...salesArgs);

  const priceEvolution = db
    .prepare(
      `WITH price_points AS (
        SELECT history.product_id,history.price,history.recorded_at
        FROM product_price_history history
        UNION ALL
        SELECT il.product_id,il.unit_price,i.invoice_date
        FROM invoice_lines il JOIN invoices i ON i.id=il.invoice_id
        WHERE il.product_id IS NOT NULL AND i.status='validated'
      )
      SELECT ${bucket('points.recorded_at')} period,p.id productId,p.name product,p.category,
        ROUND(AVG(points.price),2) averagePrice,
        ROUND(MIN(points.price),2) minimumPrice,
        ROUND(MAX(points.price),2) maximumPrice,
        COUNT(*) observations
      FROM price_points points JOIN products p ON p.id=points.product_id
      WHERE (?='' OR date(points.recorded_at)>=date(?)) AND (?='' OR date(points.recorded_at)<=date(?))
        AND (?=0 OR p.id=?) AND (?='' OR p.category=?)
      GROUP BY period,p.id,p.name,p.category
      ORDER BY period,p.name`,
    )
    .all(...priceArgs);

  const summary = db
    .prepare(
      `SELECT
        COALESCE(SUM(CASE WHEN sm.quantity>0 THEN sm.quantity ELSE 0 END),0) entries,
        COALESCE(SUM(CASE WHEN sm.quantity<0 THEN ABS(sm.quantity) ELSE 0 END),0) exits,
        COUNT(*) movements,
        ROUND(COALESCE(SUM(ABS(sm.quantity)*COALESCE(NULLIF(sm.unit_price,0),p.price)),0),2) movementValue
      FROM stock_movements sm JOIN products p ON p.id=sm.product_id
      WHERE (?='' OR date(sm.created_at)>=date(?)) AND (?='' OR date(sm.created_at)<=date(?))
        AND (?=0 OR p.id=?) AND (?='' OR p.category=?)`,
    )
    .get(...movementArgs);

  const salesSummary = db
    .prepare(
      `SELECT COUNT(DISTINCT i.id) invoices,
        COALESCE(SUM(il.quantity),0) unitsSold,
        ROUND(COALESCE(SUM(CASE WHEN i.subtotal>0 THEN il.total_line*i.total_amount/i.subtotal ELSE 0 END),0),2) revenue,
        ROUND(CASE WHEN COUNT(DISTINCT i.id)>0
          THEN SUM(CASE WHEN i.subtotal>0 THEN il.total_line*i.total_amount/i.subtotal ELSE 0 END)/COUNT(DISTINCT i.id)
          ELSE 0 END,2) averageTicket
       FROM invoice_lines il JOIN invoices i ON i.id=il.invoice_id
       LEFT JOIN products p ON p.id=il.product_id
       WHERE i.status='validated' AND (?='' OR date(i.invoice_date)>=date(?)) AND (?='' OR date(i.invoice_date)<=date(?))
         AND (?=0 OR p.id=?) AND (?='' OR il.category=?)`,
    )
    .get(...salesArgs);

  const stockSummary = db
    .prepare(
      `SELECT COUNT(*) products,COALESCE(SUM(stock_quantity),0) unitsInStock,
        SUM(CASE WHEN stock_quantity<=min_stock_threshold THEN 1 ELSE 0 END) lowStockProducts,
        SUM(CASE WHEN stock_quantity=0 THEN 1 ELSE 0 END) outOfStockProducts
       FROM products WHERE deleted_at IS NULL AND (?=0 OR id=?) AND (?='' OR category=?)`,
    )
    .get(productId, productId, category, category);

  let previousRevenue: number | null = null;
  if (/^\d{4}-\d{2}-\d{2}$/.test(from) && /^\d{4}-\d{2}-\d{2}$/.test(to)) {
    const start = new Date(`${from}T00:00:00Z`);
    const end = new Date(`${to}T00:00:00Z`);
    if (Number.isFinite(start.getTime()) && Number.isFinite(end.getTime()) && start <= end) {
      const days = Math.floor((end.getTime() - start.getTime()) / 86_400_000) + 1;
      const previousEnd = new Date(start.getTime() - 86_400_000);
      const previousStart = new Date(previousEnd.getTime() - (days - 1) * 86_400_000);
      const previousFrom = previousStart.toISOString().slice(0, 10);
      const previousTo = previousEnd.toISOString().slice(0, 10);
      previousRevenue = Number(
        (
          db
            .prepare(
              `SELECT ROUND(COALESCE(SUM(
          CASE WHEN i.subtotal>0 THEN il.total_line*i.total_amount/i.subtotal ELSE 0 END
        ),0),2) revenue
         FROM invoice_lines il JOIN invoices i ON i.id=il.invoice_id
         LEFT JOIN products p ON p.id=il.product_id
         WHERE i.status='validated' AND date(i.invoice_date)>=date(?) AND date(i.invoice_date)<=date(?)
           AND (?=0 OR p.id=?) AND (?='' OR il.category=?)`,
            )
            .get(previousFrom, previousTo, productId, productId, category, category) as {
            revenue: number;
          }
        ).revenue,
      );
    }
  }

  return {
    summary,
    salesSummary,
    stockSummary,
    comparison: { previousRevenue },
    movements,
    topProducts,
    topCategories,
    priceEvolution,
  };
}
