import { useMemo, useState } from 'react';
import type { CartLine, Product } from '../types';
export function useCart() {
  const [lines, setLines] = useState<CartLine[]>([]);
  const add = (product: Product, quantity = 1) =>
    setLines((current) => {
      const found = current.find((line) => line.product.id === product.id);
      return found
        ? current.map((line) =>
            line === found
              ? { ...line, quantity: Math.min(line.quantity + quantity, product.stockQuantity) }
              : line,
          )
        : [
            ...current,
            { product, quantity: Math.min(quantity, product.stockQuantity), selected: false },
          ];
    });
  const updateQuantity = (id: number, quantity: number) =>
    setLines((current) =>
      current.map((line) => (line.product.id === id ? { ...line, quantity } : line)),
    );
  const toggle = (id: number, selected: boolean) =>
    setLines((current) =>
      current.map((line) => (line.product.id === id ? { ...line, selected } : line)),
    );
  const removeSelected = () => setLines((current) => current.filter((line) => !line.selected));
  const selectAll = (selected: boolean) =>
    setLines((current) => current.map((line) => ({ ...line, selected })));
  const clear = () => setLines([]);
  const subtotal = useMemo(
    () => lines.reduce((sum, line) => sum + line.quantity * line.product.price, 0),
    [lines],
  );
  return { lines, add, updateQuantity, toggle, selectAll, removeSelected, clear, subtotal };
}
