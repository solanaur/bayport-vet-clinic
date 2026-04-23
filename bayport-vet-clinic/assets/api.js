window.USE_API = window.USE_API ?? true;
window.resolveApiBase = function resolveApiBase() {
  if (window.API_BASE && String(window.API_BASE).trim()) return String(window.API_BASE).trim();
  const envBase = window.BAYPORT_API_BASE || localStorage.getItem("bayport_api_base") || "";
  if (envBase) return String(envBase).trim();
  if (window.location.protocol === "file:") return "http://localhost:8080/api";
  return `${window.location.origin.replace(/\/+$/, "")}/api`;
};
window.API_BASE = window.resolveApiBase();
const API_TIMEOUT = 12000;
const API_DISABLED_ERR = "Backend integration is required. Please keep USE_API=true so the server endpoints remain reachable.";

window.formatPrice = function formatPrice(value) {
  const num = Number(value ?? 0);
  return num.toLocaleString("en-PH", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
};

function ensureApiEnabled() {
  if (!window.USE_API) {
    throw new Error(API_DISABLED_ERR);
  }
}

function buildApiUrl(path) {
  let base = String(window.API_BASE ?? "").trim().replace(/\/+$/, "");
  // "http://host:8080" with no path → append /api (otherwise /pos/checkout hits wrong handler → static 404)
  if (base.length > 0 && /^https?:\/\/[^/]+$/i.test(base)) {
    base += "/api";
  }
  if (!base) {
    base = window.resolveApiBase();
  }
  let p = path.startsWith("/") ? path : `/${path}`;
  if (base.endsWith("/api") && p.startsWith("/api/")) {
    p = p.slice(4);
  }
  return base + p;
}

window.ApiHttp = async function apiHttp(
  path,
  { method = "GET", headers = {}, body, timeoutMs = API_TIMEOUT, token } = {},
) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);

  const res = await fetch(buildApiUrl(path), {
    method,
    headers: {
      Accept: "application/json",
      ...(body instanceof FormData ? {} : { "Content-Type": "application/json" }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
    signal: ctrl.signal,
  }).catch((err) => {
    // Better error messages for connection issues
    if (err.name === "AbortError") {
      throw new Error("Request timeout - the backend server may be slow or unresponsive");
    } else if (typeof navigator !== "undefined" && navigator.onLine === false) {
      throw new Error("No Internet Connection.");
    } else if (err.message && err.message.includes("Failed to fetch")) {
      throw new Error("Cannot connect to backend server. Please ensure the application is running and the backend server has started.");
    } else if (err.message && err.message.includes("NetworkError")) {
      throw new Error("Network error - cannot reach the backend server at " + window.API_BASE);
    } else {
      throw new Error(err.message || "Connection error");
    }
  });

  clearTimeout(timer);

  if (!res.ok) {
    let textBody = "";
    let parsed = null;
    try {
      textBody = await res.text();
      parsed = textBody ? JSON.parse(textBody) : null;
    } catch {
      textBody = textBody || res.statusText;
    }
    if (parsed && typeof parsed === "object") {
      // Log structured backend error details for easier debugging (including "cause" when present).
      console.error("API error", {
        path: parsed.path,
        status: parsed.status,
        error: parsed.error,
        message: parsed.message,
        cause: parsed.cause,
      });
    } else {
      console.error("API error raw body:", textBody);
    }
    // Extract clean error message - prefer message field, fallback to error field, then status text
    let cleanMessage = parsed?.message || parsed?.error || textBody || res.statusText;
    if (parsed?.cause && typeof parsed.cause === "string" && parsed.cause.length > 0) {
      cleanMessage = `${cleanMessage} (${parsed.cause})`;
    }
    // Remove any JSON formatting artifacts
    if (typeof cleanMessage === 'string') {
      cleanMessage = cleanMessage.replace(/^400\s*-\s*\{?\s*/, '').replace(/\}?\s*$/, '');
      // If it's still JSON-like, try to parse it
      if (cleanMessage.trim().startsWith('{')) {
        try {
          const jsonMsg = JSON.parse(cleanMessage);
          cleanMessage = jsonMsg.message || jsonMsg.error || cleanMessage;
        } catch (e) {
          // Keep original if parsing fails
        }
      }
    }
    throw new Error(cleanMessage);
  }

  const text = await res.text();
  try {
    if (!text || text.trim() === '') {
      return null;
    }
    const parsed = JSON.parse(text);
    // If the response is an array, ensure it's always an array (not null)
    if (Array.isArray(parsed)) {
      return parsed;
    }
    return parsed;
  } catch {
    return text;
  }
};

window.Api = {
  token() {
    return localStorage.getItem("jwt") || null;
  },

  health: {
    check: () => ApiHttp("/health"),
  },

  auth: {
    login: (username, password, otp) => ApiHttp("/auth/login", {
      method: "POST",
      body: { username, password },
    }),
    verifyMfa: (username, code) => ApiHttp("/auth/mfa/verify", {
      method: "POST",
      body: { username, code },
    }),
  },

  notifications: {
    list: () => ApiHttp("/notifications", { token: Api.token() }),
    getAll: () => ApiHttp("/notifications/all", { token: Api.token() }),
    markRead: (id) => ApiHttp(`/notifications/${id}/read`, { 
      method: "POST", 
      token: Api.token() 
    }),
    markAllRead: () => ApiHttp("/notifications/read-all", { 
      method: "POST", 
      token: Api.token() 
    }),
    getCount: () => ApiHttp("/notifications/count", { token: Api.token() }),
  },

  pets: {
    list: () => ApiHttp("/pets", { token: Api.token() }),
    get: (id) => ApiHttp(`/pets/${id}`, { token: Api.token() }),
    create: (pet) => ApiHttp("/pets", { method: "POST", body: pet, token: Api.token() }),
    update: (pet) => ApiHttp(`/pets/${pet.id}`, { method: "PUT", body: pet, token: Api.token() }),
    remove: (id) => ApiHttp(`/pets/${id}`, { method: "DELETE", token: Api.token() }),
    uploadPhoto: (id, file) => {
      const fd = new FormData();
      fd.append("file", file);
      return ApiHttp(`/pets/${id}/photo`, { method: "POST", body: fd, token: Api.token() });
    },
    addProcedure: (id, proc) => ApiHttp(`/pets/${id}/procedures`, {
      method: "POST",
      body: proc,
      token: Api.token(),
    }),
    updateProcedure: (petId, procedureId, body) => ApiHttp(`/pets/${petId}/procedures/${procedureId}`, {
      method: "PUT",
      body,
      token: Api.token(),
    }),
    deleteProcedure: (petId, procedureId) => ApiHttp(`/pets/${petId}/procedures/${procedureId}`, {
      method: "DELETE",
      token: Api.token(),
    }),
  },

  procedures: {
    create: (petId, procedure) => ApiHttp(`/pets/${petId}/procedures`, {
      method: "POST",
      body: procedure,
      token: Api.token(),
    }),
    update: (petId, procedureId, procedure) => ApiHttp(`/pets/${petId}/procedures/${procedureId}`, {
      method: "PUT",
      body: procedure,
      token: Api.token(),
    }),
    delete: (petId, procedureId) => ApiHttp(`/pets/${petId}/procedures/${procedureId}`, {
      method: "DELETE",
      token: Api.token(),
    }),
  },

  reminders: {
    list() {
      return ApiHttp("/reminders", { token: Api.token() });
    },
    listByPet(petId) {
      return ApiHttp(`/reminders/pet/${petId}`, { token: Api.token() });
    },
    add(data) {
      return ApiHttp("/reminders", {
        method: "POST",
        body: data,
        token: Api.token(),
      });
    },
    remove(id) {
      return ApiHttp(`/reminders/${id}`, {
        method: "DELETE",
        token: Api.token(),
      });
    },
    sendEmail(id, payload) {
      return ApiHttp(`/reminder-email/${id}`, {
        method: "POST",
        body: payload,
        token: Api.token(),
      });
    },
  },


  owners: {
    list: () => ApiHttp("/owners", { token: Api.token() }),
    search: (term) => {
      const q = new URLSearchParams({ q: term ?? "" }).toString();
      return ApiHttp(`/owners/search?${q}`, { token: Api.token() });
    },
    get: (id) => ApiHttp(`/owners/${id}`, { token: Api.token() }),
    create: (owner) => ApiHttp("/owners", { method: "POST", body: owner, token: Api.token() }),
    update: (owner) => ApiHttp(`/owners/${owner.id}`, { method: "PUT", body: owner, token: Api.token() }),
    remove: (id) => ApiHttp(`/owners/${id}`, { method: "DELETE", token: Api.token() }),
  },

  appts: {
    list: (currentUser) => {
      const url = currentUser ? `/appointments?currentUser=${encodeURIComponent(currentUser)}` : "/appointments";
      return ApiHttp(url, { token: Api.token() });
    },
    listByDate: (date) => ApiHttp(`/appointments?date=${encodeURIComponent(date)}`, { token: Api.token() }),
    listForVet: (name) => ApiHttp(`/appointments?vet=${encodeURIComponent(name)}`, { token: Api.token() }),
    listUnassigned: () => ApiHttp("/appointments?unassigned=true", { token: Api.token() }),
    get: (id) => ApiHttp(`/appointments/${id}`, { token: Api.token() }),
    create: (appt) => ApiHttp("/appointments", { method: "POST", body: appt, token: Api.token() }),
    update: (appt) => ApiHttp(`/appointments/${appt.id}`, { method: "PUT", body: appt, token: Api.token() }),
    approve: (id) => ApiHttp(`/appointments/${id}/approve`, { method: "POST", token: Api.token() }),
    done: (id) => ApiHttp(`/appointments/${id}/done`, { method: "POST", token: Api.token() }),
    remove: (id) => ApiHttp(`/appointments/${id}`, { method: "DELETE", token: Api.token() }),
  },

  rx: {
    list: () => ApiHttp("/prescriptions", { token: Api.token() }),
    get: (id) => ApiHttp(`/prescriptions/${id}`, { token: Api.token() }),
    create: (rx) => ApiHttp("/prescriptions", { method: "POST", body: rx, token: Api.token() }),
    update: (rx) => ApiHttp(`/prescriptions/${rx.id}`, { method: "PUT", body: rx, token: Api.token() }),
    remove: (id) => ApiHttp(`/prescriptions/${id}`, { method: "DELETE", token: Api.token() }),
    dispense: (id) => ApiHttp(`/prescriptions/${id}/dispense`, { method: "POST", token: Api.token() }),
    archive: (id, archived) => ApiHttp(`/prescriptions/${id}/archive?archived=${archived}`, {
      method: "PATCH",
      token: Api.token(),
    }),
  },

  users: {
    list: () => ApiHttp("/users", { token: Api.token() }),
    get: (id) => ApiHttp(`/users/${id}`, { token: Api.token() }),
    create: (user) => ApiHttp("/users", { method: "POST", body: user, token: Api.token() }),
    update: (user) => ApiHttp(`/users/${user.id}`, { method: "PUT", body: user, token: Api.token() }),
    remove: (id) => ApiHttp(`/users/${id}`, { method: "DELETE", token: Api.token() }),
  },

  reports: {
    summary: (period, from, to) => {
      const params = new URLSearchParams({
        period,
        ...(from ? { from } : {}),
        ...(to ? { to } : {}),
      }).toString();
      return ApiHttp(`/reports/summary?${params}`, { token: Api.token() });
    },
    log: (from, to) => {
      const params = new URLSearchParams({ from, to }).toString();
      return ApiHttp(`/ops/log?${params}`, { token: Api.token() });
    },
  },

  inventory: {
    list: () => ApiHttp("/inventory", { token: Api.token() }),
    get: (id) => ApiHttp(`/inventory/${id}`, { token: Api.token() }),
    create: (item) => ApiHttp("/inventory", { method: "POST", body: item, token: Api.token() }),
    update: (item) => ApiHttp(`/inventory/${item.id}`, { method: "PUT", body: item, token: Api.token() }),
    remove: (id) => ApiHttp(`/inventory/${id}`, { method: "DELETE", token: Api.token() }),
    mergeCatalog: () =>
      ApiHttp("/inventory/merge-catalog", { method: "POST", token: Api.token() }),
  },

  billing: {
    list: () => ApiHttp("/billing", { token: Api.token() }),
    get: (id) => ApiHttp(`/billing/${id}`, { token: Api.token() }),
    create: (record) => ApiHttp("/billing", { method: "POST", body: record, token: Api.token() }),
    pay: (id) => ApiHttp(`/billing/${id}/pay`, { method: "POST", token: Api.token() }),
    remove: (id) => ApiHttp(`/billing/${id}`, { method: "DELETE", token: Api.token() }),
  },

  sales: {
    list: () => ApiHttp("/sales", { token: Api.token() }),
    summary: (days = 7) => ApiHttp(`/sales/summary?days=${days}`, { token: Api.token() }),
    posHistory: (limit = 40) =>
      ApiHttp(`/reports/pos-sales-recent?limit=${limit}`, { token: Api.token() }),
    receipt: (saleId) => ApiHttp(`/sales/${saleId}/receipt`, { token: Api.token() }),
    stream: () => new EventSource(`${window.API_BASE}/sales/stream`),
  },

  pos: {
    checkout: (body) =>
      ApiHttp("/sales/checkout", { method: "POST", body, token: Api.token() }),
  },
  printJobs: {
    enqueue: (body) => ApiHttp("/print-jobs", { method: "POST", body, token: Api.token() }),
    poll: (branchCode) => ApiHttp(`/print-jobs/poll?branchCode=${encodeURIComponent(branchCode)}`, { token: Api.token() }),
    markProcessing: (id) => ApiHttp(`/print-jobs/${id}/processing`, { method: "POST", token: Api.token() }),
    markPrinted: (id) => ApiHttp(`/print-jobs/${id}/printed`, { method: "POST", token: Api.token() }),
    markFailed: (id, error) => ApiHttp(`/print-jobs/${id}/failed`, { method: "POST", body: { error }, token: Api.token() }),
  },

  doctors: {
    list: () => ApiHttp("/doctors", { token: Api.token() }),
    get: (id) => ApiHttp(`/doctors/${id}`, { token: Api.token() }),
    create: (doctor) => ApiHttp("/doctors", { method: "POST", body: doctor, token: Api.token() }),
    update: (doctor) => ApiHttp(`/doctors/${doctor.id}`, { method: "PUT", body: doctor, token: Api.token() }),
    remove: (id) => ApiHttp(`/doctors/${id}`, { method: "DELETE", token: Api.token() }),
  },
  email: {
    send: (to, subject, message) =>
      ApiHttp("/email/send", {
        method: "POST",
        body: { to, subject, message },
        token: Api.token(),
      }),

    templates: {
      list: (type) =>
        ApiHttp(`/email/templates/${(type || "PET").toUpperCase()}`, {
          method: "GET",
          token: Api.token(),
        }),
    },
  },
};


const DB_KEY = "bayport_recent";
function db() {
  const raw = localStorage.getItem(DB_KEY);
  return raw ? JSON.parse(raw) : { recent: [] };
}
function saveRecent(data) {
  localStorage.setItem(DB_KEY, JSON.stringify(data));
}
function pushRecent(text) {
  const data = db();
  // Get current user name from localStorage
  const userName = localStorage.getItem('userDisplayName') || localStorage.getItem('username') || 'System';
  const textWithUser = `${userName} ${text}`;
  data.recent.unshift({ text: textWithUser, ts: Date.now() });
  data.recent = data.recent.slice(0, 20);
  saveRecent(data);
}
function clearRecent() {
  saveRecent({ recent: [] });
}

window.renderRecent = function renderRecent(el) {
  const entries = db().recent;
  const clearBtn = document.createElement("div");
  clearBtn.className = "mb-2";
  clearBtn.innerHTML = `<button class='border px-3 py-1 rounded-md text-sm hover:bg-gray-50' onclick='window._clearRecent()'>Clear recent activity</button>`;
  el.innerHTML = "";
  el.appendChild(clearBtn);
  const wrap = document.createElement("div");
  wrap.innerHTML = entries.length
    ? entries.map((i) => `<div class='text-sm'>${new Date(i.ts).toLocaleString()} — ${i.text}</div>`).join("")
    : `<div class='text-gray-500'>Nothing yet.</div>`;
  el.appendChild(wrap);
};

window._clearRecent = () => {
  clearRecent();
  const el = document.getElementById("recentList");
  if (el) {
    window.renderRecent(el);
  }
};

async function repoListPets() {
  ensureApiEnabled();
  return Api.pets.list();
}

async function repoGetPet(id) {
  ensureApiEnabled();
  try {
    return await Api.pets.get(id);
  } catch (error) {
    // Handle 404 or other errors gracefully
    if (error.message && error.message.includes('404')) {
      return null;
    }
    throw error;
  }
}

async function repoAddPet(pet, file) {
  ensureApiEnabled();
  const created = await Api.pets.create(pet);
  let finalPet = created;
  if (file) {
    try {
      await Api.pets.uploadPhoto(created.id, file);
      finalPet = await Api.pets.get(created.id);
    } catch (err) {
      console.warn("Photo upload failed:", err.message);
    }
  }
  return finalPet;
}

async function repoUpdatePet(pet, photoFile) {
  ensureApiEnabled();
  const updated = await Api.pets.update(pet);
  let finalPet = updated;
  if (photoFile) {
    try {
      await Api.pets.uploadPhoto(pet.id, photoFile);
      finalPet = await Api.pets.get(pet.id);
    } catch (err) {
      console.warn("Photo upload failed:", err.message);
    }
  }
  return finalPet;
}

async function repoDeletePet(id) {
  ensureApiEnabled();
  await Api.pets.remove(id);
}

async function repoListOwners() {
  ensureApiEnabled();
  return Api.owners.list();
}

async function repoSearchOwners(term) {
  ensureApiEnabled();
  return Api.owners.search(term);
}

async function repoGetOwner(id) {
  ensureApiEnabled();
  return Api.owners.get(id);
}

async function repoAddOwner(owner) {
  ensureApiEnabled();
  const created = await Api.owners.create(owner);
  return created;
}

async function repoUpdateOwner(owner) {
  ensureApiEnabled();
  const updated = await Api.owners.update(owner);
  return updated;
}

async function repoListAppts() {
  ensureApiEnabled();
  return Api.appts.list();
}

async function repoGetAppt(id) {
  ensureApiEnabled();
  return Api.appts.get(id);
}

async function repoAddAppt(appt) {
  ensureApiEnabled();
  const created = await Api.appts.create(appt);
  return created;
}

async function repoUpdateAppt(appt) {
  ensureApiEnabled();
  const updated = await Api.appts.update(appt);
  return updated;
}

async function repoDeleteAppt(id) {
  ensureApiEnabled();
  await Api.appts.remove(id);
}

async function repoApproveAppt(id) {
  ensureApiEnabled();
  await Api.appts.approve(id);
}

async function repoDoneAppt(id) {
  ensureApiEnabled();
  await Api.appts.done(id);
}

async function repoListRx() {
  ensureApiEnabled();
  return Api.rx.list();
}

async function repoGetRx(id) {
  ensureApiEnabled();
  return Api.rx.get(id);
}

async function repoAddRx(rx) {
  ensureApiEnabled();
  const created = await Api.rx.create(rx);
  return created;
}

async function repoUpdateRx(rx) {
  ensureApiEnabled();
  const updated = await Api.rx.update(rx);
  return updated;
}

async function repoDeleteRx(id) {
  ensureApiEnabled();
  await Api.rx.remove(id);
}

async function repoDispenseRx(id) {
  ensureApiEnabled();
  await Api.rx.dispense(id);
}

async function repoArchiveRx(id, archived = true) {
  ensureApiEnabled();
  await Api.rx.archive(id, archived);
}

async function repoListUsers() {
  ensureApiEnabled();
  try {
    const result = await Api.users.list();
    console.log('repoListUsers result:', result);
    console.log('repoListUsers result type:', typeof result);
    console.log('repoListUsers is array?', Array.isArray(result));
    return result;
  } catch (error) {
    console.error('repoListUsers error:', error);
    throw error;
  }
}

async function repoGetUser(id) {
  ensureApiEnabled();
  return Api.users.get(id);
}

async function repoAddUser(user) {
  ensureApiEnabled();
  const created = await Api.users.create(user);
  return created;
}

async function repoUpdateUser(user) {
  ensureApiEnabled();
  const updated = await Api.users.update(user);
  return updated;
}

async function repoDeleteUser(id) {
  ensureApiEnabled();
  await Api.users.remove(id);
}

async function repoSummary(period, from, to) {
  ensureApiEnabled();
  return Api.reports.summary(period, from, to);
}

async function repoAddProcedure(petId, procedure) {
  ensureApiEnabled();
  procedure.vet = typeof getUserName === "function" ? getUserName() : procedure.vet;
  await Api.pets.addProcedure(petId, procedure);
}

async function repoUpdateProcedure(petId, procedureId, updated) {
  ensureApiEnabled();
  await Api.pets.updateProcedure(petId, procedureId, updated);
}

async function repoDeleteProcedure(petId, procedureId) {
  ensureApiEnabled();
  await Api.pets.deleteProcedure(petId, procedureId);
}

window.repoCreateAppt = repoAddAppt;
window.repoCreateRx = repoAddRx;
window.repoCreateUser = repoAddUser;

window.fileToDataURL = function fileToDataURL(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = (e) => resolve(e.target.result);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
};

