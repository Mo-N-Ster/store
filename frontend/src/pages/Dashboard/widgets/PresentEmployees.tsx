import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../../design-system';
import { attendanceService } from '../../../services/attendanceService';
import { todayIso } from '../../../utils/localDate';

type PresentMember = { id: number; firstName: string; lastName: string; username: string; state: string; startTime: string | null };

export function PresentEmployees({ onOpen }: { onOpen: (id?: number) => void }) {
  const { t } = useTranslation();
  const [rows, setRows] = useState<PresentMember[]>([]);
  const [status, setStatus] = useState('loading');
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let disposed = false;
    let pending = false;
    const refresh = async () => {
      if (pending || document.hidden) return;
      pending = true;
      try {
        const result = await attendanceService.sheet({ date: todayIso(), state: 'PRESENT' }) as PresentMember[];
        if (!disposed) { setRows(result.filter((row) => row.state === 'PRESENT')); setStatus('ready'); }
      } catch { if (!disposed) { setRows([]); setStatus('error'); } }
      finally { pending = false; }
    };
    void refresh();
    const timer = window.setInterval(() => void refresh(), 30000);
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', refresh);
    return () => { disposed = true; window.clearInterval(timer); window.removeEventListener('focus', refresh); document.removeEventListener('visibilitychange', refresh); };
  }, [revision]);
  return <section className="report-card"><h2>{t('presentInStore')}{status === 'ready' ? ` (${rows.length})` : ''}</h2>
    <p className="ds-caption">{t('presentInStoreHint')}</p>
    {status === 'error' ? <><p role="status">{t('operationFailed')}</p><Button variant="secondary" onClick={() => setRevision((value) => value + 1)}>{t('retry')}</Button></> : status === 'loading' ? <p role="status">{t('loading')}</p> : !rows.length ? <p>{t('noPresentEmployees')}</p> :
      <ul className="dashboard-present-list" tabIndex={0} aria-label={t('presentInStore')}>{rows.map((row) => <li key={row.id}><button type="button" onClick={() => onOpen(row.id)}>
        <strong>{`${row.firstName || ''} ${row.lastName || ''}`.trim() || row.username}</strong>
        <span>{t('present')}{row.startTime ? ` · ${new Date(row.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}` : ''}</span>
      </button></li>)}</ul>}
    <div className="dashboard-presence-action"><Button type="button" variant="secondary" onClick={() => onOpen()}>{t('dailyAttendanceSheet')}</Button></div>
  </section>;
}
