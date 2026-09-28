import { storeApi } from './api';

export const operationsService = {
  adjustStock: (input: unknown) => storeApi.adjustStock(input),
  stockMovements: (input: unknown = {}) => storeApi.stockMovements(input),
  startInventory: (input: unknown) => storeApi.startInventory(input),
  recordInventoryLine: (input: unknown) => storeApi.recordInventoryLine(input),
  validateInventory: (input: unknown) => storeApi.validateInventory(input),
  suppliers: () => storeApi.suppliers(),
  saveSupplier: (input: unknown) => storeApi.saveSupplier(input),
  purchases: (input: unknown = {}) => storeApi.purchases(input),
  purchaseDetail: (id: number) => storeApi.purchaseDetail(id),
  createPurchase: (input: unknown) => storeApi.createPurchase(input),
  savePurchaseItem: (input: unknown) => storeApi.savePurchaseItem(input),
  validatePurchase: (input: unknown) => storeApi.validatePurchase(input),
  cancelPurchase: (input: unknown) => storeApi.cancelPurchase(input),
};
