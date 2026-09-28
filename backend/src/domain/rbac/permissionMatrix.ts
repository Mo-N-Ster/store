export const systemRoles = ['ADMIN', 'MANAGER', 'CASHIER'] as const;
export type SystemRole = (typeof systemRoles)[number];

export const permissionActions = ['READ', 'CREATE', 'UPDATE', 'DELETE', 'VALIDATE'] as const;
export type PermissionAction = (typeof permissionActions)[number];

export const permissionModules = [
  'DASHBOARD',
  'PRESENCE',
  'CASH',
  'POS',
  'PRODUCTS',
  'STOCKS',
  'PURCHASES',
  'EMPLOYEES',
  'FINANCES',
  'ADMINISTRATION',
  'SETTINGS',
  'BACKUPS',
  'RESTORE',
  'RESET',
] as const;
export type PermissionModule = (typeof permissionModules)[number];

const fullAccessModules: Record<SystemRole, readonly PermissionModule[]> = {
  ADMIN: permissionModules,
  MANAGER: [
    'DASHBOARD',
    'CASH',
    'POS',
    'PRODUCTS',
    'STOCKS',
    'PURCHASES',
    'EMPLOYEES',
    'FINANCES',
  ],
  CASHIER: [],
};

export function defaultPermissions(role: SystemRole) {
  const permissions = new Set<string>();
  for (const module of fullAccessModules[role])
    for (const action of permissionActions) permissions.add(`${module}:${action}`);

  if (role === 'MANAGER') permissions.add('PRESENCE:READ');
  if (role === 'CASHIER') {
    for (const module of ['DASHBOARD', 'PRESENCE', 'PRODUCTS', 'STOCKS'] as const)
      permissions.add(`${module}:READ`);
    for (const module of ['CASH', 'POS'] as const)
      for (const action of ['READ', 'CREATE', 'UPDATE', 'VALIDATE'] as const)
        permissions.add(`${module}:${action}`);
  }
  return permissions;
}

export function legacyRoleToSystemRole(role: string): SystemRole {
  if (role === 'owner') return 'ADMIN';
  if (role === 'manager') return 'MANAGER';
  return 'CASHIER';
}
