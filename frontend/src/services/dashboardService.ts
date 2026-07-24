import { storeApi } from './api';
export const dashboardService = {
  get: () => storeApi.dashboard(),
  notifications: () => storeApi.notifications(),
  deleteNotifications: (ids: number[]) => storeApi.deleteNotifications(ids),
  emailReportLogs: () => storeApi.emailReportLogs(),
  deleteEmailReportLogs: (ids: number[]) => storeApi.deleteEmailReportLogs(ids),
};
