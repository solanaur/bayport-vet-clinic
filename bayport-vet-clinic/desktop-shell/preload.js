const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('desktopBridge', {
  notifyError: (message) => ipcRenderer.invoke('desktop:show-error', message),
  platform: process.platform,
  listPrinters: () => ipcRenderer.invoke('desktop:list-printers'),
  printReceipt: (payload) => ipcRenderer.invoke('desktop:print-receipt', payload)
});

