import { storeApi } from './api';
export const saleService = {
  create: (input: unknown) => storeApi.createInvoice(input),
  currentCashSession: () => storeApi.currentCashSession({}),
  openCashSession: (openingAmount: number) => storeApi.openCashSession({ openingAmount }),
  closeCashSession: (countedAmount: number) => storeApi.closeCashSession({ countedAmount }),
  list: (filters: unknown = {}) => storeApi.invoices(filters),
  detail: (id: string) => storeApi.invoice(id),
  remove: (input: unknown) => storeApi.deleteInvoice(input),
  history: (input: unknown) => storeApi.history(input),
  deleteHistory: (input: unknown) => storeApi.deleteHistory(input),
  exportCsv: (name: string, content: string) => storeApi.saveExport({ name, content }),
};
