/* ===========================================================
   Bayport Veterinary Clinic — Main App Script (Frontend)
   =========================================================== */

window.tryLogin = async function(username, password, role, otp) {
  if (!role) return { ok: false, msg: "Please select a role first." };
  ensureApiEnabled();
  try {
    const payload = await Api.auth.login(username, password, otp);
    if (payload.status === "MFA_REQUIRED") {
      return { ok: false, msg: "MFA_REQUIRED", username: payload.username };
    }
    if (payload.status === "TOS_REQUIRED") {
      return { ok: false, msg: "TOS_REQUIRED", username: payload.username, currentVersion: payload.currentVersion };
    }
    if (!payload || !payload.role) {
      return { ok: false, msg: "Unable to read login response." };
    }
    const normalizedSelected =
      role === "receptionist" || role === "pharmacist" ? "front_office" : role;
    if (normalizedSelected && payload.role !== normalizedSelected) {
      return {
        ok: false,
        msg: `The account "${payload.username}" is registered as ${payload.role.toUpperCase().replace(/_/g, " ")}.` };
    }
    localStorage.setItem("role", payload.role);
    localStorage.setItem("userDisplayName", payload.name);
    localStorage.setItem("username", payload.username);
    localStorage.setItem("userId", payload.id);
    if (payload.token) {
      localStorage.setItem("token", payload.token);
      localStorage.setItem("jwt", payload.token);
    }
    return { ok: true };
  } catch (err) {
    const msg = err.message?.includes("401")
      ? "Invalid username or password."
      : (err.message || "Unable to reach the server.");
    return { ok: false, msg };
  }
};

/* ===== Log out ===== */
window.logout = function() {
  const confirmed = window.confirm("Are you sure you want to log out?");
  if (!confirmed) return;
  localStorage.removeItem("role");
  localStorage.removeItem("userDisplayName");
  localStorage.removeItem("username");
  localStorage.removeItem("userId");
  location.href = "index.html";
};

/* ===== Role & Sidebar ===== */
const CONFIG = {
  admin:        ["dashboard","pet-records","appointments","prescriptions","pos","billing","reminders","inventory","reports","settings","activity-logs","recycle-bin","manage-users"],
  vet:          ["dashboard","pet-records","appointments","prescriptions"],
  // Front Office = reception + pharmacy (single role for cloud scaling)
  front_office: ["dashboard","pet-records","appointments","prescriptions","pos","billing","reminders","inventory"],
  receptionist: ["dashboard","pet-records","appointments","prescriptions","pos","billing","reminders","inventory"],
  pharmacist:   ["dashboard","pet-records","appointments","prescriptions","pos","billing","reminders","inventory"]
};

const LABEL = {
  "dashboard":"Dashboard",
  "pet-records":"Pet Records",
  "appointments":"Appointments",
  "prescriptions":"Prescriptions",
  "billing":"Billing",
  "settings":"Settings",
  "inventory":"Inventory",
  "reports":"Reports",
  "manage-users":"Manage Users",
  "reminders":"Reminders",
  "activity-logs":"Activity Logs",
  "recycle-bin":"Recycle Bin",
  "pos":"Point of Sale"
};

/** Shorter labels in the sidebar only (less visual noise). */
const SIDEBAR_LABEL = {
  ...LABEL,
  "pet-records": "Pet records",
  "activity-logs": "Activity logs",
  "manage-users": "Users & roles",
  "recycle-bin": "Recycle bin",
  "pos": "POS"
};

/**
 * Collapsible groups — daily tasks stay on top; secondary/admin tucked away.
 * Reduces overwhelm for busy clinic staff.
 */
