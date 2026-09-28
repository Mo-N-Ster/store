import { storeApi } from './api';
export const employeeService = {
  list: () => storeApi.users(),
  save: (input: unknown) => storeApi.saveUser(input),
  resetPassword: (id: number) => storeApi.resetPassword(id),
  generateUserPassword: (id: number) => storeApi.generateUserPassword({ id }) as Promise<string>,
  setUserPassword: (input: { id: number; newPassword: string }) => storeApi.setUserPassword(input) as Promise<boolean>,
  securityQuestion: (id: number) => storeApi.securityQuestion(id),
  resetManagerPassword: (input: { id: number; answer: string; newPassword: string }) =>
    storeApi.resetManagerPassword(input),
};
