import { useId, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../../design-system';

export function ReportData({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const id = useId();
  return <section className="report-data">
    <Button type="button" variant="secondary" aria-expanded={open} aria-controls={id} onClick={() => setOpen(!open)}>{t('reportDataTable')}</Button>
    <p className="ds-caption">{t('chartTableEquivalentHint')}</p>
    <div id={id} className={`report-data__content${open ? ' is-open' : ''}`}>{children}</div>
  </section>;
}

export function ReportBars({ rows, label }: { rows: { label: string; value: number }[]; label: string }) {
  const max = Math.max(1, ...rows.map((row) => row.value));
  return <figure className="report-distribution"><figcaption>{label}</figcaption><ul>{rows.map((row, index) => <li key={index}>
    <span>{row.label}</span><strong>{row.value.toLocaleString(undefined, { maximumFractionDigits: 2 })}</strong>
    <div className="report-distribution__track" aria-hidden="true"><i style={{ width: `${row.value / max * 100}%`, backgroundColor: `var(--chart-series-${index % 6 + 1})` }} /></div>
  </li>)}</ul></figure>;
}
