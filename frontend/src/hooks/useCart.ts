import { useMemo, useState } from 'react';
import type { CartLine, Product } from '../types';
import { addCartLine, cartSubtotal, removeCartLine, updateCartLineQuantity } from '../pages/Cashier/posModel';
export function useCart() {
  const [lines, setLines] = useState<CartLine[]>([]);
  const add = (product: Product, quantity = 1) => setLines((current) => addCartLine(current, product, quantity));
  const updateQuantity = (id: number, quantity: number) =>
    setLines((current) => updateCartLineQuantity(current, id, quantity));
  const remove = (id: number) => setLines((current) => removeCartLine(current, id));
  const toggle = (id: number, selected: boolean) =>
    setLines((current) =>
      current.map((line) => (line.product.id === id ? { ...line, selected } : line)),
    );
  const removeSelected = () => setLines((current) => current.filter((line) => !line.selected));
  const selectAll = (selected: boolean) =>
    setLines((current) => current.map((line) => ({ ...line, selected })));
  const clear = () => setLines([]);
  const removeZeroQuantity = () =>
    setLines((current) => current.filter((line) => line.quantity > 0));
  const subtotal = useMemo(
    () => cartSubtotal(lines),
    [lines],
  );
  const selectedLines = useMemo(() => lines.filter((line) => line.selected), [lines]);
  const selectedSubtotal = useMemo(
    () => selectedLines.reduce((sum, line) => sum + line.quantity * line.product.price, 0),
    [selectedLines],
  );
  return {
    lines,
    selectedLines,
    add,
    updateQuantity,
    toggle,
    selectAll,
    removeSelected,
    remove,
    removeZeroQuantity,
    clear,
    subtotal,
    selectedSubtotal,
  };
}