const _SIDEBAR_FRONT_OFFICE = [
  { label: "Daily workflow", open: true, keys: ["dashboard", "pet-records", "appointments", "prescriptions"] },
  { label: "More", open: false, keys: ["billing", "reminders", "inventory"] }
];
/** Shown as a separate emphasized block (not a normal nav row). */
const SIDEBAR_POS_KEY = "pos";
const SIDEBAR_GROUPS = {
  admin: [
    { label: "Daily workflow", open: true, keys: ["dashboard", "pet-records", "appointments", "prescriptions"] },
    { label: "Scheduling & stock", open: true, keys: ["billing", "reminders", "inventory"] },
    { label: "Reports", open: false, keys: ["reports"] },
    { label: "Settings", open: false, keys: ["settings"] },
    { label: "Administration", open: false, keys: ["activity-logs", "recycle-bin", "manage-users"] }
  ],
  vet: [
    { label: "", open: true, keys: ["dashboard", "pet-records", "appointments", "prescriptions"] }
  ],
  front_office: _SIDEBAR_FRONT_OFFICE,
  receptionist: _SIDEBAR_FRONT_OFFICE,
  pharmacist: _SIDEBAR_FRONT_OFFICE
};

const ROLE_COPY = {
  admin:{ welcome:"Full access. Manage users, pets, appointments, prescriptions, and inventory." },
  vet:{ welcome:"Review/approve appointments and issue prescriptions." },
  front_office:{ welcome:"Front desk: register checkout, appointments, prescriptions, billing, and inventory." },
  receptionist:{ welcome:"Front Office: register checkout, appointments, prescriptions, and inventory." },
  pharmacist:{ welcome:"Front Office: register checkout, appointments, prescriptions, and inventory." }
};

window.getRole = function(){ return localStorage.getItem("role") || ""; };
window.getUserName = function(){ return localStorage.getItem("userDisplayName") || ""; };
const RECEIPT_PRINTER_KEY = "bayport_receipt_printer";
const BRANCH_CODE_KEY = "bayport_branch_code";
const CLINIC_SETTINGS_KEY = "bayport_clinic_settings";
window.getPreferredReceiptPrinter = function() {
  return localStorage.getItem(RECEIPT_PRINTER_KEY) || "";
};
window.setPreferredReceiptPrinter = function(deviceName) {
  if (!deviceName) {
    localStorage.removeItem(RECEIPT_PRINTER_KEY);
    return;
  }
  localStorage.setItem(RECEIPT_PRINTER_KEY, String(deviceName));
};
window.listDesktopPrinters = async function() {
  try {
    if (!window.desktopBridge?.listPrinters) return [];
    const printers = await window.desktopBridge.listPrinters();
    return Array.isArray(printers) ? printers : [];
  } catch {
    return [];
  }
};
window.getBranchCode = function() {
  return localStorage.getItem(BRANCH_CODE_KEY) || "default-clinic";
};
window.setBranchCode = function(code) {
  if (!code || !String(code).trim()) return;
  localStorage.setItem(BRANCH_CODE_KEY, String(code).trim());
};
window.queueCloudPrint = async function queueCloudPrint(html, deviceName) {
  return Api.printJobs.enqueue({
    branchCode: window.getBranchCode(),
    deviceName: deviceName || "",
    receiptHtml: html,
  });
};
window.getClinicSettings = function() {
  const defaults = {
    name: "Bayport Veterinary Clinic",
    address: "322 Quirino Avenue, Brgy. Don Galo, Parañaque City",
    logoDataUrl: "",
  };
  try {
    const raw = localStorage.getItem(CLINIC_SETTINGS_KEY);
    if (!raw) return defaults;
    const parsed = JSON.parse(raw);
    return {
      ...defaults,
      ...(parsed && typeof parsed === "object" ? parsed : {}),
    };
  } catch {
    return defaults;
  }
};
window.setClinicSettings = function(settings) {
  const current = window.getClinicSettings();
  const next = {
    ...current,
    ...(settings && typeof settings === "object" ? settings : {}),
  };
  localStorage.setItem(CLINIC_SETTINGS_KEY, JSON.stringify(next));
};

window.ensureLoggedIn = function(){
  if(!localStorage.getItem("role")) location.href="index.html";
};

/** Unified Front Office + legacy role checks (for cloud JWT `front_office`). */
window.isFrontOffice = function(role) {
  const r = (role || "").toLowerCase();
  return r === "front_office" || r === "receptionist" || r === "pharmacist";
};

