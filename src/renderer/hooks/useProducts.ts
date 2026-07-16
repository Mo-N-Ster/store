import { useCallback, useEffect, useState } from 'react';
import type { Product } from '../types';
import { storeApi } from '../services/api';
export function useProducts(search = '', category = '') {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const reload = useCallback(async () => {
    setLoading(true);
    try {
      setProducts(await storeApi.products({ search, category }));
    } finally {
      setLoading(false);
    }
  }, [search, category]);
  useEffect(() => {
    void reload();
  }, [reload]);
  return { products, loading, reload };
}
