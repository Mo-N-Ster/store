export interface UserInput {
  username: string;
  email: string;
  password?: string;
  role?: 'admin' | 'employee';
  firstName: string;
  lastName: string;
  phone?: string;
  hireDate?: string;
  active?: boolean;
}
