import { storeApi } from './api';
export const dashboardService = {
  get: () => storeApi.dashboard(),
  notifications: () => storeApi.notifications(),
};
