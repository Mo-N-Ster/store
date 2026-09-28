import fs from 'node:fs';
import { describe, expect, it } from 'vitest';
import type { CartLine, Product } from '../../../frontend/src/types';
import {
  addCartLine,
  cartSubtotal,
  cashChange,
  createSubmissionGuard,
  normalizeQuantity,
  productStockStatus,
  removeCartLine,
  saleTotal,
  updateCartLineQuantity,
} from '../../../frontend/src/pages/Cashier/posModel';

const product = (overrides: Partial<Product> = {}): Product => ({
  id: 1, name: 'Poivre', hashtag: '#poivre', category: 'Épices', description: '',
  price: 2.5, stockQuantity: 10, minStockThreshold: 2, ...overrides,
});

describe('STORE 3.0 tablet POS model', () => {
  it('classifies stock with the existing quantity and minimum threshold', () => {
    expect(productStockStatus(product({ stockQuantity: 10 }))).toBe('available');
    expect(productStockStatus(product({ stockQuantity: 2 }))).toBe('low');
    expect(productStockStatus(product({ stockQuantity: 0 }))).toBe('out');
  });

  it('adds a product selected by default and increments an existing line', () => {
    const added = addCartLine([], product(), 1);
    expect(added).toMatchObject([{ quantity: 1, selected: true }]);
    expect(addCartLine(added, product(), 2)[0].quantity).toBe(3);
  });

  it('does not add an out-of-stock product', () => {
    expect(addCartLine([], product({ stockQuantity: 0 }), 1)).toEqual([]);
  });

  it('bounds direct and repeated quantities to available stock', () => {
    expect(normalizeQuantity(99, 10)).toBe(10);
    expect(normalizeQuantity(-4, 10)).toBe(0);
    const line = addCartLine([], product({ stockQuantity: 3 }), 3);
    expect(addCartLine(line, product({ stockQuantity: 3 }), 1)[0].quantity).toBe(3);
    expect(updateCartLineQuantity(line, 1, 12)[0].quantity).toBe(3);
  });

  it('decreases to zero and removes a single cart line without affecting others', () => {
    const lines = [
      { product: product(), quantity: 1, selected: true },
      { product: product({ id: 2, name: 'Cannelle' }), quantity: 2, selected: true },
    ];
    expect(updateCartLineQuantity(lines, 1, 0)[0].quantity).toBe(0);
    expect(removeCartLine(lines, 1).map((line) => line.product.id)).toEqual([2]);
  });

  it('calculates cart subtotal, fixed/percent discounts and cash change', () => {
    const lines: CartLine[] = [{ product: product(), quantity: 4, selected: true }];
    expect(cartSubtotal(lines)).toBe(10);
    expect(saleTotal(10, 2, 'fixed', true)).toBe(8);
    expect(saleTotal(10, 10, 'percent', true)).toBe(9);
    expect(saleTotal(10, 9, 'fixed', false)).toBe(10);
    expect(cashChange(8, 10)).toBe(2);
    expect(cashChange(8, 5)).toBe(0);
  });

  it('blocks rapid duplicate submissions until the current attempt releases', () => {
    const guard = createSubmissionGuard();
    expect(guard.acquire()).toBe(true);
    expect(guard.acquire()).toBe(false);
    expect(guard.locked).toBe(true);
    guard.release();
    expect(guard.acquire()).toBe(true);
  });

  it('preserves the backend idempotency key across a recoverable retry', () => {
    const source = fs.readFileSync('frontend/src/pages/Cashier/CashierPage.tsx', 'utf8');
    expect(source).toContain('pendingSaleKey.current ||= crypto.randomUUID()');
    expect(source).toContain('idempotencyKey: pendingSaleKey.current');
    expect(source.indexOf('pendingSaleKey.current = null')).toBeGreaterThan(source.indexOf('const result = await saleService.create'));
  });

  it('keeps the cart until sale creation has resolved successfully', () => {
    const source = fs.readFileSync('frontend/src/pages/Cashier/CashierPage.tsx', 'utf8');
    expect(source.indexOf('cart.clear()')).toBeGreaterThan(source.indexOf('const result = await saleService.create'));
    const catchBlock = source.slice(source.indexOf('} catch'), source.indexOf('} finally'));
    expect(catchBlock).not.toContain('cart.clear()');
  });

  it('keeps history secondary and the portrait cart in a bottom drawer', () => {
    const source = fs.readFileSync('frontend/src/pages/Cashier/CashierPage.tsx', 'utf8');
    expect(source).toContain('<DailyHistoryDrawer');
    expect(source).toContain('<Drawer side="bottom"');
    expect(source).toContain('tablet-pos__portrait-summary');
  });

  it('defines adaptive catalogue batching and product-grid geometry', () => {
    const grid = fs.readFileSync('frontend/src/pages/Cashier/ProductGrid.tsx', 'utf8');
    const css = fs.readFileSync('frontend/src/design-system/pos.css', 'utf8');
    expect(grid).toContain('useState(60)');
    expect(grid).toContain('products.slice(0, visibleCount)');
    expect(css).toContain('repeat(auto-fill, minmax(');
    expect(css).toContain('@media (max-width: 56.25rem)');
  });

  it('uses textual stock states, accessible controls and no functional emoji', () => {
    const card = fs.readFileSync('frontend/src/pages/Cashier/ProductCard.tsx', 'utf8');
    const cart = fs.readFileSync('frontend/src/pages/Cashier/CartPanel.tsx', 'utf8');
    const combined = `${card}\n${cart}`;
    expect(card).toContain('statusLabel');
    expect(cart).toContain('aria-label={t(');
    expect(combined).not.toMatch(/[🛒📦🗑]/u);
  });

  it('provides checkout focus, decimal input and a focused success state', () => {
    const checkout = fs.readFileSync('frontend/src/pages/Cashier/CheckoutDialog.tsx', 'utf8');
    const invoice = fs.readFileSync('frontend/src/pages/Cashier/InvoicePreview.tsx', 'utf8');
    expect(checkout).toContain('data-autofocus');
    expect(checkout).toContain('NumberInput');
    expect(invoice).toContain('data-autofocus tabIndex={-1}');
    expect(invoice).toContain("role=\"alert\"");
  });
});
