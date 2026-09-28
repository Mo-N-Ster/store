import { useCallback, useEffect, useRef, useState } from 'react';
import type { Product } from '../types';
import { productService } from '../services/productService';
export function useProducts(search = '', category = '') {
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const revision = useRef(0);
  const reload = useCallback(async () => {
    const current = ++revision.current;
    setLoading(true);
    setError(false);
    try {
      const rows = await productService.list({ search, category });
      if (current !== revision.current) return;
      setProducts(rows);
      if (!search && !category) setCategories([...new Set(rows.map((product: Product) => product.category))].sort() as string[]);
    } catch {
      if (current === revision.current) { setError(true); setProducts([]); }
    } finally {
      if (current === revision.current) setLoading(false);
    }
  }, [search, category]);
  useEffect(() => {
    const timer = window.setTimeout(() => void reload(), search ? 150 : 0);
    return () => { window.clearTimeout(timer); revision.current++; };
  }, [reload]);
  return { products, categories, loading, error, reload };
}
