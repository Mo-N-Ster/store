export const storeApi = window.store;
export const selectFile = (filters: { name: string; extensions: string[] }[]) =>
  storeApi.selectFile({ filters });
