import { storeApi } from './api';
export const authService = {
  needsSetup: () => storeApi.needsSetup(),
  setupAdmin: (input: unknown) => storeApi.setupAdmin(input),
  login: (input: unknown) => storeApi.login(input),
  verifyAdmin: (input: unknown) => storeApi.verifyAdmin(input),
  forgotPasswordQuestion: (identifier: string) => storeApi.forgotPasswordQuestion(identifier),
  recoverPassword: (input: unknown) => storeApi.recoverPassword(input),
};
