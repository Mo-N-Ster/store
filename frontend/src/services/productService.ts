import { storeApi } from './api';
export const productService = {
  list: (filters: unknown = {}) => storeApi.products(filters),
  save: (input: unknown) => storeApi.saveProduct(input),
  image: (reference: string) => storeApi.articleImage(reference) as Promise<string | null>,
  selectImage: () => storeApi.selectArticleImage() as Promise<{ token: string; mimeType: string; previewDataUrl: string } | null>,
  remove: (input: unknown) => storeApi.deleteProduct(input),
  importCsv: (filePath: string) => storeApi.importProducts(filePath),
  importPdf: (filePath: string) => storeApi.importProductsPdf(filePath),
  exportCsv: (content: string) => storeApi.saveExport({ name: 'produits.csv', content }),
};
