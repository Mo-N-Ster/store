import { storeApi } from './api';
export const messageService = {
  list: (input: unknown) => storeApi.messages(input),
  recipients: () => storeApi.messageRecipients(),
  send: (input: unknown) => storeApi.sendMessage(input),
  mark: (id: number, userId: number, isRead: boolean) =>
    storeApi.markMessage({ id, userId, isRead }),
  remove: (id: number, userId: number) => storeApi.deleteMessage({ id, userId }),
};
