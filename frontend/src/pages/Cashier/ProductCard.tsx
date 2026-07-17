import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Product } from '../../types';
import { formatMoney } from '../../utils/formatters';
import { useStorePreferences } from '../../hooks/useStorePreferences';
export function ProductCard({
  product,
  onAdd,
}: {
  product: Product;
  onAdd: (product: Product, quantity: number) => void;
}) {
  const { t } = useTranslation();
  const { currency } = useStorePreferences();
  const [quantity, setQuantity] = useState(1);
  const low = product.stockQuantity <= product.minStockThreshold;
  return (
    <article className="product">
      <span className="tag">{product.category}</span>
      <h3>{product.name}</h3>
      <p>{product.description || product.hashtag || '—'}</p>
      <div className="product-meta">
        <strong>{formatMoney(product.price, currency)}</strong>
        <small className={low ? 'low' : ''}>
          {t('stock')}: {product.stockQuantity}
        </small>
      </div>
      <div className="quantity-picker">
        <button className="ghost" onClick={() => setQuantity((value) => Math.max(1, value - 1))}>
          −
        </button>
        <input
          aria-label={t('quantity')}
          type="number"
          min="1"
          max={product.stockQuantity}
          value={quantity}
          onChange={(e) =>
            setQuantity(Math.max(1, Math.min(product.stockQuantity, Number(e.target.value))))
          }
        />
        <button
          className="ghost"
          onClick={() => setQuantity((value) => Math.min(product.stockQuantity, value + 1))}
        >
          +
        </button>
      </div>
      <button disabled={!product.stockQuantity} onClick={() => onAdd(product, quantity)}>
        {t('add')}
      </button>
    </article>
  );
}
