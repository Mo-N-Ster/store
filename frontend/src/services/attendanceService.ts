import { storeApi } from './api';
export const attendanceService = {
  toggle: (employeeId: number, present: boolean) => storeApi.attendance({ employeeId, present }),
};
