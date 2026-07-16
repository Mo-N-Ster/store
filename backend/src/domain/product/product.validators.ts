import type { ProductInput } from './product.types.js';
export function validateProduct(input: ProductInput) {
  if (
    !input.name.trim() ||
    !input.category.trim() ||
    input.price < 0 ||
    input.stockQuantity < 0 ||
    input.minStockThreshold < 0
  )
    throw new Error('INVALID_PRODUCT');
}
