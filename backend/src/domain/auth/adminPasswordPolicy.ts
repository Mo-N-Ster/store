export function assertAdministrativePasswordTarget(
  target: { role: string; active: number } | undefined,
  actor: { id: number; role: string },
) {
  if (!target || !target.active) throw new Error('USER_NOT_FOUND');
  if (!Number.isInteger(actor.id) || actor.id <= 0) throw new Error('FORBIDDEN');
  if (target.role === 'owner') throw new Error('FORBIDDEN');
  if (target.role === 'manager' && actor.role !== 'owner') throw new Error('FORBIDDEN');
  return true;
}
