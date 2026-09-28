import { AsyncLocalStorage } from 'node:async_hooks';
import type Database from 'better-sqlite3';

export const auditSession = new AsyncLocalStorage<{ id: number; displayName: string }>();
export function writeAudit(db: Database.Database, userId: number | null, action: string, entity: string, entityId: string, outcome = 'SUCCESS', details?: unknown) {
  const responsible = auditSession.getStore();
  const cash = responsible ? db.prepare(`SELECT c.reference,
    CASE WHEN c.status='CLOSED' THEN c.expected_amount ELSE c.opening_amount+
      COALESCE((SELECT SUM(amount) FROM payments WHERE cash_session_id=c.id AND status='CAPTURED'),0) END amount
    FROM cash_sessions c WHERE c.employee_id=? AND (c.status='OPEN' OR (?='cash_session' AND CAST(c.id AS TEXT)=?))
    ORDER BY c.id DESC LIMIT 1`).get(responsible.id, entity, entityId) as { reference: string; amount: number } | undefined : undefined;
  const currency = cash ? (db.prepare("SELECT value FROM settings WHERE key='currency'").get() as { value: string } | undefined)?.value || 'EUR' : null;
  db.prepare(`INSERT INTO audit_logs(user_id,action,entity,entity_id,outcome,details,responsible_id,responsible_name,cash_reference,cash_amount,cash_currency)
    VALUES(?,?,?,?,?,?,?,?,?,?,?)`).run(userId, action, entity, entityId, outcome, details === undefined ? null : JSON.stringify(details), responsible?.id ?? null, responsible?.displayName ?? null, cash?.reference ?? null, cash?.amount ?? null, currency);
}
