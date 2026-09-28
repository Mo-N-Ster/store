import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Button, EmptyState, ErrorState, KpiCard, Skeleton } from '../../../design-system';
import { useStorePreferences } from '../../../hooks/useStorePreferences';
import { dashboardService } from '../../../services/dashboardService';
import { productService } from '../../../services/productService';
import { saleService } from '../../../services/saleService';
import type { Product } from '../../../types';
import { formatMoney, todayIso } from '../../../utils/formatters';
import type { DashboardSection } from '../../../navigation/navigation';
import { can, type EffectivePermission } from '../../../security/permissions';
import { PresentEmployees } from './PresentEmployees';

export function Overview({ onNavigate, permissions, onOpenPresence }: { onNavigate: (section: DashboardSection) => void; permissions: EffectivePermission[]; onOpenPresence: (id?: number) => void }) {
  const { t } = useTranslation();
  const { currency } = useStorePreferences();
  const [summary, setSummary] = useState<any>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [sales, setSales] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const load = async () => {
    setLoading(true); setFailed(false);
    try {
      const today = todayIso();
      const [dashboard, productRows, invoiceRows] = await Promise.all([dashboardService.get(), productService.list(), saleService.list({ from: today, to: today, search: '' })]);
      setSummary(dashboard); setProducts(productRows); setSales(invoiceRows);
    } catch { setSummary(null); setProducts([]); setSales([]); setFailed(true); }
    finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);
  const lowStock = useMemo(() => products.filter((row) => row.stockQuantity <= row.minStockThreshold).sort((a, b) => a.stockQuantity - b.stockQuantity), [products]);
  const outOfStock = lowStock.filter((row) => row.stockQuantity === 0);
  const validatedSales = sales.filter((row) => row.status === 'validated');
  const averageTicket = summary?.salesToday ? Number(summary.revenueToday || 0) / Number(summary.salesToday) : 0;
  const trend = summary?.salesChart || [];
  const trendMax = Math.max(1, ...trend.map((item: any) => Number(item.value)));

  if (loading) return <section className="dashboard-home" aria-label={t('loading')}><div className="report-loading"><Skeleton label={t('loading')} /><Skeleton label={t('loading')} /><Skeleton label={t('loading')} /></div></section>;
  if (failed || !summary) return <ErrorState title={t('dashboardLoadError')} description={t('retryDashboardHint')} action={<Button onClick={() => void load()}>{t('retry')}</Button>} />;
  return <section className="dashboard-home" aria-labelledby="dashboard-title"><header className="ops-page__header"><div><span className="eyebrow">{t('operationalOverview')}</span><h1 id="dashboard-title">{t('home')}</h1><p>{t('dashboardTodayHint')}</p></div><span className="ops-status ops-status--active">{t('todayLocal')}</span></header>
    {(outOfStock.length > 0 || lowStock.length > 0) && <Alert variant={outOfStock.length ? 'danger' : 'warning'} title={t('attentionRequired')} action={<Button size="sm" variant="secondary" onClick={() => onNavigate('products')}>{t('reviewProducts')}</Button>}><span>{t('stockAttentionSummary', { out: outOfStock.length, low: lowStock.length })}</span></Alert>}
    <div className="dashboard-kpis"><KpiCard label={t('revenueToday')} value={formatMoney(summary.revenueToday, currency)} context={t('validatedSalesOnly')} /><KpiCard label={t('transactions')} value={summary.salesToday || 0} context={t('validatedSalesOnly')} /><KpiCard label={t('averageTicket')} value={formatMoney(averageTicket, currency)} context={t('averageBasketDefinition')} /><KpiCard label={t('outOfStock')} value={outOfStock.length} context={t('currentState')} /></div>
    <div className="dashboard-grid"><div className="dashboard-main-stack"><section className="report-card dashboard-trend"><header><div><h2>{t('salesLastThirtyDays')}</h2><p>{t('validatedSalesTrendHint')}</p></div><Button size="sm" variant="secondary" onClick={() => onNavigate('charts')}>{t('openReports')}</Button></header>{trend.length ? <div className="dashboard-bars" role="img" aria-label={t('salesTrendAccessible')} style={{ '--bar-count': trend.length } as React.CSSProperties}>{trend.map((row: any) => { const max = trendMax; return <div key={row.label}><i style={{ height: `${Math.max(3, Number(row.value) / max * 100)}%` }} /><span>{String(row.label).slice(5)}</span><b>{formatMoney(row.value, currency)}</b></div>; })}</div> : <EmptyState title={t('noDataForPeriod')} />}</section>
      <section className="report-card"><h2>{t('todayTransactions')}</h2>{validatedSales.length ? <ul className="dashboard-sales-list" tabIndex={0} aria-label={t('todayTransactions')}>{validatedSales.map((row) => <li key={row.id}><span><strong>{row.id}</strong><small>{row.seller}</small></span><b>{formatMoney(row.totalAmount, currency)}</b></li>)}</ul> : <EmptyState title={t('noSalesToday')} />}</section>
      </div><div className="dashboard-side-stack"><section className="report-card"><h2>{t('stockAttention')}</h2>{lowStock.length ? <ul className="dashboard-attention-list" tabIndex={0} aria-label={t('stockAttention')}>{lowStock.map((row) => <li key={row.id}><span><strong>{row.name}</strong><small>{row.category}</small></span><b>{row.stockQuantity} / {row.minStockThreshold}</b><span className={`ops-status ops-status--${row.stockQuantity === 0 ? 'out' : 'low'}`}>{t(row.stockQuantity === 0 ? 'stockOut' : 'stockLow')}</span></li>)}</ul> : <EmptyState title={t('noStockAlerts')} description={t('stockHealthyHint')} />}</section>{can(permissions, 'PRESENCE', 'READ') && <PresentEmployees onOpen={onOpenPresence} />}<section className="report-card dashboard-context"><h2>{t('monthContext')}</h2><strong>{formatMoney(summary.revenueMonth, currency)}</strong><p>{t('monthRevenueDefinition')}</p><Button variant="secondary" onClick={() => onNavigate('charts')}>{t('analyzePeriod')}</Button></section></div></div>
  </section>;
}
