import { storeApi } from './api';
export const saleService = {
  create: (input: unknown) => storeApi.createInvoice(input),
  list: (filters: unknown = {}) => storeApi.invoices(filters),
  detail: (id: string) => storeApi.invoice(id),
  remove: (input: unknown) => storeApi.deleteInvoice(input),
  exportCsv: (name: string, content: string) => storeApi.saveExport({ name, content }),
};
