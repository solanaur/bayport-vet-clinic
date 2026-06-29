/**
 * API base URL for hosted static sites (Netlify / Vercel).
 * Netlify build overwrites this via scripts/write-deploy-env.js.
 */
(function () {
  if (typeof window !== "undefined" && window.desktopBridge && window.desktopBridge.apiBase) {
    window.BAYPORT_API_BASE = window.desktopBridge.apiBase;
    window.USE_API = true;
    try {
      localStorage.setItem("bayport_api_base", window.desktopBridge.apiBase);
    } catch (_) {}
  }
  if (!window.BAYPORT_API_BASE) {
    try {
      const h = (window.location.hostname || "").toLowerCase();
      if (h.includes("netlify.app") || h.includes("vercel.app")) {
        window.BAYPORT_API_BASE = "https://bayport-api.onrender.com/api";
        window.USE_API = true;
      }
    } catch (_) {}
  }
  window.BAYPORT_API_BASE = window.BAYPORT_API_BASE || "";

  try {
    if (localStorage.getItem("bayport_sidebar_collapsed") === "1") {
      document.documentElement.classList.add("bp-sidebar-collapsed-pending");
      if (!document.getElementById("bp-sidebar-boot-styles")) {
        const style = document.createElement("style");
        style.id = "bp-sidebar-boot-styles";
        style.textContent =
          "html.bp-sidebar-collapsed-pending aside:has(#sidebarNav){width:0!important;min-width:0!important;padding-left:0!important;padding-right:0!important;overflow:hidden!important;border-right-color:transparent!important}";
        (document.head || document.documentElement).appendChild(style);
      }
    }
  } catch (_) {}
})();
