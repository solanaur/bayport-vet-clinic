/**
 * API base URL for hosted static sites (Netlify / Vercel).
 * Electron desktop sets localhost via preload (desktopBridge.apiBase).
 * Netlify build overwrites this file via scripts/write-deploy-env.js.
 */
(function () {
  if (typeof window !== "undefined" && window.desktopBridge && window.desktopBridge.apiBase) {
    window.BAYPORT_API_BASE = window.desktopBridge.apiBase;
    window.USE_API = true;
    try {
      localStorage.setItem("bayport_api_base", window.desktopBridge.apiBase);
    } catch (_) {}
  }
  window.BAYPORT_API_BASE = window.BAYPORT_API_BASE || "";
})();
