import { useTranslation } from 'react-i18next';
import type { Product } from '../../types';
import { formatMoney } from '../../utils/formatters';
export function ProductCard({
  product,
  onAdd,
}: {
  product: Product;
  onAdd: (product: Product) => void;
}) {
  const { t } = useTranslation();
  const low = product.stockQuantity <= product.minStockThreshold;
  return (
    <article className="product" onClick={() => product.stockQuantity > 0 && onAdd(product)}>
      <span className="tag">{product.category}</span>
      <h3>{product.name}</h3>
      <p>{product.description || product.hashtag || '—'}</p>
      <div className="product-meta">
        <strong>{formatMoney(product.price)}</strong>
        <small className={low ? 'low' : ''}>
          {t('stock')}: {product.stockQuantity}
        </small>
      </div>
      <button disabled={!product.stockQuantity}>{t('add')}</button>
    </article>
  );
}
