import{contextBridge,ipcRenderer}from'electron';import{IPC_METHODS,IPC_PREFIX}from'../ipc/channels.js';
const bridge=Object.fromEntries(IPC_METHODS.map(name=>[name,(...args:unknown[])=>ipcRenderer.invoke(`${IPC_PREFIX}${name}`,...args)]));
contextBridge.exposeInMainWorld('store',bridge);
