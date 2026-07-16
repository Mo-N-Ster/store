export const IPC_PREFIX='store:' as const;
export const IPC_METHODS=['needsSetup','setupAdmin','login','verifyAdmin','users','saveUser','resetPassword','products','saveProduct','deleteProduct','createInvoice','invoices','invoice','deleteInvoice','attendance','messages','sendMessage','markMessage','deleteMessage','notifications','dashboard','settings','saveSettings','backup','reset','saveExport']as const;
export type IpcMethod=typeof IPC_METHODS[number];
