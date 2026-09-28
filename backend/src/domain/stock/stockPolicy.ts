export function validateStockAdjustment(input: { newQuantity: number; reason: string }) {
  if (!Number.isInteger(input.newQuantity) || input.newQuantity < 0 || input.reason.trim().length < 3)
    throw new Error('VALIDATION_ERROR');
  return { newQuantity: input.newQuantity, reason: input.reason.trim() };
}

export function validateInventoryQuantity(quantity: number) {
  if (!Number.isInteger(quantity) || quantity < 0) throw new Error('VALIDATION_ERROR');
  return quantity;
}
