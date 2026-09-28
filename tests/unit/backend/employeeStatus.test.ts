import { describe, expect, it } from 'vitest';
import {
  canEmployeeAuthenticate,
  employeeStatuses,
  isEmployeeStatus,
} from '../../../backend/src/domain/employee/employeeStatus';

describe('employee status', () => {
  it('recognizes every supported status', () => {
    for (const status of employeeStatuses) expect(isEmployeeStatus(status)).toBe(true);
    expect(isEmployeeStatus('DELETED')).toBe(false);
  });

  it('allows authentication only for active or temporarily absent employees', () => {
    expect(canEmployeeAuthenticate('ACTIVE')).toBe(true);
    expect(canEmployeeAuthenticate('ABSENT')).toBe(true);
    expect(canEmployeeAuthenticate('SUSPENDED')).toBe(false);
    expect(canEmployeeAuthenticate('RESIGNED')).toBe(false);
    expect(canEmployeeAuthenticate('ARCHIVED')).toBe(false);
  });
});