window.renderSidebar = function (container, role, activeFile) {
  container.innerHTML = "";

  const allowed = new Set(CONFIG[role] || []);
  const groups = SIDEBAR_GROUPS[role] || SIDEBAR_GROUPS.front_office;

  if (allowed.has(SIDEBAR_POS_KEY)) {
    const posWrap = document.createElement("div");
    posWrap.className = "mb-4";
    const posLink = document.createElement("a");
    posLink.href = "pos.html";
    const posActive = activeFile === "pos.html";
    posLink.className = posActive
      ? "flex items-center justify-center gap-2 w-full rounded-xl px-4 py-3.5 text-base font-bold text-white bg-gradient-to-r from-[var(--soft-teal)] to-[#0a4a96] shadow-lg ring-2 ring-[var(--soft-teal)]/30 hover:brightness-105 transition-all"
      : "flex items-center justify-center gap-2 w-full rounded-xl px-4 py-3.5 text-base font-bold text-[var(--soft-teal)] bg-blue-50 border-2 border-[var(--soft-teal)]/40 hover:bg-blue-100 transition-all";
    posLink.innerHTML =
      '<span class="text-xl leading-none">🧾</span><span>Point of Sale</span>';
    posWrap.appendChild(posLink);
    container.appendChild(posWrap);
  }

  /** Vets: one short list — no collapsible chrome. */
  const flatSingleGroup = groups.length === 1;

  groups.forEach((group) => {
    const keys = group.keys.filter((k) => allowed.has(k) && k !== SIDEBAR_POS_KEY);
    if (keys.length === 0) return;

    const wrap = document.createElement("div");
    wrap.className = flatSingleGroup
      ? "sidebar-group flex flex-col gap-0.5 mb-0"
      : "mt-1 flex flex-col gap-0.5";

    if (flatSingleGroup && String(group.label || "").trim()) {
      const head = document.createElement("p");
      head.className = "text-[11px] font-semibold uppercase tracking-wide text-gray-400 px-1 mb-2";
      head.textContent = group.label;
      container.appendChild(head);
    }

    keys.forEach((key) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className =
        "w-full text-left px-3 py-2 text-sm rounded-lg text-gray-700 hover:bg-blue-50/90 hover:text-[var(--soft-teal)] transition-colors";
      btn.textContent = SIDEBAR_LABEL[key] || LABEL[key];
      btn.onclick = () => {
        location.href = `${key}.html`;
      };
      if (`${key}.html` === activeFile) {
        btn.dataset.sidebarActive = "1";
        btn.className =
          "w-full text-left px-3 py-2 text-sm rounded-lg bg-[var(--soft-teal)] text-white font-medium shadow-sm";
      }
      wrap.appendChild(btn);
    });

    if (flatSingleGroup) {
      container.appendChild(wrap);
      return;
    }

    const det = document.createElement("details");
    det.className =
      "sidebar-group mb-1 border-b border-gray-100 pb-2 last:border-0 last:pb-0";
    if (group.open) det.open = true;

    const sum = document.createElement("summary");
    sum.className =
      "cursor-pointer select-none list-none flex items-center justify-between gap-2 px-1 py-2 text-[11px] font-semibold uppercase tracking-wide text-gray-400 hover:text-gray-600 [&::-webkit-details-marker]:hidden";
    const tit = document.createElement("span");
    tit.textContent = group.label;
    const chev = document.createElement("span");
    chev.className = "text-gray-300 text-xs transition-transform duration-200 sidebar-chevron shrink-0";
    chev.textContent = "▼";
    sum.appendChild(tit);
    sum.appendChild(chev);
    det.addEventListener("toggle", () => {
      chev.style.transform = det.open ? "rotate(0deg)" : "rotate(-90deg)";
    });
    chev.style.transform = det.open ? "rotate(0deg)" : "rotate(-90deg)";
    det.appendChild(sum);
    det.appendChild(wrap);
    container.appendChild(det);
  });

  // Open the section that contains the current page (so users don't "lose" the highlight)
  container.querySelectorAll("details.sidebar-group").forEach((det) => {
    if (det.querySelector("button[data-sidebar-active='1']")) det.open = true;
  });
};

