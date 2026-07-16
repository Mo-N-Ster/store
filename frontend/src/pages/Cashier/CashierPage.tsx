import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Product, User } from '../../types';
import { employeeService } from '../../services/employeeService';
import { saleService } from '../../services/saleService';
import { todayIso } from '../../utils/formatters';
import { useCart } from '../../hooks/useCart';
import { useProducts } from '../../hooks/useProducts';
import { ProductGrid } from './ProductGrid';
import { CartPanel } from './CartPanel';
import { InvoicePreview } from './InvoicePreview';
import { EmployeePresence } from '../../components/common/EmployeePresence';
export function CashierPage({ user, notify }: { user: User; notify: (message: string) => void }) {
  const { t } = useTranslation();
  const [users, setUsers] = useState<User[]>([]);
  const [invoices, setInvoices] = useState<any[]>([]);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [discount, setDiscount] = useState(0);
  const [discountMode, setDiscountMode] = useState<'fixed' | 'percent'>('fixed');
  const [receipt, setReceipt] = useState<any>(null);
  const { products, loading, reload } = useProducts(search, category);
  const cart = useCart();
  const loadSide = () =>
    Promise.all([
      employeeService.list().then(setUsers),
      saleService.list({ from: todayIso() }).then(setInvoices),
    ]);
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
        discount: discountMode === 'percent' ? (cart.subtotal * discount) / 100 : discount,
      });
      setReceipt(result);
      cart.clear();
      setDiscount(0);
      await Promise.all([reload(), loadSide()]);
    } catch (error: any) {
      notify(error.message?.includes('STOCK') ? 'Stock insuffisant' : 'Vente impossible');
    }
  };
  const categories = [...new Set(products.map((product) => product.category))];
  return (
    <main className="pos">
      <aside className="pos-sidebar">
        <div className="panel-title">
          <div>
            <span className="eyebrow">Point de vente</span>
            <h2>Catalogue</h2>
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
        <h3>Équipe</h3>
        <EmployeePresence employees={users} notify={notify} />
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
      />
      {receipt && <InvoicePreview data={receipt} close={() => setReceipt(null)} />}
    </main>
  );
}
