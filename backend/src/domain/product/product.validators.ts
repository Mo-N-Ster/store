import type { ProductInput } from './product.types.js';
export function validateProduct(input: ProductInput) {
  if (
    !input.name.trim() ||
    !input.category.trim() ||
    !Number.isFinite(input.price) ||
    input.price < 0 ||
    Math.abs(input.price * 100 - Math.round(input.price * 100)) > 1e-8 ||
    !Number.isInteger(input.stockQuantity) ||
    input.stockQuantity < 0 ||
    !Number.isInteger(input.minStockThreshold) ||
    input.minStockThreshold < 0
  )
    throw new Error('INVALID_PRODUCT');
}
