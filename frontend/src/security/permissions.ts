export const permissionActions = ['READ', 'CREATE', 'UPDATE', 'DELETE', 'VALIDATE'] as const;
export type PermissionAction = (typeof permissionActions)[number];
export type EffectivePermission = { module: string; actions: PermissionAction[] };
export type SessionView = {
  user: { id: number; displayName: string; role: 'owner' | 'manager' | 'employee' };
  effectivePermissions: EffectivePermission[];
};

export function can(permissions: readonly EffectivePermission[] | null, module: string, action: PermissionAction) {
  if (!permissions) return false;
  return permissions.some((entry) => entry.module === module && entry.actions.includes(action));
}
