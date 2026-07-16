import type { Product } from '../../types';
import { Spinner } from '../../components/UI/Spinner';
import { ProductCard } from './ProductCard';
export function ProductGrid({
  products,
  loading,
  onAdd,
}: {
  products: Product[];
  loading: boolean;
  onAdd: (product: Product) => void;
}) {
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
        <h3>Aucun produit trouvé</h3>
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
