import { contextBridge, ipcRenderer } from 'electron';
const methods=['needsSetup','setupAdmin','login','verifyAdmin','users','saveUser','resetPassword','products','saveProduct','deleteProduct','createInvoice','invoices','invoice','deleteInvoice','attendance','messages','sendMessage','markMessage','deleteMessage','notifications','dashboard','settings','saveSettings','backup','reset','saveExport'];
const bridge=Object.fromEntries(methods.map(name=>[name,(...args:unknown[])=>ipcRenderer.invoke(`store:${name}`,...args)]));
contextBridge.exposeInMainWorld('store',bridge);
