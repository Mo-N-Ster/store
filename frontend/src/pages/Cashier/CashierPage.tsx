import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Product, User } from '../../types';
import { saleService } from '../../services/saleService';
import { todayIso } from '../../utils/formatters';
import { useCart } from '../../hooks/useCart';
import { useProducts } from '../../hooks/useProducts';
import { ProductGrid } from './ProductGrid';
import { CartPanel } from './CartPanel';
import { InvoicePreview } from './InvoicePreview';
import { useStorePreferences } from '../../hooks/useStorePreferences';
export function CashierPage({ user, notify }: { user: User; notify: (message: string) => void }) {
  const { t } = useTranslation();
  const preferences = useStorePreferences();
  const [invoices, setInvoices] = useState<any[]>([]);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [discount, setDiscount] = useState(0);
  const [discountMode, setDiscountMode] = useState<'fixed' | 'percent'>('fixed');
  const [receipt, setReceipt] = useState<any>(null);
  const { products, loading, reload } = useProducts(search, category);
  const cart = useCart();
  const loadSide = () => saleService.list({ from: todayIso() }).then(setInvoices);
  useEffect(() => {
    void loadSide();
  }, []);
  const add = (product: Product, quantity: number) => {
    cart.add(product, quantity);
    notify(`${product.name} ✓`);
  };
  const validate = async () => {
    try {
      const result = await saleService.create({
        employeeId: user.id,
        lines: cart.lines.map((line) => ({ productId: line.product.id, quantity: line.quantity })),
        discount: preferences.discountsEnabled
          ? discountMode === 'percent'
            ? (cart.subtotal * discount) / 100
            : discount
          : 0,
      });
      setReceipt(result);
      cart.clear();
      setDiscount(0);
      await Promise.all([reload(), loadSide()]);
    } catch (error: any) {
      notify(error.message?.includes('STOCK') ? t('insufficientStock') : t('saleFailed'));
    }
  };
  const categories = [...new Set(products.map((product) => product.category))];
  return (
    <main className="pos">
      <aside className="pos-sidebar">
        <div className="panel-title">
          <div>
            <span className="eyebrow">{t('pointOfSale')}</span>
            <h2>{t('catalog')}</h2>
          </div>
        </div>
        <input
          placeholder={t('search')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <select value={category} onChange={(e) => setCategory(e.target.value)}>
          <option value="">{t('all')}</option>
          {categories.map((value) => (
            <option key={value}>{value}</option>
          ))}
        </select>
        <h3>{t('salesToday')}</h3>
        <div className="history">
          {invoices.map((invoice) => (
            <button
              key={invoice.id}
              onClick={() => saleService.detail(invoice.id).then(setReceipt)}
            >
              <span>{invoice.id}</span>
              <small>{new Date(invoice.invoiceDate).toLocaleTimeString()}</small>
            </button>
          ))}
        </div>
      </aside>
      <ProductGrid products={products} loading={loading} onAdd={add} />
      <CartPanel
        lines={cart.lines}
        subtotal={cart.subtotal}
        discount={discount}
        discountMode={discountMode}
        setDiscountMode={setDiscountMode}
        setDiscount={setDiscount}
        updateQuantity={cart.updateQuantity}
        toggle={cart.toggle}
        selectAll={cart.selectAll}
        removeSelected={cart.removeSelected}
        validate={validate}
        currency={preferences.currency}
        discountsEnabled={preferences.discountsEnabled}
      />
      {receipt && (
        <InvoicePreview
          data={receipt}
          close={() => setReceipt(null)}
          currency={preferences.currency}
          discountsEnabled={preferences.discountsEnabled}
        />
      )}
    </main>
  );
}
