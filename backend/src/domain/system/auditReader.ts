import type Database from 'better-sqlite3';

export function readAudit(db: Database.Database, filters: { from?: string; to?: string; action?: string; userId?: number; beforeId?: number } = {}) {
  if (!filters || typeof filters !== 'object' || Array.isArray(filters)) throw new Error('VALIDATION_ERROR');
  const { from = '', to = '', action = '', userId, beforeId } = filters;
  for (const date of [from, to]) if (typeof date !== 'string' || (date && (!/^\d{4}-\d{2}-\d{2}$/.test(date) || Number.isNaN(Date.parse(date)) || new Date(date).toISOString().slice(0, 10) !== date))) throw new Error('VALIDATION_ERROR');
  if ((from && to && from > to) || typeof action !== 'string' || action.length > 100 || [userId, beforeId].some((id) => id !== undefined && (!Number.isSafeInteger(id) || id <= 0))) throw new Error('VALIDATION_ERROR');
  // Deliberately omit free-form details and identity joins: no secret disclosure,
  // and no reconstruction of an actor name that was never snapshotted.
  return db.prepare(`SELECT id,user_id userId,action,entity,entity_id entityId,outcome,created_at createdAt,
    responsible_id responsibleId,responsible_name responsibleName,cash_reference cashReference,cash_amount cashAmount,cash_currency cashCurrency
    FROM audit_logs WHERE (?='' OR date(created_at)>=?) AND (?='' OR date(created_at)<=?)
      AND (?='' OR action=?) AND (? IS NULL OR user_id=?) AND (? IS NULL OR id<?)
    ORDER BY id DESC LIMIT 100`).all(from, from, to, to, action, action, userId ?? null, userId ?? null, beforeId ?? null, beforeId ?? null);
}
