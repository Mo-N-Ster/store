export const ROLES={ADMIN:'admin',EMPLOYEE:'employee'}as const;export type Role=typeof ROLES[keyof typeof ROLES];
