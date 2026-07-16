export type UserRole = 'admin' | 'employee';
export interface User {
  id: number;
  username: string;
  email: string;
  role: UserRole;
  first_name?: string;
  last_name?: string;
  firstName?: string;
  lastName?: string;
  initials: string;
  phone?: string;
  hireDate?: string;
  active: number | boolean;
}
