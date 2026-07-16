export type User = {
  id: number;
  username: string;
  email: string;
  role: 'admin' | 'employee';
  first_name?: string;
  last_name?: string;
  firstName?: string;
  lastName?: string;
  initials: string;
  phone?: string;
  hireDate?: string;
  active: number | boolean;
};
export type Product = {
  id: number;
  name: string;
  hashtag: string;
  category: string;
  description: string;
  price: number;
  stockQuantity: number;
  minStockThreshold: number;
};
export type CartLine = { product: Product; quantity: number; selected: boolean };
export type StoreApi = { [key: string]: (...args: any[]) => Promise<any> };
declare global {
  interface Window {
    store: StoreApi;
  }
}
