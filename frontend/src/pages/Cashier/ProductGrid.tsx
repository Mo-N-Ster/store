import { useEffect, useState } from 'react';
import type { Product } from '../../types';
import { Spinner } from '../../components/UI/Spinner';
import { ProductCard } from './ProductCard';
import { useTranslation } from 'react-i18next';
import { PackageSearch } from 'lucide-react';
export function ProductGrid({
  products,
  loading,
  onAdd,
}: {
  products: Product[];
  loading: boolean;
  onAdd: (product: Product, quantity: number) => void;
}) {
  const { t } = useTranslation();
  const [visibleCount, setVisibleCount] = useState(60);
  useEffect(() => setVisibleCount(60), [products]);
  if (loading)
    return (
      <section className="pos-catalog ds-state" aria-label={t('productCatalog')} aria-busy="true">
        <Spinner />
      </section>
    );
  if (!products.length)
    return (
      <section className="pos-catalog ds-state" aria-label={t('productCatalog')}>
        <PackageSearch aria-hidden="true" />
        <h3>{t('noProductsFound')}</h3>
      </section>
    );
  return (
    <section className="pos-catalog" aria-label={t('productCatalog')}>
      {products.slice(0, visibleCount).map((product) => (
        <ProductCard key={product.id} product={product} onAdd={onAdd} />
      ))}
      {visibleCount < products.length && (
        <button className="ghost load-more" onClick={() => setVisibleCount((count) => count + 60)}>
          {t('showMoreProducts', { remaining: products.length - visibleCount })}
        </button>
      )}
    </section>
  );
}
