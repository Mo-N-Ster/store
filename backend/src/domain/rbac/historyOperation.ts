/** Runtime boundary for the only supported history deletion operation. */
export function validateHistoryType(type: unknown): asserts type is 'sales' | 'purchases' | 'personnel' {
  if (type !== 'sales' && type !== 'purchases' && type !== 'personnel') throw new Error('VALIDATION_ERROR');
}

export function validateHistoryDeletion(input: unknown): { type: 'purchases'; ids: number[] } {
  if (!input || typeof input !== 'object' || Array.isArray(input)) throw new Error('VALIDATION_ERROR');
  const value = input as Record<string, unknown>;
  validateHistoryType(value.type);
  if (value.type === 'personnel') throw new Error('FORBIDDEN');
  if (value.type !== 'purchases') throw new Error('VALIDATION_ERROR');
  if (!Array.isArray(value.ids) || value.ids.some((id) => !Number.isSafeInteger(id) || id <= 0))
    throw new Error('VALIDATION_ERROR');
  return { type: 'purchases', ids: [...new Set(value.ids as number[])] };
}
