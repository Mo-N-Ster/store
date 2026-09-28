import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Alert,
  Button,
  EmptyState,
  ErrorState,
  KpiCard,
  ResponsiveTable,
  Skeleton,
  Tabs,
} from '../../../design-system';
import { useStorePreferences } from '../../../hooks/useStorePreferences';
import { can, type EffectivePermission } from '../../../security/permissions';
import { operationsService } from '../../../services/operationsService';
import { attendanceService } from '../../../services/attendanceService';
import { productService } from '../../../services/productService';
import { reportService, type ReportFilters } from '../../../services/reportService';
import { saleService } from '../../../services/saleService';
import type { Product } from '../../../types';
import { formatMoney } from '../../../utils/formatters';
import { ModalBackdrop } from '../../../components/UI/ModalBackdrop';
import { MovementChart } from './MovementChart';
import { PriceLineChart } from './PriceLineChart';
import { RankingChart } from './RankingChart';
import { ReportData, ReportBars } from './ReportData';
import { ArticleHistoryChart } from './ArticleHistoryChart';
import { movementKey, statusKey } from '../../../utils/entityLabels';
import {
  aggregateRanking,
  contextualReportFilters,
  defaultReportFilters,
  periodFilters,
  purchaseSummary,
  revenueTrend,
  teamSummary,
  type ReportTab,
} from './reportingModel';

const statusLabel = (status: string, t: (key: string) => string) =>
  t(statusKey(status));

