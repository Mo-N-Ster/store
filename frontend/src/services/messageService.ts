import { storeApi } from './api';
export const messageService = {
  list: (input: unknown) => storeApi.messages(input),
  send: (input: unknown) => storeApi.sendMessage(input),
  mark: (id: number, isRead: boolean) => storeApi.markMessage({ id, isRead }),
  remove: (id: number) => storeApi.deleteMessage(id),
};
