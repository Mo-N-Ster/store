import { storeApi } from './api';
export const employeeService = {
  list: () => storeApi.users(),
  save: (input: unknown) => storeApi.saveUser(input),
  resetPassword: (id: number) => storeApi.resetPassword(id),
  securityQuestion: (id: number) => storeApi.securityQuestion(id),
  resetManagerPassword: (input: { id: number; answer: string; newPassword: string }) =>
    storeApi.resetManagerPassword(input),
};
