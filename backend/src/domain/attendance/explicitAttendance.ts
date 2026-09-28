import type Database from 'better-sqlite3';
import bcrypt from 'bcryptjs';
import { isLoginLocked, loginPolicy, nextFailedLogin } from '../auth/loginPolicy.js';
import { canEmployeeAuthenticate, isEmployeeStatus } from '../employee/employeeStatus.js';
import { validateAttendanceTransition, type AttendanceAction } from './attendancePolicy.js';

const invalidHash = bcrypt.hashSync('STORE_INVALID_ATTENDANCE_TARGET', 10);

export type ExplicitAttendanceInput = {
  employeeId: number;
  password: string;
  action: AttendanceAction;
  facilitatedBy: number;
  timestamp?: unknown;
  startTime?: unknown;
  endTime?: unknown;
};

export function performExplicitAttendance(database: Database.Database, input: ExplicitAttendanceInput, occurredAt: string, settings: Record<string, string>) {
  if (input.timestamp !== undefined || input.startTime !== undefined || input.endTime !== undefined)
    throw new Error('VALIDATION_ERROR');
  const target = database.prepare(`SELECT users.id,users.password_hash hash,users.active,users.failed_login_attempts attempts,
    users.locked_until lockedUntil,users.role,employees.status employmentStatus
    FROM users LEFT JOIN employees ON employees.user_id=users.id WHERE users.id=?`).get(input.employeeId) as
    | { id: number; hash: string; active: number; attempts: number; lockedUntil: string | null; role: string; employmentStatus: string | null }
    | undefined;
  const currentTime = new Date(occurredAt);
  const employmentValid = !target?.employmentStatus || (isEmployeeStatus(target.employmentStatus) && canEmployeeAuthenticate(target.employmentStatus));
  const passwordMatches = bcrypt.compareSync(typeof input.password === 'string' ? input.password : '', target?.hash || invalidHash);
  if (!target || !target.active || !employmentValid || isLoginLocked(target.lockedUntil, currentTime) || !passwordMatches) {
    if (target && !isLoginLocked(target.lockedUntil, currentTime)) {
      const failure = nextFailedLogin(target.attempts, loginPolicy(settings), currentTime);
      database.prepare('UPDATE users SET failed_login_attempts=?,locked_until=? WHERE id=?').run(failure.failedAttempts, failure.lockedUntil, target.id);
    }
    throw new Error('INVALID_CREDENTIALS');
  }
  return database.transaction(() => {
    const open = database.prepare('SELECT id FROM attendances WHERE employee_id=? AND end_time IS NULL ORDER BY id DESC LIMIT 1').get(target.id) as { id: number } | undefined;
    const action = validateAttendanceTransition(input.action, Boolean(open));
    let attendanceId = open?.id;
    if (action === 'CLOCK_IN') {
      const result = database.prepare("INSERT INTO attendances(employee_id,start_time,session_ref,source,status) VALUES(?,?,NULL,'EXPLICIT','VALID')").run(target.id, occurredAt);
      attendanceId = Number(result.lastInsertRowid);
    } else database.prepare('UPDATE attendances SET end_time=? WHERE id=?').run(occurredAt, open!.id);
    database.prepare('UPDATE users SET failed_login_attempts=0,locked_until=NULL WHERE id=?').run(target.id);
    return { attendanceId: attendanceId!, subjectId: target.id, action };
  })();
}
