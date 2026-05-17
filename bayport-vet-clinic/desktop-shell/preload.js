const { contextBridge, ipcRenderer } = require('electron');

/** Local Spring Boot started by main.js — must match main.js BACKEND_URL */
const DESKTOP_API_BASE = 'http://localhost:8080/api';

contextBridge.exposeInMainWorld('desktopBridge', {
  isDesktopApp: true,
  apiBase: DESKTOP_API_BASE,
  notifyError: (message) => ipcRenderer.invoke('desktop:show-error', message),
  platform: process.platform,
  listPrinters: () => ipcRenderer.invoke('desktop:list-printers'),
  printReceipt: (payload) => ipcRenderer.invoke('desktop:print-receipt', payload),
});

