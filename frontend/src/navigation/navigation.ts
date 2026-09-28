import { can, type EffectivePermission, type PermissionAction } from '../security/permissions';

export type AppDestination = 'home' | 'pos' | 'products' | 'stock' | 'purchases' | 'team' | 'presence' | 'reports' | 'salesHistory' | 'messages' | 'administration' | 'help';
export type DashboardSection = 'home' | 'products' | 'employees' | 'sales' | 'charts' | 'mailbox' | 'settings' | 'help';
export interface NavigationItem { id: AppDestination; labelKey: string; group: 'operations' | 'management' | 'system' }

const navigationItems: NavigationItem[] = [
  { id: 'home', labelKey: 'navHome', group: 'operations' }, { id: 'pos', labelKey: 'navPos', group: 'operations' },
  { id: 'products', labelKey: 'navProducts', group: 'operations' }, { id: 'stock', labelKey: 'navStock', group: 'operations' },
  { id: 'purchases', labelKey: 'navPurchases', group: 'operations' }, { id: 'team', labelKey: 'navTeam', group: 'management' },
  { id: 'reports', labelKey: 'navReports', group: 'management' },
  { id: 'messages', labelKey: 'navMessages', group: 'management' }, { id: 'administration', labelKey: 'navAdministration', group: 'system' },
];

const destinationPermission: Partial<Record<AppDestination, readonly [string, PermissionAction]>> = {
  home: ['DASHBOARD', 'READ'], pos: ['POS', 'READ'], products: ['PRODUCTS', 'READ'], stock: ['STOCKS', 'UPDATE'],
  purchases: ['PURCHASES', 'READ'], team: ['EMPLOYEES', 'READ'], presence: ['PRESENCE', 'READ'], reports: ['FINANCES', 'READ'],
  salesHistory: ['FINANCES', 'READ'], administration: ['SETTINGS', 'READ'],
};

export function destinationIsAvailable(permissions: readonly EffectivePermission[] | null, destination: AppDestination) {
  if (!permissions) return false;
  if (destination === 'team') return can(permissions, 'EMPLOYEES', 'READ') || can(permissions, 'PRESENCE', 'READ');
  const required = destinationPermission[destination];
  return required ? can(permissions, required[0], required[1]) : ['messages', 'help'].includes(destination);
}
export function navigationFor(permissions: readonly EffectivePermission[] | null, role?: string): NavigationItem[] {
  return navigationItems.filter((item) => !(role === 'employee' && item.id === 'messages') && destinationIsAvailable(permissions, item.id));
}
export function defaultDestination(permissions: readonly EffectivePermission[] | null): AppDestination {
  return navigationFor(permissions)[0]?.id ?? 'home';
}
