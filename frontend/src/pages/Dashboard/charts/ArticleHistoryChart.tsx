import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Select } from '../../../design-system';
import { PriceLineChart } from './PriceLineChart';

export function articleHistorySeries(rows: { period: string; productId: number; product: string; quantity: number; revenue: number }[], metric: 'quantity' | 'revenue') {
  const values = new Map<string, { period: string; productId: number; productName: string; averagePrice: number }>();
  for (const row of rows) {
    const key = JSON.stringify([row.period, row.productId]);
    const point = values.get(key) || { period: row.period, productId: row.productId, productName: row.product, averagePrice: 0 };
    point.averagePrice += Number(row[metric]);
    values.set(key, point);
  }
  return [...values.values()];
}

export function ArticleHistoryChart({ rows, currency }: { rows: Parameters<typeof articleHistorySeries>[0]; currency: string }) {
  const { t } = useTranslation();
  const [metric, setMetric] = useState<'quantity' | 'revenue'>('quantity');
  const series = useMemo(() => articleHistorySeries(rows, metric), [rows, metric]);
  const label = metric === 'quantity' ? t('quantitySold') : `${t('revenue')} (${currency})`;
  return <section className="report-card"><h2>{t('articleSalesHistory')}</h2>
    <Select label={t('measure')} value={metric} onChange={(event) => setMetric(event.target.value as 'quantity' | 'revenue')}>
      <option value="quantity">{t('quantitySold')}</option><option value="revenue">{t('revenue')} ({currency})</option>
    </Select>
    <p className="ds-caption">{label} · {t('validatedSalesOnly')}</p>
    <PriceLineChart rows={series} label={`${t('articleSalesHistory')} — ${label}`} showTable={false} />
  </section>;
}
