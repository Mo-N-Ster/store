import type Database from 'better-sqlite3';
import { writeAudit } from '../system/auditWriter.js';

export function permissionSnapshot(db: Database.Database, userId: number) {
  if (!Number.isSafeInteger(userId) || userId <= 0) throw new Error('VALIDATION_ERROR');
  const user = db.prepare('SELECT id,username,role,active FROM users WHERE id=?').get(userId) as { id: number; username: string; role: string; active: number } | undefined;
  if (!user) throw new Error('USER_NOT_FOUND');
  const inherited = (db.prepare(`SELECT p.module||':'||p.action code FROM user_roles ur
    JOIN role_permissions rp ON rp.role_id=ur.role_id JOIN permissions p ON p.id=rp.permission_id
    WHERE ur.user_id=? ORDER BY code`).all(userId) as { code: string }[]).map((row) => row.code);
  const denied = (db.prepare(`SELECT p.module||':'||p.action code FROM user_permission_denials d
    JOIN permissions p ON p.id=d.permission_id WHERE d.user_id=? ORDER BY code`).all(userId) as { code: string }[]).map((row) => row.code);
  // Owner recovery authority can never be removed by individual overrides.
  const effective = user.active ? inherited.filter((code) => user.role === 'owner' || !denied.includes(code)) : [];
  return { user, inherited, denied, effective };
}

export function savePermissionDenials(db: Database.Database, input: { actorId: number; userId: number; denied: string[]; expectedDenied: string[]; expectedRole: string }) {
  return db.transaction(() => {
    const actor = db.prepare('SELECT role,active FROM users WHERE id=?').get(input.actorId) as { role: string; active: number } | undefined;
    if (!actor?.active || actor.role !== 'owner') throw new Error('FORBIDDEN');
    const current = permissionSnapshot(db, input.userId);
    if (current.user.role === 'owner') throw new Error('LAST_OWNER_REQUIRED');
    if (!Array.isArray(input.denied) || !Array.isArray(input.expectedDenied) || input.denied.length > 70 || input.expectedDenied.length > 70 || input.denied.some((code) => typeof code !== 'string' || !current.inherited.includes(code)) || input.expectedDenied.some((code) => typeof code !== 'string')) throw new Error('VALIDATION_ERROR');
    if (new Set(input.denied).size !== input.denied.length) throw new Error('VALIDATION_ERROR');
    if (current.user.role !== input.expectedRole || JSON.stringify([...input.expectedDenied].sort()) !== JSON.stringify(current.denied)) throw new Error('CONFLICT');
    if (db.prepare("SELECT 1 FROM cash_sessions WHERE employee_id=? AND status='OPEN'").get(input.userId)) throw new Error('CASH_SESSION_OPEN');
    db.prepare('DELETE FROM user_permission_denials WHERE user_id=?').run(input.userId);
    const insert = db.prepare(`INSERT INTO user_permission_denials(user_id,permission_id) SELECT ?,id FROM permissions WHERE module||':'||action=?`);
    for (const code of input.denied) insert.run(input.userId, code);
    writeAudit(db, input.actorId, 'permissions_restricted', 'user', String(input.userId), 'SUCCESS', { before: current.denied, after: [...input.denied].sort() });
    return permissionSnapshot(db, input.userId);
  })();
}
