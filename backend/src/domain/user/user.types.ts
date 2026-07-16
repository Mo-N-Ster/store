export interface UserInput {
  username: string;
  email?: string;
  password?: string;
  role?: 'owner' | 'manager' | 'employee';
  firstName: string;
  lastName: string;
  phone?: string;
  hireDate?: string;
  active?: boolean;
  securityQuestion?: string;
  securityAnswer?: string;
}
