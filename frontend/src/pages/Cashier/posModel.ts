import type { CartLine, Product } from '../../types';

export type StockStatus = 'available' | 'low' | 'out';

export function productStockStatus(product: Product): StockStatus {
  if (product.stockQuantity <= 0) return 'out';
  if (product.stockQuantity <= product.minStockThreshold) return 'low';
  return 'available';
}

export function normalizeQuantity(quantity: number, stock: number) {
  if (!Number.isFinite(quantity)) return 0;
  return Math.min(Math.max(0, Math.floor(quantity)), Math.max(0, stock));
}

export function addCartLine(lines: CartLine[], product: Product, quantity = 1): CartLine[] {
  if (product.stockQuantity <= 0) return lines;
  const existing = lines.find((line) => line.product.id === product.id);
  if (!existing)
    return [...lines, { product, quantity: normalizeQuantity(quantity, product.stockQuantity), selected: true }];
  return lines.map((line) => line.product.id === product.id
    ? { ...line, quantity: normalizeQuantity(line.quantity + quantity, product.stockQuantity), selected: true }
    : line);
}

export function updateCartLineQuantity(lines: CartLine[], id: number, quantity: number): CartLine[] {
  return lines.map((line) => line.product.id === id
    ? { ...line, quantity: normalizeQuantity(quantity, line.product.stockQuantity) }
    : line);
}

export function removeCartLine(lines: CartLine[], id: number): CartLine[] {
  return lines.filter((line) => line.product.id !== id);
}

export function cartSubtotal(lines: CartLine[]) {
  return lines.reduce((sum, line) => sum + line.quantity * line.product.price, 0);
}

export function saleTotal(subtotal: number, discount: number, mode: 'fixed' | 'percent', enabled: boolean) {
  const applied = enabled ? mode === 'percent' ? (subtotal * discount) / 100 : Math.min(discount, subtotal) : 0;
  return Math.round(Math.max(0, subtotal - applied) * 100) / 100;
}

export function cashChange(total: number, received: number) {
  return Math.round(Math.max(0, received - total) * 100) / 100;
}

export function createSubmissionGuard() {
  let locked = false;
  return {
    acquire() { if (locked) return false; locked = true; return true; },
    release() { locked = false; },
    get locked() { return locked; },
  };
}
