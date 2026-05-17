/**
 * Editable Bayport prescription pad (same layout as PDF). Type directly on the template.
 */
(function (g) {
  const CLINIC = {
    name: "BAYPORT VETERINARY CLINIC",
    address: "0383 Quirino, Ave Don Galo, Para\u00f1aque City",
    phone: "09686332940 / tel no. (02) 82548338",
    email: "bayport.vetclinic@gmail.com",
  };

  function rxBullet(index) {
    const n = index + 1;
    if (n >= 1 && n <= 20) return String.fromCharCode(0x2460 + n - 1);
    return n + ".";
  }

  function logoUrl() {
    try {
      return new URL("assets/logo.png", g.location.href).href;
    } catch (_) {
      return "assets/logo.png";
    }
  }

  function defaultLine() {
    return { drug: "", dosage: "", directions: "", dispenseQty: "" };
  }

  function normalizeData(raw) {
    const d = raw || {};
    return {
      petId: d.petId || "",
      pet: d.pet || "",
      owner: d.owner || "",
      date: d.date || new Date().toISOString().slice(0, 10),
      prescriber: d.prescriber || "",
      followUp: d.followUp || d.daysSupply || "",
      rxStatus: d.rxStatus || "SAVED",
      lines: (d.lines && d.lines.length ? d.lines : [defaultLine()]).map((l) => ({
        id: l.id || null,
        drug: l.drug || "",
        dosage: l.dosage || "",
        directions: l.directions || l.sig || "",
        dispenseQty: l.dispenseQty != null && l.dispenseQty !== "" ? String(l.dispenseQty) : "",
      })),
    };
  }

  function medBlockHtml(idx, line) {
    const qty = line.dispenseQty ? line.dispenseQty : "";
    return `
      <div class="rx-pad-med-block" data-med-idx="${idx}">
        <button type="button" class="rx-pad-med-remove" title="Remove this medicine" aria-label="Remove medicine ${idx + 1}">&times;</button>
        <div class="rx-pad-med-row">
          <span class="rx-pad-bullet">${rxBullet(idx)}</span>
          <span class="rx-pad-med-wrap">
            <input type="text" class="rx-pad-med-name" data-field="drug" value="${escAttr(line.drug)}" placeholder="Medicine name" aria-label="Medicine ${idx + 1}" autocomplete="off">
            <ul class="rx-pad-med-suggest" role="listbox" hidden></ul>
          </span>
          <span class="text-[11pt] shrink-0">#</span>
          <input type="text" class="rx-pad-med-qty" data-field="qty" value="${escAttr(qty)}" placeholder="1" aria-label="Quantity" style="width:2.5em">
        </div>
        <div class="rx-pad-sig-row">
          <span>Sig:</span>
          <input type="text" class="rx-pad-sig" data-field="sig" value="${escAttr(line.directions)}" placeholder="Instructions" aria-label="Sig ${idx + 1}">
        </div>
      </div>`;
  }

  function escAttr(s) {
    return String(s ?? "")
      .replace(/&/g, "&amp;")
      .replace(/"/g, "&quot;")
      .replace(/</g, "&lt;");
  }

  let drugCatalogCache = null;
  let drugCatalogPromise = null;

  function loadDrugCatalog() {
    if (drugCatalogPromise) return drugCatalogPromise;
    drugCatalogPromise = (async () => {
      if (drugCatalogCache) return drugCatalogCache;
      try {
        if (g.Api && typeof g.Api.inventory?.list === "function") {
          const inv = await g.Api.inventory.list();
          drugCatalogCache = (inv || [])
            .filter((i) => String(i?.category || "").trim().toUpperCase() !== "SERVICE")
            .map((i) => String(i?.name || "").trim())
            .filter(Boolean)
            .sort((a, b) => a.localeCompare(b, undefined, { sensitivity: "base" }));
          return drugCatalogCache;
        }
      } catch (e) {
        console.warn("Rx pad: inventory autocomplete unavailable", e);
      }
      drugCatalogCache = [];
      return drugCatalogCache;
    })();
    return drugCatalogPromise;
  }

  function filterDrugSuggestions(query, catalog) {
    const q = String(query || "").trim().toLowerCase();
    if (!q) return catalog.slice(0, 12);
    return catalog.filter((n) => n.toLowerCase().includes(q)).slice(0, 12);
  }

  function filterPetSuggestions(query, pets) {
    const q = String(query || "").trim().toLowerCase();
    if (!q) return (pets || []).slice(0, 12);
    return (pets || [])
      .filter((p) => {
        const name = String(p.name || "").toLowerCase();
        const owner = String(p.owner || "").toLowerCase();
        return name.includes(q) || owner.includes(q);
      })
      .slice(0, 12);
  }

  function attachPetAutocomplete(root, pets, opts) {
    const wrap = root.querySelector(".rx-pad-pet-wrap");
    if (!wrap || !pets?.length) return;
    if (wrap.dataset.rxPetAcBound === "1") return;
    wrap.dataset.rxPetAcBound = "1";
    const input = wrap.querySelector('[data-field="pet"]');
    const list = wrap.querySelector(".rx-pad-pet-suggest");
    const ownerInp = root.querySelector('[data-field="owner"]');
    if (!input || !list) return;

    let activeIdx = -1;

    function hideList() {
      list.hidden = true;
      list.innerHTML = "";
      activeIdx = -1;
    }

    async function applyPet(pet) {
      if (!pet) return;
      input.value = pet.name || "";
      input.dataset.petId = String(pet.id || "");
      let owner = pet.owner || "";
      if (typeof g.ownerForPet === "function" && pet.id) {
        try {
          owner = (await g.ownerForPet(pet.id)) || owner;
        } catch (_) { /* ignore */ }
      }
      if (ownerInp) ownerInp.value = owner;
      hideList();
      input.dispatchEvent(new Event("input", { bubbles: true }));
      if (typeof opts.onPetSelect === "function") opts.onPetSelect(pet);
    }

    function showSuggestions(items) {
      if (!items.length) {
        hideList();
        return;
      }
      list.innerHTML = items
        .map(
          (p, i) =>
            `<li role="option" data-idx="${i}" data-pet-id="${escAttr(p.id)}">${escAttr(p.name)}${p.owner ? ` <span class="text-gray-500">(${escAttr(p.owner)})</span>` : ""}</li>`
        )
        .join("");
      list.hidden = false;
      activeIdx = -1;
    }

    input.addEventListener("input", () => {
      delete input.dataset.petId;
      showSuggestions(filterPetSuggestions(input.value, pets));
    });

    input.addEventListener("keydown", (e) => {
      const options = list.querySelectorAll("li");
      if (e.key === "Tab" && !list.hidden && options.length) {
        e.preventDefault();
        const li = options[activeIdx >= 0 ? activeIdx : 0];
        const pet = pets.find((p) => String(p.id) === li?.dataset?.petId);
        if (pet) applyPet(pet);
        return;
      }
      if (e.key === "Enter" && !list.hidden && options.length) {
        e.preventDefault();
        const li = options[activeIdx >= 0 ? activeIdx : 0];
        const pet = pets.find((p) => String(p.id) === li?.dataset?.petId);
        if (pet) applyPet(pet);
        return;
      }
      if (list.hidden || !options.length) return;
      if (e.key === "ArrowDown") {
        e.preventDefault();
        activeIdx = Math.min(activeIdx + 1, options.length - 1);
        options.forEach((el, i) => el.setAttribute("aria-selected", i === activeIdx ? "true" : "false"));
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        activeIdx = Math.max(activeIdx - 1, 0);
        options.forEach((el, i) => el.setAttribute("aria-selected", i === activeIdx ? "true" : "false"));
      } else if (e.key === "Escape") {
        hideList();
      }
    });

    list.addEventListener("mousedown", (e) => {
      const li = e.target.closest("li");
      if (!li) return;
      e.preventDefault();
      const pet = pets.find((p) => String(p.id) === li.dataset.petId);
      if (pet) applyPet(pet);
    });

    input.addEventListener("blur", () => {
      setTimeout(hideList, 150);
    });
  }

  function attachMedAutocomplete(container, catalog) {
    container.querySelectorAll(".rx-pad-med-wrap").forEach((wrap) => {
      if (wrap.dataset.rxAcBound === "1") return;
      wrap.dataset.rxAcBound = "1";
      const input = wrap.querySelector(".rx-pad-med-name");
      const list = wrap.querySelector(".rx-pad-med-suggest");
      if (!input || !list) return;

      let activeIdx = -1;

      function hideList() {
        list.hidden = true;
        list.innerHTML = "";
        activeIdx = -1;
      }

      function showSuggestions(items) {
        if (!items.length) {
          hideList();
          return;
        }
        list.innerHTML = items
          .map((name, i) => `<li role="option" data-idx="${i}">${escAttr(name)}</li>`)
          .join("");
        list.hidden = false;
        activeIdx = -1;
      }

      function pick(name) {
        input.value = name;
        hideList();
        input.dispatchEvent(new Event("input", { bubbles: true }));
      }

      input.addEventListener("input", () => {
        showSuggestions(filterDrugSuggestions(input.value, catalog));
      });

      input.addEventListener("keydown", (e) => {
        const opts = list.querySelectorAll("li");
        if (e.key === "Tab" && !list.hidden && opts.length) {
          e.preventDefault();
          pick(opts[activeIdx >= 0 ? activeIdx : 0].textContent);
          return;
        }
        if (e.key === "Enter" && !list.hidden && opts.length) {
          e.preventDefault();
          pick(opts[activeIdx >= 0 ? activeIdx : 0].textContent);
          const block = input.closest("[data-med-idx]");
          block?.querySelector('[data-field="qty"]')?.focus();
          return;
        }
        if (list.hidden || !opts.length) return;
        if (e.key === "ArrowDown") {
          e.preventDefault();
          activeIdx = Math.min(activeIdx + 1, opts.length - 1);
          opts.forEach((el, i) => el.setAttribute("aria-selected", i === activeIdx ? "true" : "false"));
        } else if (e.key === "ArrowUp") {
          e.preventDefault();
          activeIdx = Math.max(activeIdx - 1, 0);
          opts.forEach((el, i) => el.setAttribute("aria-selected", i === activeIdx ? "true" : "false"));
        } else if (e.key === "Escape") {
          hideList();
        }
      });

      list.addEventListener("mousedown", (e) => {
        const li = e.target.closest("li");
        if (li) {
          e.preventDefault();
          pick(li.textContent);
        }
      });

      input.addEventListener("blur", () => {
        setTimeout(hideList, 150);
      });
    });
  }

  function sheetHtml(data, opts) {
    const showPicker = opts.showPetPicker !== false;
    const medBlocks = data.lines.map((l, i) => medBlockHtml(i, l)).join("");
    return `
      ${showPicker ? `
      <div class="rx-pad-toolbar" data-rx-toolbar>
        <label class="text-sm font-medium text-gray-700">Pet
          <select data-rx-pet-select class="ml-1">${(opts.pets || [])
            .map((p) => `<option value="${p.id}" ${String(p.id) === String(data.petId) ? "selected" : ""}>${escAttr(p.name)}</option>`)
            .join("")}</select>
        </label>
        <label class="text-sm text-gray-600">Status
          <select data-rx-status class="ml-1 border rounded px-2 py-1">
            <option value="DRAFT" ${data.rxStatus === "DRAFT" ? "selected" : ""}>Draft</option>
            <option value="SAVED" ${data.rxStatus === "SAVED" ? "selected" : ""}>Saved</option>
            <option value="DONE" ${data.rxStatus === "DONE" ? "selected" : ""}>Done</option>
          </select>
        </label>
      </div>` : ""}
      <article class="rx-pad-sheet" data-rx-pad-sheet>
        <header class="rx-pad-header">
          <img src="${logoUrl()}" alt="" class="rx-pad-logo" width="82" height="82">
          <div class="rx-pad-clinic-block">
            <h1>${CLINIC.name}</h1>
            <p>${CLINIC.address}</p>
            <p>Contact No: ${CLINIC.phone}</p>
            <p>Email: ${CLINIC.email}</p>
          </div>
        </header>
        <hr class="rx-pad-rule">
        <div class="rx-pad-field-row">
          <label class="rx-pad-pet-label">Pet's Name:
            <span class="rx-pad-pet-wrap">
              <input type="text" class="rx-pad-field" data-field="pet" data-pet-id="${escAttr(data.petId)}" value="${escAttr(data.pet)}" placeholder="Type pet name" autocomplete="off">
              <ul class="rx-pad-pet-suggest" role="listbox" hidden></ul>
            </span>
          </label>
          <label class="rx-pad-date-field">Date: <input type="date" class="rx-pad-field" data-field="date" value="${escAttr(data.date)}"></label>
        </div>
        <div class="rx-pad-field-row">
          <label>Owner's Name: <input type="text" class="rx-pad-field" data-field="owner" value="${escAttr(data.owner)}" placeholder="Owner name"></label>
        </div>
        <div class="rx-pad-meds-area">
          <div class="rx-pad-rx-symbol" aria-hidden="true">Rx</div>
          <p class="rx-pad-meds-hint">Type pet name for suggestions; owner fills automatically. Enter on Sig for the next medicine.</p>
          <div data-rx-meds>${medBlocks}</div>
        </div>
        <footer class="rx-pad-footer">
          <div class="rx-pad-footer-row">
            <div class="rx-pad-footer-col rx-pad-followup">
              <label>Follow up: <input type="text" class="rx-pad-field rx-pad-footer-field" data-field="followUp" value="${escAttr(data.followUp)}"></label>
            </div>
            <div class="rx-pad-footer-col rx-pad-signature">
              <div class="rx-pad-signature-line" aria-hidden="true"></div>
              <div class="rx-pad-signature-label">Veterinarian</div>
            </div>
          </div>
          <div class="rx-pad-page" aria-hidden="true">Page 1 of 1</div>
        </footer>
      </article>`;
  }

  function bindEditor(root, opts) {
    const data = normalizeData(opts.initialData);
    const medsEl = root.querySelector("[data-rx-meds]");
    const petSel = root.querySelector("[data-rx-pet-select]");
    const statusSel = root.querySelector("[data-rx-status]");

    function reindexMeds() {
      medsEl.querySelectorAll("[data-med-idx]").forEach((block, i) => {
        block.setAttribute("data-med-idx", String(i));
        const bullet = block.querySelector(".rx-pad-bullet");
        if (bullet) bullet.textContent = rxBullet(i);
      });
    }

    let drugCatalog = [];

    function removeMedLine(block) {
      if (!block) return;
      const blocks = medsEl.querySelectorAll("[data-med-idx]");
      if (blocks.length <= 1) {
        block.querySelector('[data-field="drug"]').value = "";
        block.querySelector('[data-field="qty"]').value = "";
        block.querySelector('[data-field="sig"]').value = "";
        return;
      }
      const focusTarget = block.nextElementSibling || block.previousElementSibling;
      block.remove();
      reindexMeds();
      focusTarget?.querySelector('[data-field="drug"]')?.focus();
    }

    function isLineEmpty(block) {
      const drug = block.querySelector('[data-field="drug"]')?.value?.trim() || "";
      const qty = block.querySelector('[data-field="qty"]')?.value?.trim() || "";
      const sig = block.querySelector('[data-field="sig"]')?.value?.trim() || "";
      return !drug && !qty && !sig;
    }

    function wireMedLineKeys(block) {
      if (block.dataset.rxKeysBound === "1") return;
      block.dataset.rxKeysBound = "1";

      const drug = block.querySelector('[data-field="drug"]');
      const qty = block.querySelector('[data-field="qty"]');
      const sig = block.querySelector('[data-field="sig"]');
      const list = block.querySelector(".rx-pad-med-suggest");

      block.querySelector(".rx-pad-med-remove")?.addEventListener("click", () => removeMedLine(block));

      drug?.addEventListener("keydown", (e) => {
        if (e.key === "Enter" && list && !list.hidden && list.querySelectorAll("li").length) return;
        if (e.key === "Enter") {
          e.preventDefault();
          (qty || sig)?.focus();
        } else if (e.key === "Backspace" && !drug.value && drug.selectionStart === 0 && isLineEmpty(block)) {
          e.preventDefault();
          removeMedLine(block);
        }
      });

      qty?.addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
          e.preventDefault();
          sig?.focus();
        }
      });

      sig?.addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
          e.preventDefault();
          addMedLine(true);
        } else if (e.key === "Backspace" && !sig.value && sig.selectionStart === 0 && isLineEmpty(block)) {
          e.preventDefault();
          removeMedLine(block);
        }
      });
    }

    function wireAllMedLines() {
      medsEl.querySelectorAll("[data-med-idx]").forEach((block) => wireMedLineKeys(block));
    }

    function addMedLine(focusDrug) {
      const idx = medsEl.querySelectorAll("[data-med-idx]").length;
      const wrap = document.createElement("div");
      wrap.innerHTML = medBlockHtml(idx, defaultLine());
      const block = wrap.firstElementChild;
      medsEl.appendChild(block);
      reindexMeds();
      attachMedAutocomplete(root, drugCatalog);
      wireMedLineKeys(block);
      if (focusDrug !== false) block.querySelector('[data-field="drug"]')?.focus();
      return block;
    }

    root.addEventListener("click", (e) => {
      if (e.target.closest("[data-rx-add-line]")) {
        e.preventDefault();
        addMedLine(true);
      }
    });

    loadDrugCatalog().then((cat) => {
      drugCatalog = cat;
      attachMedAutocomplete(root, drugCatalog);
      attachPetAutocomplete(root, opts.pets || [], opts);
      wireAllMedLines();
    });

    async function syncPetFromSelect() {
      if (!petSel) return;
      const petId = Number(petSel.value);
      data.petId = petId;
      const pet = (opts.pets || []).find((p) => Number(p.id) === petId);
      if (pet) {
        const petInp = root.querySelector('[data-field="pet"]');
        if (petInp) {
          petInp.value = pet.name || "";
          petInp.dataset.petId = String(petId);
        }
        if (typeof g.ownerForPet === "function") {
          try {
            root.querySelector('[data-field="owner"]').value = (await g.ownerForPet(petId)) || pet.owner || "";
          } catch (_) {
            root.querySelector('[data-field="owner"]').value = pet.owner || "";
          }
        } else {
          root.querySelector('[data-field="owner"]').value = pet.owner || "";
        }
      }
    }

    petSel?.addEventListener("change", syncPetFromSelect);

    function getData() {
      const lines = [];
      medsEl.querySelectorAll("[data-med-idx]").forEach((block) => {
        const drug = block.querySelector('[data-field="drug"]')?.value?.trim() || "";
        const qtyRaw = block.querySelector('[data-field="qty"]')?.value?.trim() || "";
        const sig = block.querySelector('[data-field="sig"]')?.value?.trim() || "";
        if (!drug && !sig) return;
        lines.push({
          drug,
          dosage: "",
          directions: sig,
          dispenseQty: qtyRaw ? parseInt(qtyRaw, 10) || qtyRaw : null,
        });
      });
      const petInp = root.querySelector('[data-field="pet"]');
      const petIdFromPad = petInp?.dataset?.petId ? Number(petInp.dataset.petId) : 0;
      return {
        petId: petSel ? Number(petSel.value) : petIdFromPad || data.petId,
        pet: petInp?.value?.trim() || "",
        owner: root.querySelector('[data-field="owner"]')?.value?.trim() || "",
        date: root.querySelector('[data-field="date"]')?.value || data.date,
        prescriber: data.prescriber,
        followUp: root.querySelector('[data-field="followUp"]')?.value?.trim() || "",
        daysSupply: root.querySelector('[data-field="followUp"]')?.value?.trim() || "",
        rxStatus: statusSel?.value || data.rxStatus || "SAVED",
        lines: lines.length ? lines : [defaultLine()],
      };
    }

    return {
      getData,
      addMedLine,
      destroy() {},
    };
  }

  function mount(mountEl, options) {
    if (!mountEl) return null;
    const opts = options || {};
    const data = normalizeData(opts.initialData);
    if (!data.prescriber) {
      data.prescriber =
        (typeof g.getUserName === "function" ? g.getUserName() : "") ||
        g.localStorage?.getItem("userDisplayName") ||
        g.localStorage?.getItem("username") ||
        "Vet";
    }
    mountEl.innerHTML = `
      <div class="rx-pad-editor-wrap">
        <div class="rx-pad-toolbar mb-2">
          <button type="button" data-rx-add-line class="px-3 py-1.5 text-sm border border-gray-300 rounded-md hover:bg-gray-50">+ Add medicine line</button>
        </div>
        ${sheetHtml(data, opts)}
      </div>`;
    const api = bindEditor(mountEl, { ...opts, initialData: data });
    if (typeof opts.onChange === "function") {
      mountEl.addEventListener("input", () => opts.onChange(api.getData()));
    }
    return api;
  }

  function openModal(modalRoot, options) {
    if (!modalRoot) return null;
    const opts = options || {};
    const data = normalizeData(opts.initialData);
    if (!data.prescriber) {
      data.prescriber =
        (typeof g.getUserName === "function" ? g.getUserName() : "") ||
        g.localStorage?.getItem("userDisplayName") ||
        g.localStorage?.getItem("username") ||
        "Vet";
    }
    modalRoot.innerHTML = `
      <div class="rx-pad-modal-backdrop" data-rx-modal>
        <div class="rx-pad-modal-inner">
          <div class="rx-pad-editor-wrap">
            <div class="rx-pad-toolbar">
              <button type="button" data-rx-add-line class="px-3 py-1.5 text-sm border rounded-md border-gray-300 hover:bg-gray-50">+ Add medicine</button>
              <button type="button" data-rx-save class="px-3 py-1.5 text-sm rounded-md bg-green-700 text-white">Save</button>
              <button type="button" data-rx-close class="px-3 py-1.5 text-sm border rounded-md ml-auto">Close</button>
            </div>
            ${sheetHtml(data, { ...opts, showPetPicker: opts.showPetPicker !== false })}
          </div>
        </div>
      </div>`;
    const inner = modalRoot.querySelector("[data-rx-modal]");
    const api = bindEditor(inner, { ...opts, initialData: data });

    inner.querySelector("[data-rx-close]")?.addEventListener("click", () => {
      modalRoot.innerHTML = "";
      opts.onClose?.();
    });

    inner.querySelector("[data-rx-save]")?.addEventListener("click", async () => {
      const payload = api.getData();
      if (!payload.pet?.trim()) {
        alert("Pet name is required.");
        return;
      }
      if (!payload.owner?.trim()) {
        alert("Owner name is required.");
        return;
      }
      if (opts.onSave) await opts.onSave(payload);
    });

    return api;
  }

  g.BayportRxPadEditor = { mount, openModal, normalizeData };
})(typeof globalThis !== "undefined" ? globalThis : typeof window !== "undefined" ? window : this);