window.ROLE_COPY = ROLE_COPY;

// Used by pet records/profile to turn `/uploads/...` from the backend into a full URL that browsers can load.
const API_ORIGIN_FOR_ASSETS = (() => {
  try {
    if(!window.API_BASE) return "";
    const url = new URL(window.API_BASE, window.location.origin);
    const cleanedPath = url.pathname.replace(/\/api\/?$/, "/");
    const trimmed = cleanedPath.endsWith("/") ? cleanedPath.slice(0, -1) : cleanedPath;
    return `${url.origin}${trimmed}`;
  } catch {
    return (window.API_BASE || "").replace(/\/api\/?$/, "");
  }
})();

window.resolveAssetUrl = function resolveAssetUrl(value){
  if(!value) return "";
  if(/^https?:\/\//i.test(value) || /^data:/i.test(value) || /^blob:/i.test(value)){
    return value;
  }
  if(!API_ORIGIN_FOR_ASSETS){
    return value;
  }
  return value.startsWith("/")
    ? `${API_ORIGIN_FOR_ASSETS}${value}`
    : `${API_ORIGIN_FOR_ASSETS}/${value}`;
};

/**
 * Simple health check helper used by desktop builds to verify that the
 * Spring Boot backend is reachable. Returns a promise that resolves to
 * a boolean (true if backend reports status=UP, false otherwise).
 */
window.checkBackendHealth = async function checkBackendHealth() {
  try {
    const res = await Api.health.check();
    return !!res && (res.status === "UP" || res.status === "up");
  } catch (err) {
    console && console.error && console.error("Backend health check failed:", err);
    return false;
  }
};

/* ===== Dashboard Quick Actions ===== */
window.renderQuickActions = function(el,role){
  el.innerHTML="";
  const make = (title,href,icon)=> {
    const b=document.createElement("button");
    b.className="text-left w-full bg-[#f7fbfb] hover:bg-[#eef6f6] border border-gray-200 rounded-xl p-5 shadow-soft";
    b.innerHTML=`<div class='text-2xl mb-2'>${icon}</div><div class='font-semibold'>${title}</div>`;
    b.onclick=()=>location.href=href;
    return b;
  };
  const map={
    admin:[make("Point of Sale","pos.html","🧾"),make("Add Pet Record","pet-records.html","➕"),make("Inventory","inventory.html","📦"),make("Create Appointment","appointments.html","📅"),make("Billing","billing.html","🧾"),make("Settings","settings.html","⚙️"),make("Manage Users","manage-users.html","👥"),make("Issue Prescription","prescriptions.html","💊"),make("View Reports","reports.html","📈")],
    vet:[make("Review Appointments","appointments.html","📅"),make("Issue Prescription","prescriptions.html","💊"),make("View Pet Records","pet-records.html","🐾")],
    front_office:[make("Point of Sale","pos.html","🧾"),make("Create Appointment","appointments.html","📅"),make("Prescriptions","prescriptions.html","💊"),make("Inventory","inventory.html","📦"),make("Pet Records","pet-records.html","🐾")],
    receptionist:[make("Point of Sale","pos.html","🧾"),make("Create Appointment","appointments.html","📅"),make("Prescriptions","prescriptions.html","💊"),make("Inventory","inventory.html","📦"),make("Pet Records","pet-records.html","🐾")],
    pharmacist:[make("Point of Sale","pos.html","🧾"),make("Create Appointment","appointments.html","📅"),make("Prescriptions","prescriptions.html","💊"),make("Inventory","inventory.html","📦"),make("Pet Records","pet-records.html","🐾")]
  };
  (map[role]||[]).forEach(btn=>el.appendChild(btn));
};

