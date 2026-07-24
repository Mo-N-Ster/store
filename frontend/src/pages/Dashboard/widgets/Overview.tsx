import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { dashboardService } from '../../../services/dashboardService';
import { productService } from '../../../services/productService';
import { saleService } from '../../../services/saleService';
import type { Product } from '../../../types';
import { formatMoney, todayIso } from '../../../utils/formatters';
import { useStorePreferences } from '../../../hooks/useStorePreferences';
import { SubTabs } from '../../../components/UI/SubTabs';
import { ProductDetailsDialog } from '../products/ProductDetailsDialog';
import { InvoicePreview } from '../../Cashier/InvoicePreview';

export function Overview() {
  const { t } = useTranslation();
  const preferences = useStorePreferences();
  const [summary, setSummary] = useState<any>({});
  const [products, setProducts] = useState<Product[]>([]);
  const [alerts, setAlerts] = useState<any[]>([]);
  const [sales, setSales] = useState<any[]>([]);
  const [active, setActive] = useState('products');
  const [search, setSearch] = useState('');
  const [productDetail, setProductDetail] = useState<Product | null>(null);
  const [invoiceDetail, setInvoiceDetail] = useState<any>(null);

  useEffect(() => {
    const today = todayIso();
    void Promise.all([
      dashboardService.get(),
      productService.list(),
      dashboardService.notifications(),
      saleService.list({ from: today, to: today, search: '' }),
    ]).then(([dashboard, productRows, notificationRows, saleRows]) => {
      setSummary(dashboard);
      setProducts((productRows as Product[]).sort((a, b) => a.name.localeCompare(b.name)));
      setAlerts(notificationRows);
      setSales(saleRows);
    });
  }, []);

  const lowStock = useMemo(
    () =>
      products
        .filter((product) => product.stockQuantity <= product.minStockThreshold)
        .map((product) => ({
          ...product,
          reachedAt: alerts.find(
            (alert) => alert.product_id === product.id && alert.type === 'stock_alert',
          )?.created_at,
        })),
    [alerts, products],
  );
  const visibleProducts = products.filter((product) =>
    [product.name, product.category, product.hashtag]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(search.trim().toLowerCase()),
  );
  const tabs = [
    { id: 'products', label: t('products'), count: products.length },
    { id: 'lowStock', label: t('lowStock'), count: lowStock.length },
    { id: 'salesToday', label: t('salesToday'), count: sales.length },
  ];

  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">{t('overview')}</span>
          <h1>{t('home')}</h1>
        </div>
        <p>{t('overviewDescription')}</p>
      </div>
      <div className="widgets overview-widgets">
        <button
          className={active === 'products' ? 'active' : ''}
          onClick={() => setActive('products')}
        >
          <span className="widget-icon">📦</span>
          <span>{t('products')}</span>
          <b>{summary.products || 0}</b>
        </button>
        <button
          className={active === 'lowStock' ? 'active' : ''}
          onClick={() => setActive('lowStock')}
        >
          <span className="widget-icon">⚠</span>
          <span>{t('lowStock')}</span>
          <b>{summary.lowStock || 0}</b>
        </button>
        <button
          className={active === 'salesToday' ? 'active' : ''}
          onClick={() => setActive('salesToday')}
        >
          <span className="widget-icon">🧾</span>
          <span>{t('salesToday')}</span>
          <b>{summary.salesToday || 0}</b>
          <small>{formatMoney(summary.revenueToday, preferences.currency)}</small>
        </button>
        <article>
          <span className="widget-icon">◫</span>
          <span>{t('revenueMonth')}</span>
          <b>{formatMoney(summary.revenueMonth, preferences.currency)}</b>
        </article>
      </div>
      <SubTabs tabs={tabs} active={active} onChange={setActive} ariaLabel={t('homeSections')} />

      {active === 'products' && (
        <>
          <div className="filters">
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t('searchProducts')}
            />
          </div>
          <div className="table-shell">
            <table>
              <thead>
                <tr>
                  <th>{t('product')}</th>
                  <th>{t('category')}</th>
                  <th>{t('currentStock')}</th>
                  <th>{t('unitPrice')}</th>
                </tr>
              </thead>
              <tbody>
                {visibleProducts.map((product) => (
                  <tr
                    className="clickable-row"
                    key={product.id}
                    onClick={() => setProductDetail(product)}
                  >
                    <td>
                      <b>{product.name}</b>
                      <small>{product.hashtag}</small>
                    </td>
                    <td>{product.category}</td>
                    <td>{product.stockQuantity}</td>
                    <td>{formatMoney(product.price, preferences.currency)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
      {active === 'lowStock' && (
        <div className="table-shell">
          <table>
            <thead>
              <tr>
                <th>{t('product')}</th>
                <th>{t('currentStock')}</th>
                <th>{t('minimumThreshold')}</th>
                <th>{t('thresholdReachedAt')}</th>
              </tr>
            </thead>
            <tbody>
              {lowStock.map((product) => (
                <tr
                  className="clickable-row"
                  key={product.id}
                  onClick={() => setProductDetail(product)}
                >
                  <td>{product.name}</td>
                  <td>
                    <span className="stock-pill low-stock">{product.stockQuantity}</span>
                  </td>
                  <td>{product.minStockThreshold}</td>
                  <td>{product.reachedAt ? new Date(product.reachedAt).toLocaleString() : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {active === 'salesToday' && (
        <div className="table-shell">
          <table>
            <thead>
              <tr>
                <th>{t('invoice')}</th>
                <th>{t('date')}</th>
                <th>{t('seller')}</th>
                <th>{t('total')}</th>
              </tr>
            </thead>
            <tbody>
              {sales.map((sale) => (
                <tr
                  className="clickable-row"
                  key={sale.id}
                  onClick={() => saleService.detail(sale.id).then(setInvoiceDetail)}
                >
                  <td>{sale.id}</td>
                  <td>{new Date(sale.invoiceDate).toLocaleString()}</td>
                  <td>{sale.seller}</td>
                  <td>{formatMoney(sale.totalAmount, preferences.currency)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {productDetail && (
        <ProductDetailsDialog
          product={productDetail}
          currency={preferences.currency}
          onClose={() => setProductDetail(null)}
        />
      )}
      {invoiceDetail && (
        <InvoicePreview
          data={invoiceDetail}
          close={() => setInvoiceDetail(null)}
          currency={preferences.currency}
          discountsEnabled={preferences.discountsEnabled}
        />
      )}
    </>
  );
}
