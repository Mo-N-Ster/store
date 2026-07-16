import { storeApi } from './api';
export const productService = {
  list: (filters: unknown = {}) => storeApi.products(filters),
  save: (input: unknown) => storeApi.saveProduct(input),
  remove: (input: unknown) => storeApi.deleteProduct(input),
};
