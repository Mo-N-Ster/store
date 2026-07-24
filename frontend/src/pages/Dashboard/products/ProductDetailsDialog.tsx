import { useTranslation } from 'react-i18next';
import type { Product } from '../../../types';
import { formatMoney } from '../../../utils/formatters';

export function ProductDetailsDialog({
  product,
  currency,
  onClose,
}: {
  product: Product;
  currency: string;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const fields = [
    [t('name'), product.name],
    [t('category'), product.category],
    [t('hashtag'), product.hashtag || '—'],
    [t('description'), product.description || '—'],
    [t('unitPrice'), formatMoney(product.price, currency)],
    [t('currentStock'), product.stockQuantity],
    [t('minimumThreshold'), product.minStockThreshold],
  ];
  return (
    <div className="modal" onMouseDown={onClose}>
      <section className="dialog product-details" onMouseDown={(event) => event.stopPropagation()}>
        <header>
          <div>
            <span className="eyebrow">{t('productSheet')}</span>
            <h2>{product.name}</h2>
          </div>
          <button className="ghost icon-button" onClick={onClose} aria-label={t('close')}>
            ×
          </button>
        </header>
        <dl>
          {fields.map(([label, value]) => (
            <div key={String(label)}>
              <dt>{label}</dt>
              <dd>{value}</dd>
            </div>
          ))}
        </dl>
      </section>
    </div>
  );
}
