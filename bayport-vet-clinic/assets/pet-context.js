/**
 * Pet context bar — shows pet + owner under the clinic header on clinical pages.
 */
(function () {
  function esc(s) {
    return String(s ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/"/g, "&quot;");
  }

  window.initBayportPetContextBar = function (container, pet, opts) {
    if (!container) return;
    opts = opts || {};
    if (!pet || !pet.name) {
      container.innerHTML = "";
      container.classList.add("hidden");
      return;
    }
    container.classList.remove("hidden");
    const vetName =
      opts.vetName ||
      (typeof window.getUserName === "function" ? window.getUserName() : "") ||
      localStorage.getItem("userDisplayName") ||
      "";
    const logoSrc =
      (typeof window.getClinicSettings === "function" && window.getClinicSettings().logoDataUrl) ||
      "assets/logo.png";
    const species = [pet.species, pet.breed].filter(Boolean).join(" · ");
    const meta = [
      pet.owner ? `Owner: ${pet.owner}` : "",
      species,
      pet.gender ? pet.gender : "",
      pet.age != null ? `${pet.age} yr(s)` : "",
    ]
      .filter(Boolean)
      .join("  •  ");

    container.className =
      "w-full bg-white border-b border-slate-200 px-6 lg:px-10 py-3 shadow-sm";
    container.innerHTML = `
      <div class="flex flex-wrap items-center gap-4 justify-between max-w-[1400px] mx-auto">
        <div class="flex items-center gap-3 min-w-0">
          <img src="${esc(logoSrc)}" alt="" class="w-10 h-10 rounded-lg border border-slate-100 object-contain bg-white shrink-0" onerror="this.src='assets/logo.png'" />
          <div class="min-w-0">
            <div class="text-[10px] uppercase tracking-wide text-slate-500 font-semibold">Patient</div>
            <h2 class="text-lg font-bold text-slate-900 truncate">${esc(pet.name)}</h2>
            <p class="text-xs text-slate-600 truncate">${esc(meta)}</p>
          </div>
        </div>
        ${
          vetName
            ? `<div class="text-right shrink-0">
            <div class="text-[10px] uppercase tracking-wide text-slate-500 font-semibold">Veterinarian</div>
            <div class="text-sm font-semibold text-[var(--soft-teal,#0057b8)]">Dr. ${esc(vetName)}</div>
          </div>`
            : ""
        }
      </div>`;
  };

  window.refreshPetContextFromQuery = async function (containerId) {
    const el = document.getElementById(containerId || "bpPetContext");
    if (!el) return;
    const params = new URLSearchParams(window.location.search);
    const petId = Number(params.get("petId") || params.get("id"));
    if (!Number.isFinite(petId) || petId <= 0) {
      el.innerHTML = "";
      el.classList.add("hidden");
      return;
    }
    try {
      const pet = await Api.pets.get(petId);
      window.initBayportPetContextBar(el, pet);
      sessionStorage.setItem("bayport_active_pet_id", String(petId));
    } catch (e) {
      console.warn("Pet context:", e);
    }
  };
})();
