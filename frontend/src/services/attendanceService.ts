import { storeApi } from './api';
export const attendanceService = {
  statuses: () => storeApi.attendanceStatuses(),
  sheet: (filters: unknown = {}) => storeApi.attendanceSheet(filters),
  history: (filters: unknown = {}) => storeApi.attendanceHistory(filters),
  clock: (input: { employeeId: number; password: string; action: 'CLOCK_IN' | 'CLOCK_OUT' }) => storeApi.clockAttendance(input),
  correct: (input: unknown) => storeApi.correctAttendance(input),
};
