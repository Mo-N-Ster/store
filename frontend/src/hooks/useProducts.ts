import { useCallback, useEffect, useState } from 'react';
import type { Product } from '../types';
import { productService } from '../services/productService';
export function useProducts(search = '', category = '') {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const reload = useCallback(async () => {
    setLoading(true);
    try {
      setProducts(await productService.list({ search, category }));
    } finally {
      setLoading(false);
    }
  }, [search, category]);
  useEffect(() => {
    const timer = window.setTimeout(() => void reload(), 200);
    return () => window.clearTimeout(timer);
  }, [reload]);
  return { products, loading, reload };
}
