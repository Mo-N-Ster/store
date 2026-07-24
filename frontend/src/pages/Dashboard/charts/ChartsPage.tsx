import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { productService } from '../../../services/productService';
import { reportService, type ReportFilters } from '../../../services/reportService';
import type { Product } from '../../../types';
import { formatMoney } from '../../../utils/formatters';
import { useStorePreferences } from '../../../hooks/useStorePreferences';
import { MovementChart } from './MovementChart';
import { PriceLineChart } from './PriceLineChart';
import { RankingChart } from './RankingChart';

const iso = (date: Date) => date.toISOString().slice(0, 10);
const initialFilters = (): ReportFilters => {
  const today = new Date();
  return {
    from: iso(new Date(today.getFullYear(), today.getMonth(), 1)),
    to: iso(today),
    grain: 'day',
  };
};

export function ChartsPage({ notify }: { notify: (message: string) => void }) {
  const { t } = useTranslation();
  const { currency } = useStorePreferences();
  const [filters, setFilters] = useState(initialFilters);
  const [data, setData] = useState<any>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [emailOpen, setEmailOpen] = useState(false);
  const [email, setEmail] = useState({
    to: '',
    subject: t('reportEmailSubject'),
    text: t('reportEmailBody'),
  });

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await reportService.get(filters));
    } catch {
      notify(t('reportLoadError'));
    } finally {
      setLoading(false);
    }
  }, [filters, notify, t]);

  useEffect(() => {
    productService.list().then((rows: Product[]) => setProducts(rows));
  }, []);
  useEffect(() => void load(), []); // Filters are applied explicitly.

  const categories = useMemo(
    () => [...new Set(products.map((product) => product.category))].sort(),
    [products],
  );
  const movementPeriods = useMemo(() => {
    const map = new Map<string, { period: string; entries: number; exits: number }>();
    for (const row of data?.movements || []) {
      const item = map.get(row.period) || { period: row.period, entries: 0, exits: 0 };
      item.entries += row.entries;
      item.exits += row.exits;
      map.set(row.period, item);
    }
    return [...map.values()].sort((a, b) => a.period.localeCompare(b.period));
  }, [data]);
  const productRanking = useMemo(
    () =>
      [
        ...(data?.topProducts || [])
          .reduce((map: Map<number, any>, row: any) => {
            const item = map.get(row.productId) || {
              label: row.product,
              quantity: 0,
              revenue: 0,
            };
            item.quantity += row.quantity;
            item.revenue += row.revenue;
            return map.set(row.productId, item);
          }, new Map())
          .values(),
      ].sort((a: any, b: any) => b.revenue - a.revenue),
    [data],
  );
  const categoryRanking = useMemo(
    () =>
      [
        ...(data?.topCategories || [])
          .reduce((map: Map<string, any>, row: any) => {
            const item = map.get(row.category) || {
              label: row.category,
              quantity: 0,
              revenue: 0,
            };
            item.quantity += row.quantity;
            item.revenue += row.revenue;
            return map.set(row.category, item);
          }, new Map())
          .values(),
      ].sort((a: any, b: any) => b.revenue - a.revenue),
    [data],
  );
  const filename = `STORE-report-${filters.from}-${filters.to}.pdf`;

  const printable = async (operation: () => Promise<unknown>) => {
    document.body.classList.add('report-print-mode');
    await new Promise<void>((resolve) =>
      requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
    );
    try {
      return await operation();
    } finally {
      document.body.classList.remove('report-print-mode');
    }
  };
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
      await printable(() => reportService.emailPdf({ ...email, filename }));
      setEmailOpen(false);
      notify(t('reportEmailed'));
    } catch {
      notify(t('reportEmailError'));
    }
  };

  return (
    <div className="reports-page">
      <div className="page-heading report-header">
        <div>
          <span className="eyebrow">{t('analytics')}</span>
          <h1>{t('structuredReports')}</h1>
        </div>
        <div className="report-actions">
          <button className="secondary" onClick={exportPdf}>
            ↓ {t('exportPdf')}
          </button>
          <button onClick={() => setEmailOpen(true)}>✉ {t('emailReport')}</button>
        </div>
      </div>

      <form
        className="report-filters"
        onSubmit={(event) => {
          event.preventDefault();
          void load();
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
        <label>
          {t('product')}
          <select
            value={filters.productId || ''}
            onChange={(event) =>
              setFilters({ ...filters, productId: Number(event.target.value) || undefined })
            }
          >
            <option value="">{t('allProducts')}</option>
            {products.map((product) => (
              <option key={product.id} value={product.id}>
                {product.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t('category')}
          <select
            value={filters.category || ''}
            onChange={(event) =>
              setFilters({ ...filters, category: event.target.value || undefined })
            }
          >
            <option value="">{t('allCategories')}</option>
            {categories.map((category) => (
              <option key={category}>{category}</option>
            ))}
          </select>
        </label>
        <button type="submit">{t('applyFilters')}</button>
      </form>

      {loading ? (
        <div className="empty-state">{t('loading')}</div>
      ) : !data ? null : (
        <>
          <div className="report-summary">
            <article>
              <span>{t('entries')}</span>
              <strong>{data.summary.entries}</strong>
            </article>
            <article>
              <span>{t('exits')}</span>
              <strong>{data.summary.exits}</strong>
            </article>
            <article>
              <span>{t('movements')}</span>
              <strong>{data.summary.movements}</strong>
            </article>
            <article>
              <span>{t('movementValue')}</span>
              <strong>{formatMoney(data.summary.movementValue, currency)}</strong>
            </article>
          </div>

          <section className="report-section">
            <header>
              <span>01</span>
              <div>
                <h2>{t('movementReport')}</h2>
                <p>{t('movementReportHint')}</p>
              </div>
            </header>
            {movementPeriods.length ? (
              <MovementChart rows={movementPeriods} entries={t('entries')} exits={t('exits')} />
            ) : (
              <p className="empty-state">{t('noReportData')}</p>
            )}
            <div className="table-shell">
              <table>
                <thead>
                  <tr>
                    <th>{t('period')}</th>
                    <th>{t('product')}</th>
                    <th>{t('category')}</th>
                    <th>{t('movementType')}</th>
                    <th>{t('entries')}</th>
                    <th>{t('exits')}</th>
                    <th>{t('unitPrice')}</th>
                    <th>{t('entryValue')}</th>
                    <th>{t('exitValue')}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.movements.map((row: any, index: number) => (
                    <tr key={`${row.period}-${row.productId}-${row.reason}-${index}`}>
                      <td>{row.period}</td>
                      <td>{row.product}</td>
                      <td>{row.category}</td>
                      <td>{t(`movement_${row.reason}`, { defaultValue: row.reason })}</td>
                      <td>{row.entries}</td>
                      <td>{row.exits}</td>
                      <td>{formatMoney(row.unitPrice, currency)}</td>
                      <td>{formatMoney(row.entryValue, currency)}</td>
                      <td>{formatMoney(row.exitValue, currency)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <section className="report-section">
            <header>
              <span>02</span>
              <div>
                <h2>{t('bestSellingReport')}</h2>
                <p>{t('bestSellingHint')}</p>
              </div>
            </header>
            <div className="report-rankings">
              <div>
                <h3>{t('products')}</h3>
                <RankingChart currency={currency} rows={productRanking} />
              </div>
              <div>
                <h3>{t('categories')}</h3>
                <RankingChart currency={currency} rows={categoryRanking} />
              </div>
            </div>
            <div className="table-shell">
              <table>
                <thead>
                  <tr>
                    <th>{t('period')}</th>
                    <th>{t('product')}</th>
                    <th>{t('category')}</th>
                    <th>{t('quantitySold')}</th>
                    <th>{t('revenue')}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.topProducts.map((row: any, index: number) => (
                    <tr key={`${row.period}-${row.productId}-${index}`}>
                      <td>{row.period}</td>
                      <td>{row.product}</td>
                      <td>{row.category}</td>
                      <td>{row.quantity}</td>
                      <td>{formatMoney(row.revenue, currency)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <section className="report-section">
            <header>
              <span>03</span>
              <div>
                <h2>{t('priceEvolutionReport')}</h2>
                <p>{t('priceEvolutionHint')}</p>
              </div>
            </header>
            {data.priceEvolution.length ? (
              <PriceLineChart
                rows={data.priceEvolution.map((row: any) => ({ ...row, productName: row.product }))}
              />
            ) : (
              <p className="empty-state">{t('noReportData')}</p>
            )}
            <div className="table-shell">
              <table>
                <thead>
                  <tr>
                    <th>{t('period')}</th>
                    <th>{t('product')}</th>
                    <th>{t('averagePrice')}</th>
                    <th>{t('minimumPrice')}</th>
                    <th>{t('maximumPrice')}</th>
                    <th>{t('observations')}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.priceEvolution.map((row: any) => (
                    <tr key={`${row.period}-${row.productId}`}>
                      <td>{row.period}</td>
                      <td>{row.product}</td>
                      <td>{formatMoney(row.averagePrice, currency)}</td>
                      <td>{formatMoney(row.minimumPrice, currency)}</td>
                      <td>{formatMoney(row.maximumPrice, currency)}</td>
                      <td>{row.observations}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}

      {emailOpen && (
        <div
          className="modal critical-modal"
          onMouseDown={(event) => event.target === event.currentTarget && setEmailOpen(false)}
        >
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
              <button type="button" className="secondary" onClick={() => setEmailOpen(false)}>
                {t('cancel')}
              </button>
              <button type="submit">{t('send')}</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
