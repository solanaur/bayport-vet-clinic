/**
 * Bayport — shared top header (notifications, clock, user menu with Help + Logout).
 * Requires: deploy-env (optional), api.js, app.js (getRole, getUserName, logout, isFrontOffice).
 */
(function () {
  const ACCENT = {
    "soft-teal": "var(--soft-teal)",
    "bp-blue": "var(--bp-blue)",
    "pos-brand": "var(--pos-brand)",
  };

  function esc(s) {
    return String(s ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/"/g, "&quot;");
  }

  function initialsFromName(name) {
    const parts = String(name || "")
      .trim()
      .split(/\s+/)
      .filter(Boolean);
    if (!parts.length) return "•";
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  function apiBase() {
    try {
      return String((window.resolveApiBase && window.resolveApiBase()) || window.API_BASE || "")
        .trim()
        .replace(/\/+$/, "");
    } catch {
      return "";
    }
  }

  function authHeaders() {
    const token = localStorage.getItem("token") || localStorage.getItem("jwt");
    return {
      Authorization: token ? `Bearer ${token}` : "",
      "Content-Type": "application/json",
    };
  }

  function closeAllHeaderMenus(root) {
    if (!root) return;
    root.querySelector("#bpNotifDropdown")?.classList.add("hidden");
    root.querySelector("#bpUserMenuDropdown")?.classList.add("hidden");
    const chev = root.querySelector("#bpUserMenuChevron");
    if (chev) chev.classList.remove("rotate-180");
  }

  function fillUserRow(root) {
    const nm =
      (typeof window.getUserName === "function" ? window.getUserName() : "") ||
      localStorage.getItem("userDisplayName") ||
      localStorage.getItem("username") ||
      "Staff";
    const title =
      typeof window.getBayportHeaderRoleTitle === "function"
        ? window.getBayportHeaderRoleTitle()
        : "Staff";
    const iniEl = root.querySelector("#bpHeaderInitials");
    const nameEl = root.querySelector("#bpHeaderDisplayName");
    const roleEl = root.querySelector("#bpHeaderRoleTitle");
    if (iniEl) iniEl.textContent = initialsFromName(nm);
    if (nameEl) nameEl.textContent = nm;
    if (roleEl) roleEl.textContent = title;
  }

  function wireNotifications(root, accentVar) {
    const wrap = root.querySelector("#bpNotificationWrap");
    const btn = root.querySelector("#bpNotifBtn");
    const dropdown = root.querySelector("#bpNotifDropdown");
    const list = root.querySelector("#bpNotifList");
    const count = root.querySelector("#bpNotifCount");
    const markAll = root.querySelector("#bpMarkAllReadBtn");
    if (!wrap || !btn || !dropdown || !list || !count) return;

    async function loadNotifications() {
      const base = apiBase();
      if (!base) return;
      try {
        const response = await fetch(`${base}/notifications`, { headers: authHeaders() });
        if (!response.ok) return;
        const notifications = await response.json();
        const unreadCount = Array.isArray(notifications) ? notifications.filter((n) => !n.read).length : 0;
        if (unreadCount > 0) {
          count.textContent = String(unreadCount);
          count.classList.remove("hidden");
        } else {
          count.classList.add("hidden");
        }
        if (!Array.isArray(notifications) || notifications.length === 0) {
          list.innerHTML = '<div class="text-center text-gray-500 py-4">No notifications</div>';
        } else {
          list.innerHTML = notifications
            .map(
              (notif) => `
            <div class="p-3 border-b border-gray-100 ${notif.read ? "bg-white" : "bg-blue-50"} hover:bg-gray-50 cursor-pointer" data-bp-notif-id="${notif.id}">
              <div class="text-sm text-gray-800">${esc(notif.message)}</div>
              <div class="text-xs text-gray-500 mt-1">${esc(new Date(notif.createdAt).toLocaleString())}</div>
            </div>`,
            )
            .join("");
          list.querySelectorAll("[data-bp-notif-id]").forEach((row) => {
            row.addEventListener("click", async () => {
              const id = Number(row.getAttribute("data-bp-notif-id"));
              if (id) await markNotificationRead(id);
            });
          });
        }
      } catch (err) {
        console.error("Notifications:", err);
      }
    }

    async function markNotificationRead(id) {
      const base = apiBase();
      if (!base) return;
      try {
        await fetch(`${base}/notifications/${id}/read`, { method: "POST", headers: authHeaders() });
        await loadNotifications();
      } catch (err) {
        console.error("Mark read:", err);
      }
    }

    async function markAllAsRead() {
      const base = apiBase();
      if (!base) return;
      try {
        await fetch(`${base}/notifications/read-all`, { method: "POST", headers: authHeaders() });
        await loadNotifications();
      } catch (err) {
        console.error("Mark all read:", err);
      }
    }

    window.markNotificationRead = markNotificationRead;

    btn.addEventListener("click", (e) => {
      e.stopPropagation();
      const userMenu = root.querySelector("#bpUserMenuDropdown");
      if (userMenu && !userMenu.classList.contains("hidden")) {
        userMenu.classList.add("hidden");
        root.querySelector("#bpUserMenuChevron")?.classList.remove("rotate-180");
      }
      dropdown.classList.toggle("hidden");
      loadNotifications();
    });

    markAll?.addEventListener("click", (e) => {
      e.stopPropagation();
      markAllAsRead();
    });

    loadNotifications();
    setInterval(loadNotifications, 30000);
  }

  function wireUserMenu(root) {
    const btn = root.querySelector("#bpUserMenuBtn");
    const menu = root.querySelector("#bpUserMenuDropdown");
    const chev = root.querySelector("#bpUserMenuChevron");
    if (!btn || !menu) return;

    btn.addEventListener("click", (e) => {
      e.stopPropagation();
      if (menu.classList.contains("hidden")) {
        root.querySelector("#bpNotifDropdown")?.classList.add("hidden");
      }
      menu.classList.toggle("hidden");
      chev?.classList.toggle("rotate-180", !menu.classList.contains("hidden"));
    });

    menu.querySelector("#bpMenuHelp")?.addEventListener("click", () => {
      closeAllHeaderMenus(root);
      window.location.href = "help.html";
    });
    menu.querySelector("#bpMenuLogout")?.addEventListener("click", () => {
      closeAllHeaderMenus(root);
      if (typeof window.logout === "function") window.logout();
    });
  }

  let bpGlobalHeaderCloserBound = false;
  function ensureGlobalHeaderCloser() {
    if (bpGlobalHeaderCloserBound) return;
    bpGlobalHeaderCloserBound = true;
    document.addEventListener("click", (e) => {
      document.querySelectorAll("[data-bp-app-header-root]").forEach((r) => {
        if (r.contains(e.target)) return;
        closeAllHeaderMenus(r);
      });
    });
  }

  function wireDocumentClose() {
    ensureGlobalHeaderCloser();
  }

  function wireClock(root) {
    const el = root.querySelector("#bpHeaderClock");
    if (!el) return;
    function tick() {
      el.textContent = new Date().toLocaleTimeString(undefined, {
        hour: "numeric",
        minute: "2-digit",
      });
    }
    tick();
    setInterval(tick, 1000);
  }

  /**
   * @param {HTMLElement} root
   * @param {{ accent?: string, tagline?: string }} [opts]
   */
  window.initBayportAppHeader = function (root, opts) {
    if (!root || root.dataset.bpHeaderInited === "1") return;
    root.dataset.bpHeaderInited = "1";
    opts = opts || {};
    const accentKey = opts.accent || "soft-teal";
    const accentVar = ACCENT[accentKey] || ACCENT["soft-teal"];
    const tagline =
      opts.tagline != null
        ? opts.tagline
        : "Caring for your pets with compassion and expertise.";

    root.setAttribute("data-bp-app-header-root", "1");

    root.className =
      "w-full shrink-0 text-white shadow-soft flex flex-wrap items-center justify-between gap-3 px-6 lg:px-10 py-3.5";
    root.style.backgroundColor = accentVar;

    root.innerHTML = `
      <div class="flex items-center gap-3 min-w-0 flex-1">
        <img src="assets/logo.png" class="w-11 h-11 sm:w-12 sm:h-12 rounded-lg bg-white p-0.5 object-contain shrink-0" alt="" />
        <div class="min-w-0">
          <h1 class="text-lg sm:text-xl font-semibold tracking-tight truncate">Bayport Veterinary Clinic</h1>
          <p class="text-xs sm:text-sm text-white/85 truncate">${esc(tagline)}</p>
        </div>
      </div>
      <div class="flex items-center gap-2 sm:gap-4 shrink-0 ml-auto">
        <div id="bpNotificationWrap" class="relative">
          <button type="button" id="bpNotifBtn" class="relative rounded-lg bg-white px-3 py-2 shadow-sm hover:bg-gray-50 flex items-center justify-center bp-header-accent-text" data-accent="${accentKey}" title="Notifications" aria-label="Notifications">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"></path>
            </svg>
            <span id="bpNotifCount" class="hidden absolute -top-1 -right-1 bg-red-500 text-white text-[10px] font-bold rounded-full min-w-[1.25rem] h-5 px-1 flex items-center justify-center">0</span>
          </button>
          <div id="bpNotifDropdown" class="hidden absolute right-0 mt-2 w-80 max-w-[calc(100vw-2rem)] bg-white rounded-lg shadow-lg border border-gray-200 z-[200] max-h-96 overflow-y-auto text-gray-900">
            <div class="p-3 border-b border-gray-200 flex justify-between items-center gap-2">
              <h3 class="font-semibold text-gray-800 text-sm">Notifications</h3>
              <button type="button" id="bpMarkAllReadBtn" class="text-xs font-medium hover:underline shrink-0 bp-mark-all-read" data-accent="${accentKey}">Mark all as read</button>
            </div>
            <div id="bpNotifList" class="p-2">
              <div class="text-center text-gray-500 py-4 text-sm">No notifications</div>
            </div>
          </div>
        </div>
        <div class="hidden sm:flex flex-col items-end leading-tight text-right pr-1 border-r border-white/25 mr-1 sm:mr-2">
          <span class="text-[10px] uppercase tracking-wide text-white/70">Time</span>
          <span id="bpHeaderClock" class="text-sm font-semibold tabular-nums"></span>
        </div>
        <div class="relative">
          <button type="button" id="bpUserMenuBtn" class="flex items-center gap-2 rounded-xl pl-1 pr-2 py-1 hover:bg-white/10 border border-transparent hover:border-white/15 transition-colors text-left">
            <span id="bpHeaderInitials" class="w-9 h-9 rounded-full bg-white flex items-center justify-center text-xs font-bold shrink-0 shadow-sm bp-header-initials" data-accent="${accentKey}"></span>
            <div class="hidden md:block min-w-0 max-w-[11rem]">
              <div id="bpHeaderDisplayName" class="text-sm font-semibold truncate leading-tight"></div>
              <div id="bpHeaderRoleTitle" class="text-[10px] text-white/75 font-medium leading-tight truncate"></div>
            </div>
            <svg id="bpUserMenuChevron" class="w-4 h-4 text-white/80 shrink-0 transition-transform hidden md:block" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path>
            </svg>
          </button>
          <div id="bpUserMenuDropdown" class="hidden absolute right-0 mt-2 w-52 rounded-xl bg-white text-gray-900 shadow-xl border border-gray-200 py-1 z-[200] overflow-hidden">
            <button type="button" id="bpMenuHelp" class="w-full text-left px-4 py-3 text-sm font-medium hover:bg-gray-50 flex items-center gap-2">
              <svg class="w-4 h-4 opacity-70" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
              Help &amp; support
            </button>
            <div class="border-t border-gray-100"></div>
            <button type="button" id="bpMenuLogout" class="w-full text-left px-4 py-3 text-sm font-semibold hover:bg-red-50 text-red-700">
              Log out
            </button>
          </div>
        </div>
      </div>
    `;

    root.querySelectorAll("[data-accent]").forEach((el) => {
      const k = el.getAttribute("data-accent") || accentKey;
      el.style.color = ACCENT[k] || accentVar;
    });

    fillUserRow(root);
    wireClock(root);
    wireNotifications(root, accentVar);
    wireUserMenu(root);
    wireDocumentClose();
  };
})();
