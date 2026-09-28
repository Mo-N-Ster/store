import { useTranslation } from 'react-i18next';
import { PackagePlus } from 'lucide-react';
import type { Product } from '../../types';
import { formatMoney } from '../../utils/formatters';
import { useStorePreferences } from '../../hooks/useStorePreferences';
import { productStockStatus } from './posModel';
import { ArticleImage } from '../../components/common/ArticleImage';
export function ProductCard({
  product,
  onAdd,
}: {
  product: Product;
  onAdd: (product: Product, quantity: number) => void;
}) {
  const { t } = useTranslation();
  const { currency } = useStorePreferences();
  const status = productStockStatus(product);
  const statusLabel = t(status === 'out' ? 'stockOut' : status === 'low' ? 'stockLow' : 'stockAvailable');
  return (
    <article className={`pos-product pos-product--${status}`} aria-label={`${product.name}, ${formatMoney(product.price, currency)}, ${statusLabel}`}>
      <ArticleImage imageRef={product.imageRef} alt={t('articleImageAlt', { article: product.name })} className="pos-product__placeholder" />
      <span className="pos-product__category">{product.category}</span>
      <h3>{product.name}</h3>
      <div className="pos-product__meta">
        <strong>{formatMoney(product.price, currency)}</strong>
        <span className={`pos-stock pos-stock--${status}`}>{statusLabel} · {product.stockQuantity}</span>
      </div>
      <button className="pos-product__add" disabled={status === 'out'} onClick={() => onAdd(product, 1)}>
        <PackagePlus aria-hidden="true" />{status === 'out' ? t('unavailable') : t('addToCart')}
      </button>
    </article>
  );
}
