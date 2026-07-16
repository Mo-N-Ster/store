import { storeApi } from './api';
export const employeeService = {
  list: () => storeApi.users(),
  save: (input: unknown) => storeApi.saveUser(input),
  resetPassword: (id: number) => storeApi.resetPassword(id),
};
