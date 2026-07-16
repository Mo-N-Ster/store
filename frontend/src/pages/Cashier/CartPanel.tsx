import { useTranslation } from 'react-i18next';
import type { CartLine } from '../../types';
import { formatMoney } from '../../utils/formatters';
type Props = {
  lines: CartLine[];
  subtotal: number;
  discount: number;
  discountMode: 'fixed' | 'percent';
  setDiscountMode: (mode: 'fixed' | 'percent') => void;
  setDiscount: (n: number) => void;
  updateQuantity: (id: number, n: number) => void;
  toggle: (id: number, v: boolean) => void;
  selectAll: (selected: boolean) => void;
  removeSelected: () => void;
  validate: () => void;
};
export function CartPanel({
  lines,
  subtotal,
  discount,
  discountMode,
  setDiscountMode,
  setDiscount,
  updateQuantity,
  toggle,
  selectAll,
  removeSelected,
  validate,
}: Props) {
  const { t } = useTranslation();
  return (
    <aside className="cart">
      <div className="panel-title">
        <div>
          <span className="eyebrow">Commande actuelle</span>
          <h2>{t('cart')}</h2>
        </div>
        <span className="counter">{lines.length}</span>
      </div>
      {!lines.length ? (
        <div className="empty-state">
          <span>🛒</span>
          <p>{t('emptyCart')}</p>
        </div>
      ) : (
        lines.map((line) => (
          <div className="cart-line" key={line.product.id}>
            <input
              type="checkbox"
              checked={line.selected}
              onChange={(e) => toggle(line.product.id, e.target.checked)}
            />
            <span>{line.product.name}</span>
            <input
              type="number"
              min="1"
              max={line.product.stockQuantity}
              value={line.quantity}
              onChange={(e) => updateQuantity(line.product.id, Number(e.target.value))}
            />
            <b>{formatMoney(line.quantity * line.product.price)}</b>
          </div>
        ))
      )}
      {!!lines.length && (
        <label className="select-all">
          <input
            type="checkbox"
            checked={lines.every((line) => line.selected)}
            onChange={(e) => selectAll(e.target.checked)}
          />
          Tout sélectionner
        </label>
      )}
      <div className="cart-actions">
        <button
          className="danger icon-button"
          onClick={() => {
            const count = lines.filter((line) => line.selected).length;
            if (count > 3 && !confirm(`Supprimer ${count} articles du panier ?`)) return;
            removeSelected();
          }}
        >
          🗑
        </button>
        <label>
          {t('discount')}{' '}
          <select
            value={discountMode}
            onChange={(e) => setDiscountMode(e.target.value as 'fixed' | 'percent')}
          >
            <option value="fixed">€</option>
            <option value="percent">%</option>
          </select>
          <input
            type="number"
            min="0"
            max={discountMode === 'percent' ? 100 : subtotal}
            value={discount}
            onChange={(e) => setDiscount(Number(e.target.value))}
          />
        </label>
      </div>
      <div className="cart-total">
        <span>{t('total')}</span>
        <strong>
          {formatMoney(
            Math.max(
              0,
              subtotal - (discountMode === 'percent' ? (subtotal * discount) / 100 : discount),
            ),
          )}
        </strong>
      </div>
      <button className="validate-button" disabled={!lines.length} onClick={validate}>
        {t('validate')} →
      </button>
    </aside>
  );
}