/* ===== Global Error Handler (helps on desktop builds without DevTools) ===== */
if(!window.__bayport_error_handler_installed){
  window.__bayport_error_handler_installed = true;
  let errorPopupShown = false;

  function showFriendlyError(message) {
    // Avoid spamming the user with repeated popups; show at most once per session.
    if (errorPopupShown) return;
    errorPopupShown = true;
    let msg = message || "Something went wrong in the app. Please try again, and check that the backend is running.";

    try {
      const offline =
        (typeof navigator !== "undefined" && navigator && navigator.onLine === false) ||
        /Failed to fetch|NetworkError|ERR_INTERNET_DISCONNECTED/i.test(msg || "");
      if (offline) {
        msg = "No Internet Connection.";
      }
    } catch (_) {
      // If anything goes wrong while checking connectivity, fall back to the original message.
    }

    if (window.desktopBridge?.notifyError) {
      window.desktopBridge.notifyError(msg);
    } else {
      alert(msg);
    }
  }

  window.addEventListener('error', function(e){
    try{
      const msg = e?.message || 'Unknown script error';
      console && console.error && console.error('GlobalError:', e?.error||e);
      showFriendlyError(msg);
    }catch(_){/* ignore */}
  });

  window.addEventListener('unhandledrejection', function(e){
    try{
      const reason = e && (e.reason?.message || e.reason || '').toString();
      console && console.error && console.error('UnhandledRejection:', e?.reason||e);
      const msg = reason || 'Unknown promise rejection';
      showFriendlyError(msg);
    }catch(_){/* ignore */}
  });
}

/* ===== Domain Constants & Validation ===== */
window.SPECIES_TO_BREEDS = {
  Canine: [
    "Aspin","Labrador","Golden Retriever","German Shepherd","Poodle","Shih Tzu","Pug","Beagle","Husky","Dachshund","Chihuahua","Rottweiler","French Bulldog","Corgi","Border Collie"
  ],
  Feline: [
    "Puspin","Persian","Siamese","Maine Coon","British Shorthair","American Shorthair","Ragdoll","Scottish Fold","Bengal","Sphynx","Russian Blue","Norwegian Forest","Burmese","Abyssinian","Manx"
  ]
};

// Phone numbers must be exactly 11 digits (Philippine mobile-style numbers),
// digits only. This is enforced both here and on the backend.
window.validatePhone = function(phone){
  const cleaned = (phone || "").replace(/\D/g, "");
  return /^\d{11}$/.test(cleaned);
};

window.validateEmail = function(email){
  if(!email) return true;
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
};