export function ChartsPage({
  notify,
  permissions,
}: {
  notify: (message: string) => void;
  permissions: EffectivePermission[];
}) {
  const { t } = useTranslation();
  const { currency } = useStorePreferences();
  const [filters, setFilters] = useState<ReportFilters>(defaultReportFilters);
  const [appliedFilters, setAppliedFilters] = useState<ReportFilters>(defaultReportFilters);
  const [data, setData] = useState<any>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [invoices, setInvoices] = useState<any[]>([]);
  const [purchases, setPurchases] = useState<any[]>([]);
  const [suppliers, setSuppliers] = useState<any[]>([]);
  const [attendance, setAttendance] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<'forbidden' | 'technical' | null>(null);
  const [tab, setTab] = useState<ReportTab>('summary');
  const [emailOpen, setEmailOpen] = useState(false);
  const [email, setEmail] = useState({
    to: '',
    subject: t('reportEmailSubject'),
    text: t('reportEmailBody', { date: new Date().toLocaleString() }),
  });

  const load = useCallback(
    async (requested: ReportFilters, requestedTab: ReportTab = tab) => {
      if (requested.from > requested.to) {
        notify(t('invalidDateRange'));
        return;
      }
      const effective = contextualReportFilters(requestedTab, requested);
      setLoading(true);
      setData(null);
      setError(null);
      try {
        const requests: Promise<any>[] = [
          reportService.get(effective),
          productService.list(),
          saleService.list({
            from: effective.from,
            to: effective.to,
            search: '',
            productId: effective.productId,
            category: effective.category,
          }),
        ];
        if (can(permissions, 'PURCHASES', 'READ'))
          requests.push(operationsService.purchases(effective), operationsService.suppliers());
        else requests.push(Promise.resolve([]), Promise.resolve([]));
        if (can(permissions, 'PRESENCE', 'READ'))
          requests.push(
            attendanceService.history({
              from: effective.from,
              to: effective.to,
              employeeId: effective.employeeId,
              state: effective.attendanceState,
            }),
          );
        else requests.push(Promise.resolve([]));
        const [report, productRows, invoiceRows, purchaseRows, supplierRows, attendanceRows] =
          await Promise.all(requests);
        setData(report);
        setProducts(productRows);
        setInvoices(invoiceRows);
        setPurchases(purchaseRows);
        setSuppliers(supplierRows);
        setAttendance(attendanceRows);
        setAppliedFilters(effective);
      } catch (caught: any) {
        setError(String(caught?.message || '').includes('FORBIDDEN') ? 'forbidden' : 'technical');
      } finally {
        setLoading(false);
      }
    },
    [notify, permissions, t, tab],
  );

  useEffect(() => {
    void load(filters);
  }, []);
  const categories = useMemo(
    () => [...new Set(products.map((row) => row.category))].sort(),
    [products],
  );
  const attendancePeople = useMemo(() => {
    const people = new Map<number, string>();
    for (const row of attendance)
      people.set(
        row.employeeId,
        `${row.firstName || ''} ${row.lastName || ''}`.trim() || row.username,
      );
    return [...people].sort((left, right) => left[1].localeCompare(right[1]));
  }, [attendance]);
  const productRanking = useMemo(
    () => aggregateRanking(data?.topProducts || [], 'productId', 'product'),
    [data],
  );
  const categoryRanking = useMemo(
    () => aggregateRanking(data?.topCategories || [], 'category', 'category'),
    [data],
  );
  const purchase = useMemo(
    () => purchaseSummary(purchases, appliedFilters),
    [appliedFilters, purchases],
  );
  const team = useMemo(() => teamSummary(attendance), [attendance]);
  const supplierRanking = useMemo(() => {
    const totals = new Map<string, { label: string; revenue: number; quantity: number }>();
    for (const row of purchase.validated) {
      const key = String(row.supplier_id ?? 'none');
      const item = totals.get(key) || { label: row.supplier || '—', revenue: 0, quantity: 0 };
      item.revenue += Number(row.total_amount || 0);
      item.quantity += 1;
      totals.set(key, item);
    }
    return [...totals.values()].sort((a, b) => b.revenue - a.revenue);
  }, [purchase]);
  const trend = data
    ? revenueTrend(Number(data.salesSummary.revenue), data.comparison?.previousRevenue)
    : null;
  const validatedInvoices = invoices.filter((row) => row.status === 'validated');
  const cancelledInvoices = invoices.filter((row) => row.status === 'cancelled');
  const movementPeriods = useMemo(() => {
    const values = new Map<string, { period: string; entries: number; exits: number }>();
    for (const row of data?.movements || []) {
      const item = values.get(row.period) || { period: row.period, entries: 0, exits: 0 };
      item.entries += Number(row.entries);
      item.exits += Number(row.exits);
      values.set(row.period, item);
    }
    return [...values.values()].sort((a, b) => a.period.localeCompare(b.period));
  }, [data]);

  const applyPreset = (days: number) => {
    const next = periodFilters(days);
    setFilters(next);
    void load(next);
  };
  const printable = async (operation: () => Promise<unknown>) => {
    document.body.classList.add('report-print-mode');
    try {
      await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
      return await operation();
    } finally {
      document.body.classList.remove('report-print-mode');
    }
  };
  const filename = `STORE-${tab}-${appliedFilters.from}-${appliedFilters.to}.pdf`;
  const exportPdf = async () => {
    try {
      const path = await printable(() => reportService.exportPdf(filename));
      if (path) notify(t('reportExported'));
    } catch {
      notify(t('reportExportError'));
    }
  };
  const sendEmail = async (event: FormEvent) => {
    event.preventDefault();
    try {
      const result: any = await printable(() => reportService.emailPdf({ ...email, filename }));
      setEmailOpen(false);
      notify(t(result?.status === 'sent' ? 'reportEmailed' : 'reportQueued'));
    } catch {
      notify(t('reportEmailError'));
    }
  };

  const table = (headers: string[], rows: React.ReactNode) => (
    <ReportData>
    <ResponsiveTable label={t('reportDataTable')}>
      <table>
        <thead>
          <tr>
            {headers.map((header) => (
              <th key={header}>{header}</th>
            ))}
          </tr>
        </thead>
        <tbody>{rows}</tbody>
      </table>
    </ResponsiveTable>
    </ReportData>
  );
  const noData = <EmptyState title={t('noDataForPeriod')} description={t('adjustReportFilters')} />;

  const summaryPanel = !data ? null : (
    <section className="report-domain">
      <div className="report-kpis">
        <KpiCard
          label={t('revenue')}
          value={formatMoney(data.salesSummary.revenue, currency)}
          trend={
            trend === null ? undefined : (
              <span className="ops-status">
                {trend >= 0 ? '+' : ''}
                {trend.toFixed(1)}%
              </span>
            )
          }
          context={t('validatedSalesOnly')}
        />
        <KpiCard
          label={t('invoiceCount')}
          value={data.salesSummary.invoices}
          context={t('validatedSalesOnly')}
        />
        <KpiCard
          label={t('averageTicket')}
          value={formatMoney(data.salesSummary.averageTicket, currency)}
          context={t('averageBasketDefinition')}
        />
        <KpiCard
          label={t('outOfStock')}
          value={data.stockSummary.outOfStockProducts}
          context={t('currentState')}
        />
      </div>
      <div className="report-two-column">
        <section className="report-card">
          <h2>{t('topProductsByRevenue')}</h2>
          {productRanking.length ? (
            <>
              <RankingChart rows={productRanking.slice(0, 5)} currency={currency} />
              <ol className="report-accessible-list">
                {productRanking.slice(0, 5).map((row) => (
                  <li key={row.label}>
                    {row.label}: {formatMoney(row.revenue, currency)}
                  </li>
                ))}
              </ol>
            </>
          ) : (
            noData
          )}
        </section>
        <section className="report-card">
          <h2>{t('attentionRequired')}</h2>
          {data.stockSummary.outOfStockProducts || data.stockSummary.lowStockProducts ? (
            <ul className="report-attention">
              <li>
                <strong>{data.stockSummary.outOfStockProducts}</strong> {t('outOfStock')}
              </li>
              <li>
                <strong>{data.stockSummary.lowStockProducts}</strong> {t('lowStockProducts')}
              </li>
            </ul>
          ) : (
            <p>{t('noOperationalAlerts')}</p>
          )}
          <p className="ds-caption">{t('stockStateIsCurrent')}</p>
        </section>
      </div>
    </section>
  );
  const salesPanel = !data ? null : (
    <section className="report-domain">
      <div className="report-kpis">
        <KpiCard
          label={t('revenue')}
          value={formatMoney(data.salesSummary.revenue, currency)}
          context={t('validatedSalesOnly')}
        />
        <KpiCard
          label={t('transactions')}
          value={validatedInvoices.length}
          context={t('validatedSalesOnly')}
        />
        <KpiCard
          label={t('averageTicket')}
          value={formatMoney(data.salesSummary.averageTicket, currency)}
        />
        <KpiCard
          label={t('cancelledSales')}
          value={cancelledInvoices.length}
          context={t('excludedFromSalesKpis')}
        />
      </div>
      {invoices.length > 0 && <ReportBars label={t('salesByStatus')} rows={['validated', 'cancelled', 'draft'].map((status) => ({ label: statusLabel(status, t), value: invoices.filter((row) => row.status === status).length }))} />}
      {invoices.length
        ? table(
            [t('invoice'), t('date'), t('seller'), t('total'), t('status')],
            invoices.map((row) => (
              <tr key={row.id}>
                <td>{row.id}</td>
                <td>{new Date(row.invoiceDate).toLocaleString()}</td>
                <td>{row.seller}</td>
                <td>{formatMoney(row.totalAmount, currency)}</td>
                <td>{statusLabel(row.status, t)}</td>
              </tr>
            )),
          )
        : noData}
    </section>
  );
  const productsPanel = !data ? null : (
    <section className="report-domain">
      <Alert variant="info" title={t('rankingCriterion')}>
        {t('rankingByValidatedRevenue')}
      </Alert>
      <div className="report-two-column">
        <section className="report-card">
          <h2>{t('topProductsByRevenue')}</h2>
          {productRanking.length ? (
            <RankingChart rows={productRanking} currency={currency} />
          ) : (
            noData
          )}
        </section>
        <section className="report-card">
          <h2>{t('topCategories')}</h2>
          {categoryRanking.length ? (
            <><RankingChart rows={categoryRanking} currency={currency} />
              {table([t('category'), t('quantitySold'), t('revenue')], categoryRanking.map((row) => <tr key={row.label}><td>{row.label}</td><td>{row.quantity}</td><td>{formatMoney(row.revenue, currency)}</td></tr>))}
            </>
          ) : (
            noData
          )}
        </section>
      </div>
      {data.topProducts.length > 0 && <ArticleHistoryChart rows={data.topProducts} currency={currency} />}
      {data.topProducts.length
        ? table(
            [t('period'), t('product'), t('category'), t('quantitySold'), t('revenue')],
            data.topProducts.map((row: any, index: number) => (
              <tr key={`${row.period}-${row.productId}-${index}`}>
                <td>{row.period}</td>
                <td>{row.product}</td>
                <td>{row.category}</td>
                <td>{row.quantity}</td>
                <td>{formatMoney(row.revenue, currency)}</td>
              </tr>
            )),
          )
        : noData}
      {data.priceEvolution.length > 0 && (
        <section className="report-card">
          <h2>{t('priceEvolutionReport')}</h2>
          <PriceLineChart
            rows={data.priceEvolution.map((row: any) => ({ ...row, productName: row.product }))}
          />
        </section>
      )}
    </section>
  );
  const stockPanel = !data ? null : (
    <section className="report-domain">
      <Alert variant="info" title={t('currentState')}>
        {t('stockStateIgnoresPeriod')}
      </Alert>
      <div className="report-kpis">
        <KpiCard label={t('products')} value={data.stockSummary.products} />
        <KpiCard label={t('unitsInStock')} value={data.stockSummary.unitsInStock} />
        <KpiCard label={t('lowStockProducts')} value={data.stockSummary.lowStockProducts} />
        <KpiCard label={t('outOfStock')} value={data.stockSummary.outOfStockProducts} />
      </div>
      {movementPeriods.length ? (
        <>
          <MovementChart rows={movementPeriods} entries={t('entries')} exits={t('exits')} />
          {table(
            [t('period'), t('product'), t('movementType'), t('entries'), t('exits')],
            data.movements.map((row: any, index: number) => (
              <tr key={`${row.period}-${row.productId}-${row.reason}-${index}`}>
                <td>{row.period}</td>
                <td>{row.product}</td>
                <td>{t(movementKey(row.reason))}</td>
                <td>{row.entries}</td>
                <td>{row.exits}</td>
              </tr>
            )),
          )}
        </>
      ) : (
        noData
      )}
    </section>
  );
  const purchasePanel = (
    <section className="report-domain">
      {supplierRanking.length > 0 && <section className="report-card">
        <h2>{t('purchasesBySupplier')}</h2>
        <p>{t('validatedPurchasesOnly')}</p>
        <RankingChart rows={supplierRanking} currency={currency} />
      </section>}
      <Alert variant="info" title={t('purchaseTotalsDefinition')}>
        {t('validatedPurchasesOnly')}
      </Alert>
      <div className="report-kpis">
        <KpiCard label={t('validatedPurchases')} value={purchase.validated.length} />
        <KpiCard
          label={t('validatedPurchaseTotal')}
          value={formatMoney(purchase.validatedTotal, currency)}
        />
        <KpiCard
          label={t('drafts')}
          value={purchase.draftCount}
          context={t('excludedFromPurchaseTotal')}
        />
        <KpiCard
          label={t('cancelled')}
          value={purchase.cancelledCount}
          context={t('excludedFromPurchaseTotal')}
        />
      </div>
      {purchase.rows.length
        ? table(
            [t('date'), t('reference'), t('supplier'), t('total'), t('status')],
            purchase.rows.map((row) => (
              <tr key={row.id}>
                <td>{new Date(row.created_at).toLocaleString()}</td>
                <td>{row.reference}</td>
                <td>{row.supplier || '—'}</td>
                <td>{formatMoney(row.total_amount, currency)}</td>
                <td>{statusLabel(row.status, t)}</td>
              </tr>
            )),
          )
        : noData}
    </section>
  );
  const teamPanel = (
    <section className="report-domain">
      <Alert variant="info" title={t('privacyMinimized')}>
        {t('teamReportPrivacyHint')}
      </Alert>
      <div className="report-kpis">
        <KpiCard label={t('attendanceSessions')} value={team.sessions} />
        <KpiCard label={t('completedSessions')} value={team.completedSessions} />
        <KpiCard
          label={t('hoursWorked')}
          value={team.hours.toLocaleString(undefined, { maximumFractionDigits: 2 })}
        />
        <KpiCard label={t('interrupted')} value={team.interruptedSessions} />
      </div>
      {attendance.length > 0 && <ReportBars label={t('attendanceByStatus')} rows={[
        { label: t('inProgress'), value: attendance.filter((row) => !row.endTime).length },
        { label: t('completedSessions'), value: attendance.filter((row) => row.endTime && !['INTERRUPTED', 'CORRECTED'].includes(row.status)).length },
        { label: t('corrected'), value: attendance.filter((row) => row.endTime && row.status === 'CORRECTED').length },
        { label: t('interrupted'), value: attendance.filter((row) => row.endTime && row.status === 'INTERRUPTED').length },
      ]} />}
      {attendance.length > 0 && <ReportBars label={t('hoursByEmployee')} rows={Object.values(attendance.reduce((totals: Record<string, { label: string; value: number }>, row: any) => {
        const key = String(row.employeeId);
        const item = totals[key] || { label: `${row.firstName || ''} ${row.lastName || ''}`.trim() || row.username, value: 0 };
        if (row.endTime) item.value += Number(row.hours || 0);
        totals[key] = item;
        return totals;
      }, {}))} />}
      {attendance.length
        ? table(
            [t('employee'), t('start'), t('end'), t('hoursWorked'), t('status')],
            attendance.map((row) => (
              <tr key={row.id}>
                <td>{`${row.firstName || ''} ${row.lastName || ''}`.trim() || row.username}</td>
                <td>{new Date(row.startTime).toLocaleString()}</td>
                <td>{row.endTime ? new Date(row.endTime).toLocaleString() : t('inProgress')}</td>
                <td>{row.endTime ? row.hours : '—'}</td>
                <td>
                  {t(statusKey(row.endTime ? row.status : 'OPEN', 'attendance'))}
                </td>
              </tr>
            )),
          )
        : noData}
    </section>
  );
  const financePanel = !data ? null : (
    <section className="report-domain">
      <Alert variant="warning" title={t('financeScope')}>
        {t('financeNotAccounting')}
      </Alert>
      <div className="report-kpis">
        <KpiCard
          label={t('validatedSalesRevenue')}
          value={formatMoney(data.salesSummary.revenue, currency)}
          context={t('validatedSalesOnly')}
        />
        <KpiCard
          label={t('validatedPurchaseTotal')}
          value={formatMoney(purchase.validatedTotal, currency)}
          context={t('notProfitCalculation')}
        />
      </div>
    </section>
  );

  const tabs = [
    { id: 'summary', label: t('summary'), panel: summaryPanel },
    { id: 'sales', label: t('sales'), panel: salesPanel },
    { id: 'products', label: t('products'), panel: productsPanel },
    { id: 'stock', label: t('stock'), panel: stockPanel },
    ...(can(permissions, 'PURCHASES', 'READ')
      ? [{ id: 'purchases', label: t('purchases'), panel: purchasePanel }]
      : []),
    ...(can(permissions, 'PRESENCE', 'READ')
      ? [{ id: 'team', label: t('team'), panel: teamPanel }]
      : []),
    { id: 'finance', label: t('finances'), panel: financePanel },
  ] as Array<{ id: ReportTab; label: string; panel: React.ReactNode }>;

  return (
    <section className="reports-page ops-page" aria-labelledby="reports-title">
      <header className="ops-page__header">
        <div>
          <span className="eyebrow">{t('analytics')}</span>
          <h1 id="reports-title" tabIndex={-1}>
            {t('structuredReports')}
          </h1>
          <p>{t('reportsPurpose')}</p>
          <p className="ds-caption">{t('period')}: {appliedFilters.from} — {appliedFilters.to} · {currency}</p>
        </div>
        <div className="report-actions">
          <Button variant="secondary" onClick={() => void exportPdf()}>
            {t('exportPdf')}
          </Button>
          <Button onClick={() => { setEmail((current) => ({ ...current, text: t('reportEmailBody', { date: new Date().toLocaleString() }) })); setEmailOpen(true); }}>{t('emailReport')}</Button>
        </div>
      </header>
      <form
        className="report-filter-bar"
        onSubmit={(event) => {
          event.preventDefault();
          void load(filters);
        }}
      >
        <label>
          {t('from')}
          <input
            type="date"
            value={filters.from}
            onChange={(event) => setFilters({ ...filters, from: event.target.value })}
          />
        </label>
        <label>
          {t('to')}
          <input
            type="date"
            value={filters.to}
            onChange={(event) => setFilters({ ...filters, to: event.target.value })}
          />
        </label>
        <label>
          {t('period')}
          <select
            value={filters.grain}
            onChange={(event) =>
              setFilters({ ...filters, grain: event.target.value as ReportFilters['grain'] })
            }
          >
            <option value="day">{t('daily')}</option>
            <option value="week">{t('weekly')}</option>
            <option value="month">{t('monthly')}</option>
          </select>
        </label>
        {(tab === 'sales' || tab === 'products' || tab === 'stock' || tab === 'purchases') && (
          <>
            {tab === 'purchases' && (
              <label>
                {t('supplier')}
                <select
                  value={filters.supplierId || ''}
                  onChange={(event) =>
                    setFilters({ ...filters, supplierId: Number(event.target.value) || undefined })
                  }
                >
                  <option value="">{t('allSuppliers')}</option>
                  {suppliers.map((row) => (
                    <option key={row.id} value={row.id}>
                      {row.name}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <label>
              {t('product')}
              <select
                value={filters.productId || ''}
                onChange={(event) =>
                  setFilters({ ...filters, productId: Number(event.target.value) || undefined })
                }
              >
                <option value="">{t('allProducts')}</option>
                {products.map((row) => (
                  <option key={row.id} value={row.id}>
                    {row.name}
                  </option>
                ))}
              </select>
            </label>
            {tab !== 'purchases' && (
              <label>
                {t('category')}
                <select
                  value={filters.category || ''}
                  onChange={(event) =>
                    setFilters({ ...filters, category: event.target.value || undefined })
                  }
                >
                  <option value="">{t('allCategories')}</option>
                  {categories.map((value) => (
                    <option key={value}>{value}</option>
                  ))}
                </select>
              </label>
            )}
          </>
        )}
        {tab === 'team' && (
          <>
            <label>
              {t('employee')}
              <select
                value={filters.employeeId || ''}
                onChange={(event) =>
                  setFilters({ ...filters, employeeId: Number(event.target.value) || undefined })
                }
              >
                <option value="">{t('allEmployees')}</option>
                {attendancePeople.map(([id, label]) => (
                  <option key={id} value={id}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              {t('status')}
              <select
                value={filters.attendanceState || ''}
                onChange={(event) =>
                  setFilters({ ...filters, attendanceState: event.target.value || undefined })
                }
              >
                <option value="">{t('allStatuses')}</option>
                <option value="OPEN">{t('inProgress')}</option>
                <option value="CLOSED">{t('validated')}</option>
                <option value="CORRECTED">{t('corrected')}</option>
                <option value="INTERRUPTED">{t('interrupted')}</option>
              </select>
            </label>
          </>
        )}
        <Button type="submit">{t('applyFilters')}</Button>
        <Button type="button" variant="secondary" onClick={() => applyPreset(7)}>
          {t('lastSevenDays')}
        </Button>
        <Button type="button" variant="secondary" onClick={() => applyPreset(30)}>
          {t('lastThirtyDays')}
        </Button>
        <Button
          type="button"
          variant="ghost"
          onClick={() => {
            const next = defaultReportFilters();
            setFilters(next);
            void load(next);
          }}
        >
          {t('resetFilters')}
        </Button>
      </form>
      {loading ? (
        <div className="report-loading" aria-live="polite">
          <Skeleton label={t('loading')} />
          <Skeleton label={t('loading')} />
          <Skeleton label={t('loading')} />
        </div>
      ) : error === 'forbidden' ? (
        <ErrorState title={t('accessDenied')} description={t('reportForbiddenHint')} />
      ) : error ? (
        <ErrorState
          title={t('reportLoadError')}
          description={t('retryReportHint')}
          action={<Button onClick={() => void load(filters)}>{t('retry')}</Button>}
        />
      ) : (
        <Tabs
          ariaLabel={t('reportTypes')}
          active={tab}
          onChange={(id) => {
            const nextTab = id as ReportTab;
            const nextFilters = contextualReportFilters(nextTab, filters);
            setTab(nextTab);
            setFilters(nextFilters);
            void load(nextFilters, nextTab);
            requestAnimationFrame(() => document.getElementById('reports-title')?.focus());
          }}
          tabs={tabs}
        />
      )}
      {emailOpen && (
        <ModalBackdrop onClose={() => setEmailOpen(false)}>
          <form className="dialog" onSubmit={sendEmail}>
            <h2>{t('emailReport')}</h2>
            <label>
              {t('recipientEmail')}
              <input
                type="email"
                required
                value={email.to}
                onChange={(event) => setEmail({ ...email, to: event.target.value })}
              />
            </label>
            <label>
              {t('subject')}
              <input
                required
                value={email.subject}
                onChange={(event) => setEmail({ ...email, subject: event.target.value })}
              />
            </label>
            <label>
              {t('message')}
              <textarea
                rows={4}
                value={email.text}
                onChange={(event) => setEmail({ ...email, text: event.target.value })}
              />
            </label>
            <div className="dialog-actions">
              <Button type="button" variant="secondary" onClick={() => setEmailOpen(false)}>
                {t('cancel')}
              </Button>
              <Button type="submit">{t('send')}</Button>
            </div>
          </form>
        </ModalBackdrop>
      )}
    </section>
  );
}
