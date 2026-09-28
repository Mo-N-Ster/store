export const employeeStatuses = [
  'ACTIVE',
  'ABSENT',
  'SUSPENDED',
  'RESIGNED',
  'ARCHIVED',
] as const;

export type EmployeeStatus = (typeof employeeStatuses)[number];

export function isEmployeeStatus(value: unknown): value is EmployeeStatus {
  return typeof value === 'string' && employeeStatuses.includes(value as EmployeeStatus);
}

export function canEmployeeAuthenticate(status: EmployeeStatus) {
  return status === 'ACTIVE' || status === 'ABSENT';
}
