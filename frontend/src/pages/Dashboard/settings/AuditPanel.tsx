import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, TextInput, NumberInput, ResponsiveTable } from '../../../design-system';
import { storeApi } from '../../../services/api';
import { formatMoney } from '../../../utils/formatters';

type Row = { id: number; userId: number | null; action: string; entity: string; entityId: string; outcome: string; createdAt: string; responsibleId: number | null; responsibleName: string | null; cashReference: string | null; cashAmount: number | null; cashCurrency: string | null };
export function AuditPanel() {
  const { t } = useTranslation();
  const [filters, setFilters] = useState({ from: '', to: '', action: '', userId: '' });
  const [applied, setApplied] = useState(filters);
  const [beforeId, setBeforeId] = useState<number>();
  const [rows, setRows] = useState<Row[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(false);
  useEffect(() => { let active = true; setBusy(true); setError(false); void storeApi.auditLogs({ ...applied, userId: applied.userId ? Number(applied.userId) : undefined, beforeId }).then((result) => { if (active) setRows(result); }).catch(() => { if (active) { setRows([]); setError(true); } }).finally(() => { if (active) setBusy(false); }); return () => { active = false; }; }, [applied, beforeId]);
  return <section><h2>{t('admin_audit')}</h2><p>{t('auditReadHint')}</p><form className="ops-filters" onSubmit={(event) => { event.preventDefault(); setBeforeId(undefined); setApplied({ ...filters }); }}>
    <TextInput type="date" label={t('startDate')} value={filters.from} onChange={(event) => setFilters({ ...filters, from: event.target.value })} />
    <TextInput type="date" label={t('endDate')} value={filters.to} onChange={(event) => setFilters({ ...filters, to: event.target.value })} />
    <TextInput label={t('auditActionCode')} value={filters.action} onChange={(event) => setFilters({ ...filters, action: event.target.value })} />
    <NumberInput label={t('auditActorId')} min="1" step="1" value={filters.userId} onChange={(event) => setFilters({ ...filters, userId: event.target.value })} />
    <Button type="submit" disabled={busy}>{t('applyFilters')}</Button></form>
    <p className="ds-caption">{t('auditSnapshotHint')}</p>
    {busy ? <p role="status">{t('loading')}</p> : error ? <p role="alert">{t('operationFailed')}</p> : <ResponsiveTable label={t('admin_audit')}><table><thead><tr>{['dateAndTime', 'auditActorId', 'auditResponsible', 'auditCash', 'auditActionCode', 'reference', 'status'].map((key) => <th key={key}>{t(key)}</th>)}</tr></thead><tbody>{rows.map((row) => <tr key={row.id}><td>{row.createdAt}</td><td>{row.userId ?? '—'}</td><td>{row.responsibleName ? `${row.responsibleName} (#${row.responsibleId})` : t('auditNotRecorded')}</td><td>{row.cashAmount !== null && row.cashCurrency ? <>{formatMoney(row.cashAmount, row.cashCurrency)}<small className="audit-cash-reference">{row.cashReference}</small></> : t(row.responsibleId !== null ? 'auditNoCash' : 'auditNotRecorded')}</td><td>{row.action}</td><td>{row.entity} · {row.entityId}</td><td>{row.outcome}</td></tr>)}</tbody></table>{!rows.length && <p>{t('noDataForPeriod')}</p>}</ResponsiveTable>}
    <div className="ops-form__actions"><Button variant="secondary" disabled={busy || beforeId === undefined} onClick={() => setBeforeId(undefined)}>{t('auditLatest')}</Button><Button disabled={busy || rows.length < 100} onClick={() => setBeforeId(rows.at(-1)?.id)}>{t('auditOlder')}</Button></div>
  </section>;
}
