/* ===========================================================
   Bayport Veterinary Clinic — Main App Script (Frontend)
   =========================================================== */

window.tryLogin = async function(username, password, role, otp) {
  if (!role) return { ok: false, msg: "Please select a role first." };
  ensureApiEnabled();
  try {
    const payload = await Api.auth.login(username, password, otp);
    if (payload.status === "MFA_REQUIRED") {
      return {
        ok: false,
        msg: "MFA_REQUIRED",
        username: payload.username,
        emailConfigured: payload.emailConfigured !== false,
        mfaMessage: payload.message || "",
      };
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
    if (typeof window.trustCurrentDevice === "function" && !window.isDeviceLockEnabled()) {
      window.trustCurrentDevice("Auto-trusted on login");
    }
    if (typeof window.isDeviceLockEnabled === "function" && window.isDeviceLockEnabled()
        && typeof window.isCurrentDeviceAuthorized === "function" && !window.isCurrentDeviceAuthorized()) {
      return {
        ok: false,
        msg: "Unauthorized device. Ask an administrator to trust this device in Settings → Security.",
      };
    }
    return { ok: true };
  } catch (err) {
    const raw = err.message || "Unable to reach the server.";
    let msg = raw;
    if (raw.includes("Failed to fetch") || raw.includes("Cannot connect to backend")) {
      msg = "Cannot connect to backend server. Please ensure the application is running and the backend server has started.";
    } else if (raw.includes("401") && raw.length < 80) {
      msg = "Invalid username or password.";
    }
    return { ok: false, msg };
  }
};

/** Display pet age from ageDisplay, numeric age, or date of birth. */
window.formatPetAge = function formatPetAge(p) {
  if (!p) return "";
  if (p.ageDisplay && String(p.ageDisplay).trim()) return String(p.ageDisplay).trim();
  if (p.age != null && p.age !== "" && Number(p.age) > 0) {
    const n = Number(p.age);
    return `${n} yr${n === 1 ? "" : "s"}`;
  }
  if (p.dateOfBirth) {
    try {
      const dob = new Date(String(p.dateOfBirth).slice(0, 10) + "T12:00:00");
      const now = new Date();
      let months = (now.getFullYear() - dob.getFullYear()) * 12 + (now.getMonth() - dob.getMonth());
      if (now.getDate() < dob.getDate()) months -= 1;
      if (months < 12 && months >= 0) return `${months} month${months === 1 ? "" : "s"}`;
      const years = Math.floor(months / 12);
      if (years > 0) return `${years} yr${years === 1 ? "" : "s"}`;
    } catch (_) {}
  }
  return "";
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
  admin:        ["dashboard","pet-records","appointments","consultations","inventory","billing","pos","reports","reminders","activity-logs","recycle-bin","settings","manage-users","help"],
  /** Clinical focus: no billing, POS, inventory, or reports in nav (billing still flows from consultations). */
  vet:          ["dashboard","pet-records","appointments","consultations","help"],
  // Front Office = reception + pharmacy — no Consultations module (vet/clinical only).
  front_office: ["dashboard","pet-records","appointments","inventory","billing","pos","reminders","help"],
  receptionist: ["dashboard","pet-records","appointments","inventory","billing","pos","reminders","help"],
  pharmacist:   ["dashboard","pet-records","appointments","inventory","billing","pos","reminders","help"]
};

const LABEL = {
  "dashboard":"Dashboard",
  "pet-records":"Pet profiles",
  "appointments":"Appointments",
  "consultations":"Consultations & Rx",
  "prescriptions":"Consultations & Rx",
  "billing":"Billing",
  "pos":"Point of sale",
  "activity-logs":"Activity Logs",
  "recycle-bin":"Recycle Bin",
  "settings":"Settings",
  "inventory":"Inventory",
  "reports":"Reports",
  "reminders":"Reminders",
  "manage-users":"Users & Roles",
  "help":"Help & support"
};

/** Shorter labels in the sidebar only (less visual noise). */
const SIDEBAR_LABEL = {
  ...LABEL,
  "pet-records": "Pet profiles",
  "manage-users": "Users & roles",
  "billing": "Billing & payments"
};

/** Inline SVG (h-5 w-5) for sidebar rows — keeps actions recognizable at a glance. */
const SIDEBAR_ICONS = {
  dashboard: `<svg class="h-5 w-5 shrink-0 opacity-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"/></svg>`,
  "pet-records": `<svg class="h-5 w-5 shrink-0 opacity-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/></svg>`,
  appointments: `<svg class="h-5 w-5 shrink-0 opacity-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/></svg>`,
  consultations: `<svg class="h-5 w-5 shrink-0 opacity-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01"/></svg>`,
  prescriptions: `<svg class="h-5 w-5 shrink-0 opacity-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z"/></svg>`,
  billing: `<svg class="h-5 w-5 shrink-0 opacity-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 9V7a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2m2 4h10a2 2 0 002-2v-6a2 2 0 00-2-2H9a2 2 0 00-2 2v2a2 2 0 002 2z"/></svg>`,
  pos: `<svg class="h-5 w-5 shrink-0 opacity-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"/></svg>`,
  inventory: `<svg class="h-5 w-5 shrink-0 opacity-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"/></svg>`,
  reports: `<svg class="h-5 w-5 shrink-0 opacity-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"/></svg>`,
  reminders: `<svg class="h-5 w-5 shrink-0 opacity-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/></svg>`,
  settings: `<svg class="h-5 w-5 shrink-0 opacity-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/></svg>`,
  "manage-users": `<svg class="h-5 w-5 shrink-0 opacity-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"/></svg>`,
  "activity-logs": `<svg class="h-5 w-5 shrink-0 opacity-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>`,
  "recycle-bin": `<svg class="h-5 w-5 shrink-0 opacity-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg>`,
  help: `<svg class="h-5 w-5 shrink-0 opacity-90" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>`
};

const NAV_TOOLTIPS = {
  dashboard: "Overview and shortcuts for your role",
  "pet-records": "Search and open pet profiles",
  appointments: "Schedule and manage visits",
  consultations: "Visits, diagnosis, procedures, and prescriptions",
  prescriptions: "Visits, diagnosis, procedures, and prescriptions",
  billing: "Invoices and balances",
  pos: "Checkout and receipts",
  inventory: "Stock and catalog",
  reports: "Revenue and operational summaries",
  reminders: "Vaccination and pet owner email reminders",
  settings: "Clinic and system preferences",
  "manage-users": "Staff accounts and roles",
  "activity-logs": "Who changed what",
  "recycle-bin": "Restore deleted records",
  help: "FAQs, tips, and how to get support"
};

/**
 * Collapsible groups — Core workflow first; operations and admin settings below.
 */
const _SIDEBAR_FRONT_OFFICE = [
  { label: "Core workflow", open: true, keys: ["dashboard", "pet-records", "appointments"] },
  { label: "Operations", open: true, keys: ["billing", "pos", "inventory", "reminders"] },
  { label: "Help & support", open: false, keys: ["help"] }
];
const SIDEBAR_GROUPS = {
  admin: [
    { label: "Core workflow", open: true, keys: ["dashboard", "pet-records", "appointments", "consultations"] },
    { label: "Operations", open: true, keys: ["billing", "pos", "inventory", "reports", "reminders"] },
    { label: "Admin settings", open: false, keys: ["settings", "manage-users"] },
    { label: "Records & audit", open: false, keys: ["activity-logs", "recycle-bin"] },
    { label: "Help & support", open: false, keys: ["help"] }
  ],
  vet: [
    { label: "Core workflow", open: true, keys: ["dashboard", "pet-records", "appointments", "consultations"] },
    { label: "Help & support", open: false, keys: ["help"] }
  ],
  front_office: _SIDEBAR_FRONT_OFFICE,
  receptionist: _SIDEBAR_FRONT_OFFICE,
  pharmacist: _SIDEBAR_FRONT_OFFICE
};

const ROLE_COPY = {
  admin:{ welcome:"" },
  vet:{ welcome:"" },
  front_office:{ welcome:"" },
  receptionist:{ welcome:"" },
  pharmacist:{ welcome:"" }
};

window.getRole = function(){ return localStorage.getItem("role") || ""; };
window.getUserName = function(){ return localStorage.getItem("userDisplayName") || ""; };

/** Display title under the user name in the global header (not the same as the account role key). */
window.getBayportHeaderRoleTitle = function () {
  const r = String(window.getRole() || "").toLowerCase();
  if (r === "admin") return "Administrator";
  if (r === "vet") return "Vet";
  if (typeof window.isFrontOffice === "function" && window.isFrontOffice(r)) return "Staff";
  return "Staff";
};
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
const DEVICE_ID_KEY = "bayport_device_id";
const TRUSTED_DEVICES_KEY = "bayport_trusted_devices";
const DEVICE_LOCK_KEY = "bayport_device_lock_enabled";

window.getDeviceId = function () {
  let id = localStorage.getItem(DEVICE_ID_KEY);
  if (!id) {
    const raw = [navigator.userAgent, screen.width, screen.height, navigator.language].join("|");
    id = "dev_" + Array.from(raw).reduce((h, c) => ((h << 5) - h + c.charCodeAt(0)) | 0, 0).toString(36);
    localStorage.setItem(DEVICE_ID_KEY, id);
  }
  return id;
};

window.getTrustedDevices = function () {
  try {
    const list = JSON.parse(localStorage.getItem(TRUSTED_DEVICES_KEY) || "[]");
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
};

window.trustCurrentDevice = function (label) {
  const id = window.getDeviceId();
  const list = window.getTrustedDevices().filter((d) => d.id !== id);
  list.push({ id, label: label || "This device", trustedAt: new Date().toISOString() });
  localStorage.setItem(TRUSTED_DEVICES_KEY, JSON.stringify(list));
};

window.isDeviceLockEnabled = function () {
  return localStorage.getItem(DEVICE_LOCK_KEY) === "1";
};

window.setDeviceLockEnabled = function (on) {
  localStorage.setItem(DEVICE_LOCK_KEY, on ? "1" : "0");
};

window.isCurrentDeviceAuthorized = function () {
  if (!window.isDeviceLockEnabled()) return true;
  const id = window.getDeviceId();
  return window.getTrustedDevices().some((d) => d.id === id);
};

window.enforceDeviceAuthorization = function () {
  if (window.isCurrentDeviceAuthorized()) return true;
  alert("Unauthorized device. Ask an administrator to approve this device in Settings → Security.");
  if (typeof window.logout === "function") window.logout();
  else location.href = "index.html";
  return false;
};

/** True when Rx group can be edited (draft/saved or incomplete clinical fields). */
window.canEditPrescriptionGroup = function (groupRxs) {
  if (!Array.isArray(groupRxs) || !groupRxs.length) return false;
  const status = String(groupRxs[0].rxStatus || "SAVED").toUpperCase();
  if (status === "DRAFT" || status === "SAVED") return true;
  if (status === "DONE") {
    const first = groupRxs[0];
    const sig = first.directions && String(first.directions).trim();
    const incomplete =
      !first.drug ||
      !sig ||
      sig === "As directed" ||
      !first.dispenseQty ||
      !first.owner;
    return incomplete;
  }
  return false;
};

window.resolveVetLicenseNo = async function (prescriber) {
  const settings = window.getClinicSettings();
  if (settings.defaultVetLicenseNo && String(settings.defaultVetLicenseNo).trim()) {
    return String(settings.defaultVetLicenseNo).trim();
  }
  const name = String(prescriber || "").trim();
  if (!name) return "";
  try {
    if (window.Api && typeof window.Api.doctors?.list === "function") {
      const doctors = await window.Api.doctors.list();
      const match = (doctors || []).find(
        (d) => String(d.fullName || "").toLowerCase() === name.toLowerCase()
      );
      return match?.licenseNo ? String(match.licenseNo).trim() : "";
    }
  } catch (e) {
    console.warn("License lookup:", e);
  }
  return "";
};

window.formatPetLastVaccination = function (pet) {
  const v = typeof window.resolvePetLastVaccination === "function"
    ? window.resolvePetLastVaccination(pet)
    : pet;
  if (!v) return "";
  const parts = [];
  if (v.date) parts.push(String(v.date).slice(0, 10));
  if (v.place) parts.push(String(v.place).trim());
  if (v.vet) parts.push("Vet: " + String(v.vet).trim());
  return parts.join("  \u2022  ");
};

/** Resolve last vaccination date, place, and vet (stored fields or latest vaccination procedure). */
window.resolvePetLastVaccination = function (pet) {
  if (!pet) return { date: "", place: "", vet: "", summary: "" };
  let date = pet.lastVaccinationDate ? String(pet.lastVaccinationDate).slice(0, 10) : "";
  let place = pet.lastVaccinationPlace ? String(pet.lastVaccinationPlace).trim() : "";
  let vet = pet.lastVaccinationVet ? String(pet.lastVaccinationVet).trim() : "";
  if (!date) {
    const procs = pet.procedures || [];
    let latest = null;
    for (const pr of procs) {
      const d = pr?.performedAt ? String(pr.performedAt).slice(0, 10) : "";
      if (!d) continue;
      const cat = String(pr.category || "").toLowerCase();
      const nm = String(pr.name || "").toLowerCase();
      const notes = String(pr.notes || "").toLowerCase();
      const combined = nm + " " + notes + " " + cat;
      const isVacc =
        cat.includes("vaccin") ||
        combined.includes("vaccine") ||
        combined.includes("vaccination") ||
        combined.includes("rabies") ||
        combined.includes("dhppi") ||
        combined.includes("fvrcp");
      if (!isVacc) continue;
      if (!latest || d > latest.date) {
        latest = { date: d, vet: pr.vet ? String(pr.vet).trim() : "" };
      }
    }
    if (latest) {
      date = latest.date;
      if (!vet && latest.vet) vet = latest.vet;
      if (!place) place = "Bayport Veterinary Clinic, Para\u00f1aque City";
    }
  }
  const summaryParts = [];
  if (date) summaryParts.push(date);
  if (place) summaryParts.push(place);
  if (vet) summaryParts.push("Vet: " + vet);
  return {
    date,
    place,
    vet,
    summary: summaryParts.join("  \u2022  "),
  };
};

window.getClinicSettings = function() {
  const defaults = {
    name: "Bayport Veterinary Clinic",
    address: "0383 Quirino, Ave Don Galo, Parañaque City",
    logoDataUrl: "",
    rxBlankTemplate: "",
    defaultVetLicenseNo: "",
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
  if (typeof window.enforceDeviceAuthorization === "function") window.enforceDeviceAuthorization();
};

/** Unified Front Office + legacy role checks (for cloud JWT `front_office`). */
window.isFrontOffice = function(role) {
  const r = (role || "").toLowerCase();
  return r === "front_office" || r === "receptionist" || r === "pharmacist";
};

/** Map sub-pages to the sidebar item that should stay highlighted. */
function normalizeActiveFile(activeFile) {
  const f = String(activeFile || "").toLowerCase();
  if (f === "appointments-calendar.html" || f === "procedure.html") return "appointments.html";
  if (f === "reminders-calendar.html") return "reminders.html";
  return activeFile;
}

window.renderSidebar = function (container, role, activeFile) {
  container.innerHTML = "";
  activeFile = normalizeActiveFile(activeFile);

  const allowed = new Set(CONFIG[role] || []);
  const groups = SIDEBAR_GROUPS[role] || SIDEBAR_GROUPS.front_office;

  /** Single group with no section label → flat list without <details>. */
  const flatSingleGroup = groups.length === 1 && !String(groups[0].label || "").trim();

  groups.forEach((group) => {
    const keys = group.keys.filter((k) => allowed.has(k));
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
      const tip = NAV_TOOLTIPS[key];
      if (tip) btn.title = tip;
      btn.className =
        "w-full text-left px-3 py-2 text-sm rounded-lg text-gray-700 hover:bg-blue-50/90 hover:text-[var(--soft-teal)] transition-colors flex items-center gap-2.5";
      const iconHtml = SIDEBAR_ICONS[key] || "";
      btn.innerHTML = `${iconHtml}<span class="min-w-0 flex-1">${SIDEBAR_LABEL[key] || LABEL[key]}</span>`;
      btn.onclick = () => {
        const parentDet = btn.closest("details.sidebar-group");
        if (parentDet) parentDet.open = true;
        if (key === "prescriptions") {
          location.href = "consultations.html?tab=rx";
          return;
        }
        location.href = `${key}.html`;
      };
      if (`${key}.html` === activeFile) {
        btn.dataset.sidebarActive = "1";
        btn.className =
          "w-full text-left px-3 py-2 text-sm rounded-lg bg-[var(--soft-teal)] text-white font-medium shadow-sm flex items-center gap-2.5";
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
      if (!det.open && det.querySelector("button[data-sidebar-active='1']")) {
        det.open = true;
        return;
      }
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
 * Pet photo slot: solid Bayport blue when missing or on image error (no stock photo).
 * @param {string|null|undefined} photoPath
 * @param {string} wrapClass classes on outer wrapper (include size, e.g. w-full h-full rounded-xl)
 * @param {string} imgClass classes on img (e.g. object-cover w-full h-full)
 */
window.renderPetPhotoHtml = function renderPetPhotoHtml(photoPath, wrapClass, imgClass) {
  const trimmed = photoPath && String(photoPath).trim();
  const url = trimmed ? window.resolveAssetUrl(trimmed) : "";
  const fallback =
    `<div data-pet-photo-fallback class="absolute inset-0 bg-[var(--soft-teal)]" aria-hidden="true"></div>`;
  if (!url) {
    return `<div class="${wrapClass} relative overflow-hidden bg-[var(--soft-teal)]" data-pet-photo-wrap>${fallback}</div>`;
  }
  const esc = String(url).replace(/&/g, "&amp;").replace(/"/g, "&quot;");
  const fb = `<div data-pet-photo-fallback class="absolute inset-0 hidden bg-[var(--soft-teal)]" aria-hidden="true"></div>`;
  return `<div class="${wrapClass} relative overflow-hidden bg-[var(--soft-teal)]" data-pet-photo-wrap>${fb}<img src="${esc}" class="absolute inset-0 z-10 ${imgClass || "object-cover w-full h-full"}" alt="" decoding="async" onerror="this.remove();var w=this.closest('[data-pet-photo-wrap]');var f=w&&w.querySelector('[data-pet-photo-fallback]');if(f){f.classList.remove('hidden');}"></div>`;
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
const QA_ICON = {
  calendar: `<svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/></svg>`,
  clipboard: `<svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"/></svg>`,
  book: `<svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/></svg>`,
  cube: `<svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"/></svg>`,
  cash: `<svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 9V7a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2m2 4h10a2 2 0 002-2v-6a2 2 0 00-2-2H9a2 2 0 00-2 2v2a2 2 0 002 2z"/></svg>`,
  chart: `<svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"/></svg>`,
  users: `<svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"/></svg>`,
  cog: `<svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/></svg>`
};

window.renderQuickActions = function (el, role) {
  el.innerHTML = "";
  const make = (title, href, iconKey, { primary = false, subtitle = "" } = {}) => {
    const b = document.createElement("button");
    b.type = "button";
    b.title = title;
    const ic = QA_ICON[iconKey] || QA_ICON.clipboard;
    const base =
      "text-left w-full rounded-xl p-5 shadow-soft border flex gap-4 items-start transition-transform hover:scale-[1.01] active:scale-[0.99]";
    const style = primary
      ? `${base} bg-[var(--soft-teal)] text-white border-[var(--soft-teal)] ring-2 ring-offset-2 ring-[var(--soft-teal)]/30`
      : `${base} bg-[#f7fbfb] hover:bg-[#eef6f6] border-gray-200 text-gray-800`;
    const sub = subtitle ? `<div class="text-xs mt-1 ${primary ? "text-white/85" : "text-gray-500"}">${subtitle}</div>` : "";
    b.className = style;
    b.innerHTML = `<span class="shrink-0 ${primary ? "text-white" : "text-[var(--soft-teal)]"}">${ic}</span><div class="min-w-0"><div class="font-semibold leading-snug">${title}</div>${sub}</div>`;
    b.onclick = () => {
      location.href = href;
    };
    return b;
  };
  const fo = window.isFrontOffice(role);
  const map = {
    admin: [
      make("Start consultation", "consultations.html", "clipboard", { primary: true, subtitle: "Diagnosis and procedures" }),
      make("Create appointment", "appointments.html", "calendar", { subtitle: "Book the next visit" }),
      make("Pet profiles", "pet-records.html", "book"),
      make("Billing & payments", "billing.html", "cash"),
      make("Point of sale", "pos.html", "cash", { subtitle: "Fast checkout" }),
      make("Reports", "reports.html", "chart"),
      make("Inventory", "inventory.html", "cube"),
      make("Users & roles", "manage-users.html", "users"),
      make("Settings", "settings.html", "cog")
    ],
    vet: [
      make("Start consultation", "consultations.html", "clipboard", { primary: true, subtitle: "Step-by-step visit workflow" }),
      make("Appointments", "appointments.html", "calendar", { subtitle: "Today and upcoming" }),
      make("Pet profiles", "pet-records.html", "book", { subtitle: "Medical history and reminders" })
    ],
    front_office: [
      make("Create appointment", "appointments.html", "calendar", { primary: true, subtitle: "Book visits and manage queue" }),
      make("Pet profiles", "pet-records.html", "book"),
      make("Billing & payments", "billing.html", "cash"),
      make("Point of sale", "pos.html", "cash", { subtitle: "Checkout" }),
      make("Inventory", "inventory.html", "cube")
    ],
    receptionist: null,
    pharmacist: null
  };
  const rows = map[role] || map.front_office;
  (rows || []).forEach((btn) => el.appendChild(btn));
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
  if (pageKey === "prescriptions") pageKey = "consultations";
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

