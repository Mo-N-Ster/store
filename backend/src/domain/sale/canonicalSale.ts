import type { SaleLineInput } from './sale.types.js';

// Versioned tuples avoid dependence on caller property order. Preserve duplicate
// lines (do not merge them); only their ordering is immaterial to equivalence.
export function canonicalSale(lines: SaleLineInput[], discount: number, received?: number) {
  if (!Array.isArray(lines) || !lines.length || !Number.isFinite(discount) || discount < 0)
    throw new Error('INVALID_INVOICE');
  const items = lines.map((line) => {
    if (!line || !Number.isSafeInteger(line.productId) || line.productId <= 0 ||
        !Number.isSafeInteger(line.quantity) || line.quantity <= 0)
      throw new Error('INVALID_INVOICE');
    return [line.productId, line.quantity];
  }).sort((a, b) => a[0] - b[0] || a[1] - b[1]);
  if (received !== undefined && (!Number.isFinite(received) || received < 0))
    throw new Error('VALIDATION_ERROR');
  return JSON.stringify([1, items, discount, received === undefined ? ['default'] : ['explicit', received]]);
}