/* ===== Procedure Catalog & Prices (PHP) — clinic fee schedule ===== */
window.PROCEDURE_CATALOG = {
  'Consultation & Check-up': [
    { name:'Consultation Fee', price:350, medications:'', dosage:'', directions:'' },
    { name:'Consultation After 6:00 PM', price:500, medications:'', dosage:'', directions:'' },
    { name:'Emergency Fee', price:800, medications:'', dosage:'', directions:'' },
    { name:'Confinement 1st Day', price:1200, medications:'', dosage:'', directions:'' },
    { name:'Succeeding Days', price:1000, medications:'', dosage:'', directions:'' }
  ],
  'Vaccine & Deworming': [
    { name:'Feline 4 in 1', price:1000, medications:'Feline 4-in-1 Vaccine', dosage:'as indicated', directions:'Follow schedule' },
    { name:'Canine 5 in 1', price:1000, medications:'Canine 5-in-1 Vaccine', dosage:'as indicated', directions:'Follow schedule' },
    { name:'Canine 6 in 1', price:1000, medications:'Canine 6-in-1 Vaccine', dosage:'as indicated', directions:'Follow schedule' },
    { name:'Canine 8 in 1', price:1200, medications:'Canine 8-in-1 Vaccine', dosage:'as indicated', directions:'Follow schedule' },
    { name:'Kennel Cough Vaccine', price:500, medications:'KC Vaccine', dosage:'as indicated', directions:'Follow schedule' },
    { name:'Anti-Rabies', price:350, medications:'Anti-Rabies Vaccine', dosage:'as indicated', directions:'Annual booster' },
    { name:'Deworming Canine', price:250, medications:'Anthelmintic', dosage:'per kg', directions:'Repeat as advised' },
    { name:'Deworming Feline', price:200, medications:'Anthelmintic', dosage:'per kg', directions:'Repeat as advised' }
  ],
  'Laboratory Tests': [
    { name:'Complete Blood Count (CBC)', price:550 },
    { name:'Comprehensive Blood Chemistry', price:3550 },
    { name:'Chemistry 24 Panel', price:2950 },
    { name:'Chemistry 10 Panel', price:2250 },
    { name:'X-Ray', price:700 },
    { name:'Ultrasound OB', price:800 },
    { name:'Ultrasound', price:800 },
    { name:'Incubator', price:1000, notes:'Per day' },
    { name:'2D ECHO', price:2500 },
    { name:'ECG', price:1500 },
    { name:'PCR Test', price:3500 },
    { name:'Rivalta Test', price:350 },
    { name:'Urinalysis', price:400 },
    { name:'Fecalysis', price:350 },
    { name:'Immunofluorescence Assay Test', price:1300 },
    { name:'Ear Smear Test', price:300 },
    { name:'Oxygen Chamber', price:1500, notes:'Per hour' },
    { name:'Nebulization Treatment', price:250 },
    { name:'Pet iChip Standard', price:500 },
    { name:'Blood Glucose Test', price:250 },
    { name:'Vaginal Smear Test', price:250 },
    { name:'Skin Scrape Test', price:250 },
    { name:'Laser Therapy', price:1500 },
    { name:'Dental Service', price:1500 }
  ],
  'Rapid Tests': [
    { name:'Feline Panleukopenia Virus Drop Test', price:800 },
    { name:'Feline Herpes Virus Drop Test', price:800 },
    { name:'FIV/FeLV Test', price:1100 },
    { name:'Canine Distemper Virus Drop Test', price:800 },
    { name:'Canine Parvo Virus & Corona Virus Drop Test', price:800 },
    { name:'4-in-1 Test', price:1100 },
    { name:'Giardia Drop Test', price:800 }
  ],
  'Surgical Service': [
    { name:'Surgical Procedures', customPrice:true, medications:'', dosage:'', directions:'Quote per case — enter agreed price when saving.' }
  ],
  'Spaying & Castration': [
    { name:'Feline Spaying (Female)', price:8000 },
    { name:'Feline Castration (Male)', price:6000 },
    { name:'Canine Spaying (Female)', price:14000 },
    { name:'Canine Castration (Male)', price:12000 }
  ]
};

/**
 * Clinic fee rows for POS — SKU order must match bayport-backend inventory-services.json generation.
 */
window.buildPosProcedureRows = function buildPosProcedureRows() {
  const cat = window.PROCEDURE_CATALOG || {};
  const rows = [];
  let n = 0;
  for (const [section, items] of Object.entries(cat)) {
    if (!Array.isArray(items)) continue;
    for (const it of items) {
      n++;
      const sku = "SVC-" + String(n).padStart(3, "0");
      rows.push({
        sku,
        section,
        name: it.name,
        customPrice: !!it.customPrice,
        unitPrice: it.customPrice ? null : Number(it.price ?? 0),
        notes: it.notes || "",
      });
    }
  }
  return rows;
};

/* ===== Guard ===== */
window.guard=function(pageKey){
  window.ensureLoggedIn();
  const role=window.getRole();
  const allowed=CONFIG[role]||[];
  if(!allowed.includes(pageKey)) location.href="dashboard.html";
};

/* ===== Helpers for Pet Names etc. ===== */
window.petName = async function(id) {
  if (!id) return "Unknown Pet";
  try {
    const pet = await repoGetPet(id);
    return pet ? pet.name : "Unknown Pet";
  } catch (error) {
    console.warn(`Pet ${id} not found:`, error);
    return "Unknown Pet";
  }
};

window.ownerForPet = async function(id) {
  const pet = await repoGetPet(id);
  if(!pet) return "Unknown Owner";
  if (pet.owner) return pet.owner;
  if (pet.ownerId){ const o = await repoGetOwner(pet.ownerId); return o? o.fullName : "Unknown Owner"; }
  return "Unknown Owner";
};

