export type StoreApi = Record<string, (...args: any[]) => Promise<any>>;
declare global {
  interface Window {
    store: StoreApi;
  }
}
