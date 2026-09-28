import { useTranslation } from 'react-i18next';
import { Modal } from '../../../design-system';
import type { Product } from '../../../types';
import { formatMoney } from '../../../utils/formatters';
import { productStockStatus } from '../../Cashier/posModel';
import { ArticleImage } from '../../../components/common/ArticleImage';

export function ProductDetailsDialog({ product, currency, onClose }: { product: Product; currency: string; onClose: () => void }) {
  const { t } = useTranslation();
  const status = productStockStatus(product);
  const fields = [[t('name'), product.name], [t('category'), product.category], [t('hashtag'), product.hashtag || '—'], [t('description'), product.description || '—'], [t('unitPrice'), formatMoney(product.price, currency)], [t('currentStock'), product.stockQuantity], [t('minimumThreshold'), product.minStockThreshold]];
  return <Modal title={product.name} description={t('productSheet')} closeLabel={t('close')} onClose={onClose}>
    <ArticleImage imageRef={product.imageRef} alt={t('articleImageAlt', { article: product.name })} className="article-image--detail" />
    <span className={`ops-status ops-status--${status}`}>{t(status === 'out' ? 'stockOut' : status === 'low' ? 'stockLow' : 'stockAvailable')}</span>
    <dl className="ops-detail-list">{fields.map(([label, value]) => <div key={String(label)}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
  </Modal>;
}
