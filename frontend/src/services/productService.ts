import { storeApi } from './api';
export const productService = {
  list: (filters: unknown = {}) => storeApi.products(filters),
  save: (input: unknown) => storeApi.saveProduct(input),
  remove: (input: unknown) => storeApi.deleteProduct(input),
  importCsv: (filePath: string) => storeApi.importProducts(filePath),
  exportCsv: (content: string) => storeApi.saveExport({ name: 'produits.csv', content }),
};
