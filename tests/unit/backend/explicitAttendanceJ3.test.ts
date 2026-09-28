import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import bcrypt from 'bcryptjs';
import Database from 'better-sqlite3';
import { afterEach, describe, expect, it } from 'vitest';
import { schema } from '../../../backend/src/database/schema';
import { performExplicitAttendance } from '../../../backend/src/domain/attendance/explicitAttendance';

const roots: string[] = [];
afterEach(() => { for (const root of roots.splice(0)) fs.rmSync(root, { recursive: true, force: true }); });

function fixture(file = ':memory:') {
  const database = new Database(file); database.exec(schema);
  const insert = database.prepare('INSERT INTO users(username,password_hash,role,first_name,last_name,initials,active) VALUES(?,?,?,?,?,?,?)');
  for (const [username, password, role, active] of [['a', 'Password-A!', 'employee', 1], ['b', 'Password-B!', 'employee', 1], ['manager', 'Password-C!', 'manager', 1], ['disabled', 'Password-D!', 'employee', 0]] as const)
    insert.run(username, bcrypt.hashSync(password, 4), role, username, 'Test', username.slice(0, 2).toUpperCase(), active);
  return database;
}
const request = (employeeId: number, password: string, action: 'CLOCK_IN' | 'CLOCK_OUT', extra = {}) => ({ employeeId, password, action, facilitatedBy: 1, ...extra });

describe('J.3 explicit multi-user attendance', () => {
  it('clocks multiple people independently and enforces transitions', () => {
    const database = fixture();
    try {
      performExplicitAttendance(database, request(1, 'Password-A!', 'CLOCK_IN'), '2026-09-25T08:00:00.000Z', {});
      expect(() => performExplicitAttendance(database, request(1, 'Password-A!', 'CLOCK_IN'), '2026-09-25T08:01:00.000Z', {})).toThrow('SERVICE_ALREADY_OPEN');
      performExplicitAttendance(database, request(2, 'Password-B!', 'CLOCK_IN'), '2026-09-25T08:02:00.000Z', {});
      performExplicitAttendance(database, request(3, 'Password-C!', 'CLOCK_IN'), '2026-09-25T08:03:00.000Z', {});
      expect(database.prepare('SELECT employee_id employeeId FROM attendances WHERE end_time IS NULL ORDER BY employee_id').all()).toEqual([{ employeeId: 1 }, { employeeId: 2 }, { employeeId: 3 }]);
      performExplicitAttendance(database, request(2, 'Password-B!', 'CLOCK_OUT'), '2026-09-25T12:00:00.000Z', {});
      expect(database.prepare('SELECT employee_id employeeId FROM attendances WHERE end_time IS NULL ORDER BY employee_id').all()).toEqual([{ employeeId: 1 }, { employeeId: 3 }]);
      expect(() => performExplicitAttendance(database, request(2, 'Password-B!', 'CLOCK_OUT'), '2026-09-25T12:01:00.000Z', {})).toThrow('NO_OPEN_SERVICE');
    } finally { database.close(); }
  });

  it('requires the selected person own credential and mutates nothing on failure', () => {
    const database = fixture();
    try {
      expect(() => performExplicitAttendance(database, request(2, 'Password-A!', 'CLOCK_IN'), '2026-09-25T08:00:00.000Z', {})).toThrow('INVALID_CREDENTIALS');
      expect(database.prepare('SELECT COUNT(*) count FROM attendances').get()).toEqual({ count: 0 });
      expect(database.prepare('SELECT failed_login_attempts attempts FROM users WHERE id=2').get()).toEqual({ attempts: 1 });
      expect(() => performExplicitAttendance(database, request(4, 'Password-D!', 'CLOCK_IN'), '2026-09-25T08:00:00.000Z', {})).toThrow('INVALID_CREDENTIALS');
      expect(database.prepare('SELECT COUNT(*) count FROM attendances').get()).toEqual({ count: 0 });
    } finally { database.close(); }
  });

  it('accepts no renderer timestamp and records only backend-supplied current time', () => {
    const database = fixture();
    try {
      expect(() => performExplicitAttendance(database, request(1, 'Password-A!', 'CLOCK_IN', { timestamp: '2099-01-01' }), '2026-09-25T08:00:00.000Z', {})).toThrow('VALIDATION_ERROR');
      expect(() => performExplicitAttendance(database, request(1, 'Password-A!', 'CLOCK_IN', { startTime: '2020-01-01' }), '2026-09-25T08:00:00.000Z', {})).toThrow('VALIDATION_ERROR');
      performExplicitAttendance(database, request(1, 'Password-A!', 'CLOCK_IN'), '2026-09-25T08:00:00.000Z', {});
      expect(database.prepare('SELECT start_time startTime,source,session_ref sessionRef FROM attendances').get()).toEqual({ startTime: '2026-09-25T08:00:00.000Z', source: 'EXPLICIT', sessionRef: null });
    } finally { database.close(); }
  });

  it('preserves explicit open attendance and historical authentication records across restart', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'store-j3-profile-')); roots.push(root);
    expect(path.resolve(root).startsWith(path.resolve(os.tmpdir()))).toBe(true);
    expect(path.resolve(root)).not.toBe(path.resolve(process.env.APPDATA || '.'));
    const file = path.join(root, 'store.db'); let database = fixture(file);
    database.prepare("INSERT INTO attendances(employee_id,start_time,end_time,source,status) VALUES(1,'2025-01-01T08:00:00Z','2025-01-01T16:00:00Z','AUTHENTICATION','VALID')").run();
    performExplicitAttendance(database, request(2, 'Password-B!', 'CLOCK_IN'), '2026-09-25T08:00:00.000Z', {});
    database.close(); database = new Database(file);
    try {
      expect(database.prepare("SELECT COUNT(*) count FROM attendances WHERE source='AUTHENTICATION'").get()).toEqual({ count: 1 });
      expect(database.prepare('SELECT employee_id employeeId FROM attendances WHERE end_time IS NULL').get()).toEqual({ employeeId: 2 });
      expect(database.pragma('integrity_check')).toEqual([{ integrity_check: 'ok' }]);
      expect(database.pragma('foreign_key_check')).toEqual([]);
    } finally { database.close(); }
  });

  it('never persists the attendance credential', () => {
    const database = fixture();
    try {
      performExplicitAttendance(database, request(1, 'Password-A!', 'CLOCK_IN'), '2026-09-25T08:00:00.000Z', {});
      expect(JSON.stringify(database.prepare('SELECT * FROM attendances').all())).not.toContain('Password-A!');
    } finally { database.close(); }
  });
});
