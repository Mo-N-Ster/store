import { History, ShoppingCart, UnlockKeyhole, X } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Drawer, Select, TextInput } from '../../design-system';
import type { Product, User } from '../../types';
import { useCart } from '../../hooks/useCart';
import { useProducts } from '../../hooks/useProducts';
import { useStorePreferences } from '../../hooks/useStorePreferences';
import { saleService } from '../../services/saleService';
import { formatMoney, todayIso } from '../../utils/formatters';
import { CartPanel } from './CartPanel';
import { CashSessionDialog } from './CashSessionDialog';
import { CheckoutDialog } from './CheckoutDialog';
import { DailyHistoryDrawer } from './DailyHistoryDrawer';
import { InvoicePreview } from './InvoicePreview';
import { ProductGrid } from './ProductGrid';
import { createSubmissionGuard, saleTotal } from './posModel';

export function CashierPage({ user, notify, clearCartRequest, onSessionSafetyChange }: { user: User; notify: (message: string) => void; clearCartRequest: number; onSessionSafetyChange: (state: { cartNonEmpty: boolean; checkoutCritical: boolean }) => void }) {
  const { t } = useTranslation();
  const preferences = useStorePreferences();
  const [invoices, setInvoices] = useState<any[]>([]);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [discount, setDiscount] = useState(0);
  const [discountMode, setDiscountMode] = useState<'fixed' | 'percent'>('fixed');
  const [receipt, setReceipt] = useState<any>(null);
  const [saleCompleted, setSaleCompleted] = useState(false);
  const [validating, setValidating] = useState(false);
  const [cashSession, setCashSession] = useState<any | undefined>(undefined);
  const [openingCash, setOpeningCash] = useState(false);
  const [closingCash, setClosingCash] = useState(false);
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [portraitCartOpen, setPortraitCartOpen] = useState(false);
  const [amountReceived, setAmountReceived] = useState('');
  const pendingSaleKey = useRef<string | null>(null);
  const submissionGuard = useRef(createSubmissionGuard());
  const searchRef = useRef<HTMLInputElement>(null);
  const { products, categories, loading, error: productsError, reload } = useProducts(search, category);
  const cart = useCart();
  const total = useMemo(() => saleTotal(cart.subtotal, discount, discountMode, preferences.discountsEnabled), [cart.subtotal, discount, discountMode, preferences.discountsEnabled]);
  const itemCount = cart.lines.reduce((sum, line) => sum + line.quantity, 0);

  useEffect(() => {
    onSessionSafetyChange({ cartNonEmpty: cart.lines.some((line) => line.quantity > 0), checkoutCritical: checkoutOpen || validating });
  }, [cart.lines, checkoutOpen, validating, onSessionSafetyChange]);
  useEffect(() => () => onSessionSafetyChange({ cartNonEmpty: false, checkoutCritical: false }), [onSessionSafetyChange]);

  const loadHistory = () => saleService.list({ from: todayIso() }).then(setInvoices);
  useEffect(() => {
    void loadHistory();
    void saleService.currentCashSession().then((session: any) => setCashSession(session || null));
  }, []);
  useEffect(() => {
    const focusSearch = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'f') {
        event.preventDefault();
        searchRef.current?.focus();
        searchRef.current?.select();
      }
    };
    window.addEventListener('keydown', focusSearch);
    return () => window.removeEventListener('keydown', focusSearch);
  }, []);

  const add = (product: Product, quantity: number) => {
    cart.add(product, quantity);
    notify(t('addedToCart', { product: product.name }));
  };

  const validate = async () => {
    if (!submissionGuard.current.acquire()) return;
    const billableLines = cart.lines.filter((line) => line.quantity > 0);
    if (!cashSession || !billableLines.length) { submissionGuard.current.release(); return; }
    const received = Number(amountReceived);
    if (!Number.isFinite(received) || received < total) { submissionGuard.current.release(); notify(t('amountReceivedInsufficient')); return; }
    setValidating(true);
    pendingSaleKey.current ||= crypto.randomUUID();
    try {
      const result = await saleService.create({
        employeeId: user.id,
        lines: billableLines.map((line) => ({ productId: line.product.id, quantity: line.quantity })),
        discount: Math.max(0, cart.subtotal - total),
        amountReceived: received,
        idempotencyKey: pendingSaleKey.current,
      });
      setReceipt(result);
      setSaleCompleted(true);
      pendingSaleKey.current = null;
      cart.clear();
      setDiscount(0);
      setAmountReceived('');
      setCheckoutOpen(false);
      setPortraitCartOpen(false);
      await Promise.all([reload(), loadHistory()]);
      setCashSession(await saleService.currentCashSession());
    } catch (error: any) {
      notify(error.message?.includes('STOCK') ? t('insufficientStock') : error.message?.includes('CASH_SESSION') ? t('cashSessionRequired') : t('saleFailedRecoverable'));
    } finally {
      setValidating(false);
      submissionGuard.current.release();
    }
  };

  useEffect(() => {
    if (clearCartRequest > 0) cart.clear();
  }, [clearCartRequest]);

  const disabledReason = !cashSession ? t('checkoutCashClosed') : !cart.lines.some((line) => line.quantity > 0) ? t('checkoutEmptyCart') : undefined;
  const cartPanel = <CartPanel lines={cart.lines} subtotal={cart.subtotal} total={total} discount={discount} discountMode={discountMode} setDiscountMode={setDiscountMode} setDiscount={setDiscount} updateQuantity={cart.updateQuantity} remove={cart.remove} onCheckout={() => setCheckoutOpen(true)} validating={validating} currency={preferences.currency} discountsEnabled={preferences.discountsEnabled} checkoutDisabledReason={disabledReason} />;

  return (
    <main className="tablet-pos">
      <section className="tablet-pos__workspace">
        <header className="tablet-pos__toolbar">
          <div className="tablet-pos__search"><TextInput ref={searchRef} type="search" label={t('searchProducts')} value={search} placeholder={t('searchByNameCategoryHashtag')} clearLabel={t('clearSearch')} onClear={search ? () => setSearch('') : undefined} onChange={(event) => setSearch(event.target.value)} /></div>
          <Select className="tablet-pos__category" label={t('category')} value={category} onChange={(event) => setCategory(event.target.value)}><option value="">{t('allCategories')}</option>{categories.map((value) => <option key={value}>{value}</option>)}</Select>
          <div className={`tablet-pos__cash tablet-pos__cash--${cashSession ? 'open' : 'closed'}`}><span>{cashSession === undefined ? t('loading') : cashSession ? t('cashSessionOpen') : t('cashSessionClosed')}</span>{cashSession && <small>{cashSession.reference}</small>}</div>
          <Button variant="secondary" icon={History} onClick={() => setHistoryOpen(true)}>{t('history')}</Button>
        </header>
        {!cashSession && cashSession !== undefined && <Alert variant="warning" title={t('cashSessionClosed')} action={<Button icon={UnlockKeyhole} onClick={() => setOpeningCash(true)}>{t('openCashSession')}</Button>}>{t('cashSessionClosedHint')}</Alert>}
        {cashSession && <div className="tablet-pos__cash-actions"><span>{t('expectedCash')}: <b>{formatMoney(Number(cashSession.expectedAmount ?? cashSession.opening_amount), preferences.currency)}</b></span><Button size="sm" variant="ghost" onClick={() => setClosingCash(true)}>{t('closeCashSession')}</Button></div>}
        <div className="tablet-pos__results"><span>{t('resultCount', { count: products.length })}</span>{(search || category) && <Button size="sm" variant="ghost" icon={X} onClick={() => { setSearch(''); setCategory(''); }}>{t('clearFilters')}</Button>}</div>
        {productsError && <Alert variant="danger" title={t('operationFailed')} action={<Button onClick={() => void reload()}>{t('retry')}</Button>}>{t('retry')}</Alert>}
        <ProductGrid products={products} loading={loading} onAdd={add} />
      </section>
      <div className="tablet-pos__desktop-cart">{cartPanel}</div>
      <div className="tablet-pos__portrait-summary"><div><span>{t('cartItemCount', { count: itemCount })}</span><strong>{formatMoney(total, preferences.currency)}</strong></div><Button size="lg" icon={ShoppingCart} onClick={() => setPortraitCartOpen(true)}>{t('viewCart')}</Button></div>
      {portraitCartOpen && <Drawer side="bottom" title={t('cart')} closeLabel={t('close')} onClose={() => setPortraitCartOpen(false)}><div className="tablet-pos__drawer-cart">{cartPanel}</div></Drawer>}
      {checkoutOpen && <CheckoutDialog total={total} received={amountReceived} setReceived={setAmountReceived} currency={preferences.currency} submitting={validating} onConfirm={() => void validate()} onClose={() => setCheckoutOpen(false)} />}
      {historyOpen && <DailyHistoryDrawer invoices={invoices} currency={preferences.currency} onClose={() => setHistoryOpen(false)} onSelect={(id) => { void saleService.detail(id).then((data) => { setSaleCompleted(false); setReceipt(data); setHistoryOpen(false); }); }} />}
      {receipt && <InvoicePreview data={receipt} saleCompleted={saleCompleted} close={() => { setReceipt(null); setSaleCompleted(false); }} currency={preferences.currency} discountsEnabled={preferences.discountsEnabled} />}
      {openingCash && <CashSessionDialog mode="open" currency={preferences.currency} onClose={() => setOpeningCash(false)} onSubmit={async (amount) => { const session = await saleService.openCashSession(amount); setCashSession(session); setOpeningCash(false); notify(t('cashSessionOpened')); }} />}
      {closingCash && cashSession && <CashSessionDialog mode="close" expectedAmount={Number(cashSession.expectedAmount || cashSession.opening_amount || 0)} currency={preferences.currency} onClose={() => setClosingCash(false)} onSubmit={async (amount) => { const result = await saleService.closeCashSession(amount); notify(t('cashDifference', { value: result.difference })); setClosingCash(false); setCashSession(null); }} />}
    </main>
  );
}
