/** Presentation only: never alter persisted domain statuses or infer unknown states. */
export function statusKey(value: unknown, domain: 'document' | 'attendance' | 'account' = 'document') {
  const status = String(value ?? '').trim().toUpperCase();
  if (domain === 'attendance') return ({ OPEN: 'inProgress', PRESENT: 'present', VALID: 'attendanceCompleted', CLOSED: 'attendanceCompleted', COMPLETED: 'attendanceCompleted', CORRECTED: 'corrected', INTERRUPTED: 'interrupted', ABSENT: 'absent' } as Record<string, string>)[status] ?? 'unknownStatus';
  if (domain === 'account') return ({ ACTIVE: 'active', INACTIVE: 'inactive', SUSPENDED: 'suspended', ARCHIVED: 'archived' } as Record<string, string>)[status] ?? 'unknownStatus';
  return ({ DRAFT: 'draft', VALIDATED: 'validated', CANCELLED: 'cancelled', OPEN: 'inProgress', CLOSED: 'closed', PENDING: 'pending', SENT: 'sent', FAILED: 'failed' } as Record<string, string>)[status] ?? 'unknownStatus';
}
export const movementTypes = ['initial', 'purchase', 'purchase_cancellation', 'sale', 'invoice_reversal', 'inventory', 'adjustment', 'product_deletion'] as const;
export function movementKey(value: unknown) {
  const reason = String(value ?? '').trim().toLowerCase();
  return (movementTypes as readonly string[]).includes(reason) ? `movement_${reason}` : 'movement_other';
}
