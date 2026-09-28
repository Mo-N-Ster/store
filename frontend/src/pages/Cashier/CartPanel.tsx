import { Banknote, ShoppingCart, Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button, IconButton, NumberInput, Select } from '../../design-system';
import type { CartLine } from '../../types';
import { formatMoney } from '../../utils/formatters';

type Props = {
  lines: CartLine[]; subtotal: number; total: number; discount: number;
  discountMode: 'fixed' | 'percent'; setDiscountMode: (mode: 'fixed' | 'percent') => void;
  setDiscount: (value: number) => void; updateQuantity: (id: number, value: number) => void;
  remove: (id: number) => void; onCheckout: () => void; validating: boolean;
  currency: string; discountsEnabled: boolean; checkoutDisabledReason?: string;
};

export function CartPanel({ lines, subtotal, total, discount, discountMode, setDiscountMode, setDiscount, updateQuantity, remove, onCheckout, validating, currency, discountsEnabled, checkoutDisabledReason }: Props) {
  const { t } = useTranslation();
  const itemCount = lines.reduce((sum, line) => sum + line.quantity, 0);
  return (
    <aside className="pos-cart" aria-label={t('cart')} aria-busy={validating || undefined}>
      <header className="pos-cart__header"><div><span className="eyebrow">{t('currentOrder')}</span><h2>{t('cart')}</h2></div><span className="counter" aria-label={t('cartItemCount', { count: itemCount })}>{itemCount}</span></header>
      <div className="pos-cart__lines" aria-live="polite">
        {!lines.length ? <div className="pos-cart__empty"><ShoppingCart aria-hidden="true" /><strong>{t('emptyCart')}</strong><span>{t('emptyCartHint')}</span></div> : lines.map((line) => (
          <article className="pos-cart-line" key={line.product.id}>
            <div className="pos-cart-line__title"><strong>{line.product.name}</strong><span>{formatMoney(line.product.price, currency)} · {t('unitPrice')}</span></div>
            <div className="pos-cart-line__controls">
              <span>{t('quantity')}</span>
              <input className="pos-cart-line__quantity" type="number" inputMode="numeric" min="0" max={line.product.stockQuantity} aria-label={t('productQuantity', { product: line.product.name })} value={line.quantity} disabled={validating} onFocus={(event) => event.currentTarget.select()} onChange={(event) => updateQuantity(line.product.id, Number(event.target.value))} />
              <IconButton className="pos-cart-line__remove" icon={Trash2} label={t('removeProductFromCart', { product: line.product.name })} disabled={validating} onClick={() => remove(line.product.id)} />
            </div>
            <strong className="pos-cart-line__total">{formatMoney(line.quantity * line.product.price, currency)}</strong>
          </article>
        ))}
      </div>
      <div className="pos-cart__footer">
        {discountsEnabled && lines.length > 0 && <div className="pos-cart__discount"><Select label={t('discountType')} value={discountMode} disabled={validating} onChange={(event) => setDiscountMode(event.target.value as 'fixed' | 'percent')}><option value="fixed">{currency}</option><option value="percent">%</option></Select><NumberInput step="0.01" label={t('discount')} min="0" max={discountMode === 'percent' ? 100 : subtotal} value={discount} disabled={validating} onChange={(event) => setDiscount(Number(event.target.value))} /></div>}
        <div className="pos-cart__total"><span>{t('total')}</span><strong>{formatMoney(total, currency)}</strong></div>
        {checkoutDisabledReason && <p className="pos-cart__reason" role="status">{checkoutDisabledReason}</p>}
        <Button className="pos-cart__checkout" size="lg" icon={Banknote} disabled={Boolean(checkoutDisabledReason) || validating} onClick={(event) => { const inputs = event.currentTarget.closest('.pos-cart')?.querySelectorAll<HTMLInputElement>('input') ?? []; if (Array.from(inputs).every((input) => input.reportValidity())) onCheckout(); }}>{t('checkout')}</Button>
      </div>
    </aside>
  );
}
