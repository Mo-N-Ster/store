import { Clock3, LogIn, LogOut, Pencil, UserCheck } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Button, EmptyState, Modal, Select, Tabs, TextInput } from '../../../design-system';
import { PasswordInput } from '../../../components/UI/PasswordInput';
import { statusKey } from '../../../utils/entityLabels';
import { todayIso } from '../../../utils/localDate';
import { attendanceService } from '../../../services/attendanceService';
import { can, type EffectivePermission } from '../../../security/permissions';

type SheetRow = { id: number; username: string; firstName: string; lastName: string; role: string; attendanceId: number | null; startTime: string | null; endTime: string | null; durationMinutes: number; state: 'PRESENT' | 'COMPLETED' | 'ABSENT' };
type HistoryRow = { id: number; employeeId: number; username: string; firstName: string; lastName: string; role: string; startTime: string; endTime: string | null; hours: number | null; status: string; source: string };
const today = todayIso;
const time = (value: string | null) => value ? new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '—';
const duration = (minutes: number) => `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;

export function PresencePage({ notify, permissions, initialEmployeeId }: { management?: boolean; notify: (message: string) => void; permissions: EffectivePermission[]; initialEmployeeId?: number }) {
  const { t } = useTranslation();
  const [tab, setTab] = useState('today');
  const [date, setDate] = useState(today());
  const [employee, setEmployee] = useState(initialEmployeeId ? String(initialEmployeeId) : '');
  const [role, setRole] = useState('');
  const [state, setState] = useState('');
  const [rows, setRows] = useState<SheetRow[]>([]);
  const [history, setHistory] = useState<HistoryRow[]>([]);
  const [clockTarget, setClockTarget] = useState<{ row: SheetRow; action: 'CLOCK_IN' | 'CLOCK_OUT' } | null>(null);
  const [password, setPassword] = useState('');
  const [credentialError, setCredentialError] = useState('');
  const [correct, setCorrect] = useState<HistoryRow | null>(null);
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const passwordRef = useRef<HTMLInputElement>(null);
  useEffect(() => { if (credentialError && !busy) passwordRef.current?.focus(); }, [credentialError, busy]);
  const sheetFilters = useMemo(() => ({ date, employeeId: employee ? Number(employee) : undefined, role: role || undefined, state: state || undefined }), [date, employee, role, state]);
  const load = async () => {
    const [sheet, records] = await Promise.all([
      attendanceService.sheet(sheetFilters),
      attendanceService.history({ from: date, to: date, employeeId: employee ? Number(employee) : undefined, role: role || undefined }),
    ]);
    setRows(sheet); setHistory(records);
  };
  useEffect(() => { void load(); }, [sheetFilters]);
  const people = useMemo(() => {
    const values = new Map<number, { id: number; label: string }>();
    for (const row of rows) values.set(row.id, { id: row.id, label: `${row.firstName} ${row.lastName}` });
    for (const row of history) values.set(row.employeeId, { id: row.employeeId, label: `${row.firstName} ${row.lastName}` });
    return [...values.values()].sort((a, b) => a.label.localeCompare(b.label));
  }, [history, rows]);
  const presentCount = rows.filter((row) => row.state === 'PRESENT').length;
  const closeClock = () => { setPassword(''); setCredentialError(''); setClockTarget(null); };
  const submitClock = (event: React.FormEvent) => {
    event.preventDefault();
    if (!clockTarget || lock.current) return;
    lock.current = true; setBusy(true); setCredentialError('');
    void attendanceService.clock({ employeeId: clockTarget.row.id, password, action: clockTarget.action })
      .then(async () => { closeClock(); await load(); notify(t(clockTarget.action === 'CLOCK_IN' ? 'clockInRecorded' : 'clockOutRecorded')); })
      .catch((error) => { setPassword(''); setCredentialError(t(String(error?.message).includes('INVALID_CREDENTIALS') ? 'attendanceCredentialInvalid' : 'operationFailed')); })
      .finally(() => { lock.current = false; setBusy(false); });
  };
  const dailyPanel = <div className="attendance-view">
    <div className="ops-summary-grid"><article><UserCheck /><span>{t('currentlyPresent')}</span><strong>{presentCount}</strong></article><article><Clock3 /><span>{t('peopleOnSheet')}</span><strong>{rows.length}</strong></article></div>
    {!rows.length ? <EmptyState title={t('noAttendanceForFilters')} /> : <div className="attendance-sheet" role="table" aria-label={t('dailyAttendanceSheet')}>
      <div className="attendance-sheet__head" role="row"><span role="columnheader">{t('person')}</span><span role="columnheader">{t('role')}</span><span role="columnheader">{t('clockIn')}</span><span role="columnheader">{t('clockOut')}</span><span role="columnheader">{t('duration')}</span><span role="columnheader">{t('status')}</span><span role="columnheader">{t('actions')}</span></div>
      <div className="attendance-sheet__body">{rows.map((row) => <div className="attendance-sheet__row" role="row" key={row.id}><span role="cell" data-label={t('person')}><strong>{row.firstName} {row.lastName}</strong><small>{row.username}</small></span><span role="cell" data-label={t('role')}>{t(row.role)}</span><span role="cell" data-label={t('clockIn')}>{time(row.startTime)}</span><span role="cell" data-label={t('clockOut')}>{time(row.endTime)}</span><span role="cell" data-label={t('duration')}>{row.state === 'PRESENT' ? t('inProgress') : row.startTime ? duration(row.durationMinutes) : '—'}</span><span role="cell" data-label={t('status')}><span className={`ops-status ops-status--${row.state === 'PRESENT' ? 'present' : row.state === 'COMPLETED' ? 'validated' : 'absent'}`}>{t(row.state === 'PRESENT' ? 'present' : row.state === 'COMPLETED' ? 'attendanceCompleted' : 'absent')}</span></span><span role="cell" data-label={t('actions')}>{date === today() ? <Button size="sm" variant={row.state === 'PRESENT' ? 'secondary' : 'primary'} icon={row.state === 'PRESENT' ? LogOut : LogIn} onClick={() => { setPassword(''); setCredentialError(''); setClockTarget({ row, action: row.state === 'PRESENT' ? 'CLOCK_OUT' : 'CLOCK_IN' }); }}>{t(row.state === 'PRESENT' ? 'clockOut' : 'clockIn')}</Button> : '—'}</span></div>)}</div>
    </div>}
  </div>;
  const historyPanel = !history.length ? <EmptyState title={t('noAttendanceHistory')} /> : <div className="attendance-history">{history.map((row) => <article key={row.id}><div><strong>{row.firstName} {row.lastName}</strong><span>{new Date(row.startTime).toLocaleString()} → {row.endTime ? new Date(row.endTime).toLocaleString() : t('inProgress')}</span></div><span>{row.endTime ? `${row.hours ?? 0} h` : t('inProgress')}</span><span className={`ops-status ops-status--${row.endTime ? 'validated' : 'present'}`}>{t(statusKey(row.endTime ? row.status : 'OPEN', 'attendance'))}</span>{can(permissions, 'PRESENCE', 'UPDATE') && <Button size="sm" variant="secondary" icon={Pencil} disabled={!row.endTime} onClick={() => setCorrect(row)}>{t('correct')}</Button>}</article>)}</div>;
  return <div className="ops-domain">
    <header className="attendance-heading"><div><span className="eyebrow">{t('attendanceAgenda')}</span><h2>{t('dailyAttendanceSheet')}</h2><p>{t('explicitAttendanceHint')}</p></div></header>
    <div className="ops-filters"><TextInput type="date" label={t('date')} value={date} max={today()} onChange={(event) => setDate(event.target.value)} /><Select label={t('employee')} value={employee} onChange={(event) => setEmployee(event.target.value)}><option value="">{t('allEmployees')}</option>{people.map((person) => <option key={person.id} value={person.id}>{person.label}</option>)}</Select><Select label={t('role')} value={role} onChange={(event) => setRole(event.target.value)}><option value="">{t('allRoles')}</option><option value="employee">{t('employee')}</option><option value="manager">{t('manager')}</option><option value="owner">{t('owner')}</option></Select><Select label={t('status')} value={state} onChange={(event) => setState(event.target.value)}><option value="">{t('allStatuses')}</option><option value="PRESENT">{t('present')}</option><option value="COMPLETED">{t('attendanceCompleted')}</option><option value="ABSENT">{t('absent')}</option></Select></div>
    <Tabs ariaLabel={t('attendanceSections')} active={tab} onChange={setTab} tabs={[{ id: 'today', label: t('dailySheet'), panel: dailyPanel }, { id: 'history', label: t('history'), panel: historyPanel }]} />
    {clockTarget && <Modal title={t(clockTarget.action === 'CLOCK_IN' ? 'confirmClockIn' : 'confirmClockOut')} description={t('attendanceSignatureHint', { person: `${clockTarget.row.firstName} ${clockTarget.row.lastName}` })} closeLabel={t('close')} onClose={() => !busy && closeClock()} dismissible={!busy}><form className="ops-form" onSubmit={submitClock}><Alert variant="info" title={`${clockTarget.row.firstName} ${clockTarget.row.lastName}`}>{t('backendTimeHint')}</Alert><label className="ds-field"><span className="ds-field__label">{t('password')}</span><PasswordInput ref={passwordRef} data-autofocus disabled={busy} value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" aria-invalid={credentialError ? true : undefined} required autoFocus /></label>{credentialError && <p className="ds-field__error" role="alert">{credentialError}</p>}<div className="ops-form__actions"><Button type="button" variant="secondary" disabled={busy} onClick={closeClock}>{t('cancel')}</Button><Button type="submit" loading={busy} loadingLabel={t('working')} icon={clockTarget.action === 'CLOCK_IN' ? LogIn : LogOut}>{t(clockTarget.action === 'CLOCK_IN' ? 'clockIn' : 'clockOut')}</Button></div></form></Modal>}
    {correct && <Modal title={t('correctAttendance')} description={t('attendanceCorrectionTraceHint')} closeLabel={t('close')} onClose={() => !busy && setCorrect(null)} dismissible={!busy}><form className="ops-form" onSubmit={(event) => { event.preventDefault(); if (lock.current) return; lock.current = true; setBusy(true); const form = new FormData(event.currentTarget); void attendanceService.correct({ id: correct.id, startTime: String(form.get('startTime')), endTime: String(form.get('endTime')), reason: String(form.get('reason')) }).then(async () => { setCorrect(null); await load(); notify(t('attendanceCorrected')); }).catch(() => notify(t('operationFailed'))).finally(() => { lock.current = false; setBusy(false); }); }}><Alert variant="warning" title={t('historicalCorrection')}>{t('originalValues')}: {new Date(correct.startTime).toLocaleString()} → {correct.endTime ? new Date(correct.endTime).toLocaleString() : t('inProgress')}</Alert><TextInput name="startTime" type="datetime-local" label={t('newStart')} defaultValue={String(correct.startTime).slice(0, 16).replace(' ', 'T')} max={new Date().toISOString().slice(0, 16)} required /><TextInput name="endTime" type="datetime-local" label={t('newEnd')} defaultValue={String(correct.endTime).slice(0, 16).replace(' ', 'T')} max={new Date().toISOString().slice(0, 16)} required /><TextInput name="reason" label={t('reason')} minLength={3} required /><div className="ops-form__actions"><Button type="button" variant="secondary" onClick={() => setCorrect(null)}>{t('cancel')}</Button><Button type="submit" loading={busy} loadingLabel={t('saving')}>{t('saveCorrection')}</Button></div></form></Modal>}
  </div>;
}
