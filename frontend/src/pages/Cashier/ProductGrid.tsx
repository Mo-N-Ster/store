import type { Product } from '../../types';
import { Spinner } from '../../components/UI/Spinner';
import { ProductCard } from './ProductCard';
import { useTranslation } from 'react-i18next';
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
  if (loading)
    return (
      <section className="catalog empty-state">
        <Spinner />
      </section>
    );
  if (!products.length)
    return (
      <section className="catalog empty-state">
        <span>📦</span>
        <h3>{t('noProductsFound')}</h3>
      </section>
    );
  return (
    <section className="catalog">
      {products.map((product) => (
        <ProductCard key={product.id} product={product} onAdd={onAdd} />
      ))}
    </section>
  );
}
