export const purchaseStatuses = ['DRAFT', 'VALIDATED', 'CANCELLED'] as const;
export type PurchaseStatus = (typeof purchaseStatuses)[number];

export function validatePurchaseItem(input: { quantity: number; unitCost: number }) {
  if (
    !Number.isInteger(input.quantity) ||
    input.quantity <= 0 ||
    !Number.isFinite(input.unitCost) ||
    input.unitCost < 0
  )
    throw new Error('VALIDATION_ERROR');
  return input;
}

export function canEditPurchase(status: string) {
  return status === 'DRAFT';
}
