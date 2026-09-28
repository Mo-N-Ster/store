export function validateMoneyAmount(value: number) {
  if (!Number.isFinite(value) || value < 0) throw new Error('VALIDATION_ERROR');
  return Math.round(value * 100) / 100;
}

export function calculateCashDifference(expected: number, counted: number) {
  return Math.round((counted - expected) * 100) / 100;
}
