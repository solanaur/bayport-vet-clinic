/**
 * Prescription registry (active/archived, print, dispense) for the Consultations module.
 */
window.initBayportRxRegistry = function initBayportRxRegistry(options) {
  const opts = options || {};
  const id = (key, fallback) => opts[key] || fallback;
  const activeSection = document.getElementById(id("activeSectionId", "rxReg_activeSection"));
  const archivedSection = document.getElementById(id("archivedSectionId", "rxReg_archivedSection"));
  const printedSection = document.getElementById(id("printedSectionId", "rxReg_printedSection"));
  const modalRoot = document.getElementById(id("modalRootId", "rxReg_modalRoot"));
  const issueBtn = document.getElementById(id("issueBtnId", "rxReg_issueBtn"));
  const role = typeof getRole === "function" ? getRole() : "";
  if (!activeSection || !archivedSection || !printedSection || !modalRoot) {
    console.warn("Bayport Rx registry: missing container elements");
    return { refresh: () => {}, openIssue: () => {} };
  }

    let selectedProducts = [];
    let currentTab = 'active';
    let expandedPrescriptions = new Set();
    if (issueBtn && (role === 'admin' || role === 'vet')) issueBtn.classList.remove('hidden');

    const RX_SEP = " \u00b7 ";
    function rxLineBullet(index) {
      const n = index + 1;
      if (n >= 1 && n <= 20) return String.fromCharCode(0x2460 + n - 1);
      return n + ".";
    }

    // Group prescriptions by pet, date, and prescriber
    function groupPrescriptions(prescriptions) {
      const groups = {};
      prescriptions.forEach(rx => {
        const key = `${rx.petId}_${rx.date}_${rx.prescriber}`;
        if (!groups[key]) {
          groups[key] = {
            id: rx.id,
            petId: rx.petId,
            pet: rx.pet,
            owner: rx.owner,
            date: rx.date,
            prescriber: rx.prescriber,
            prescriberLicenseNo: rx.prescriberLicenseNo,
            printedAt: rx.printedAt || null,
            printCount: rx.printCount || 0,
            dispensed: rx.dispensed,
            archived: rx.archived,
            products: []
          };
        }
        groups[key].products.push({
          id: rx.id,
          drug: rx.drug,
          dosage: rx.dosage,
          directions: rx.directions,
          dispenseQty: rx.dispenseQty,
          diagnosis: rx.diagnosis,
          daysSupply: rx.daysSupply,
          rxStatus: rx.rxStatus,
          owner: rx.owner,
          pet: rx.pet,
        });
        if ((rx.printCount || 0) > (groups[key].printCount || 0)) {
          groups[key].printCount = rx.printCount || 0;
        }
        if (rx.printedAt && (!groups[key].printedAt || String(rx.printedAt) > String(groups[key].printedAt))) {
          groups[key].printedAt = rx.printedAt;
        }
      });
      return Object.values(groups);
    }

    window.bayportRxSwitchTab = function(tab) {
      currentTab = tab;
      const tabActive = document.getElementById(id('tabActiveId','rxReg_tabActive'));
      const tabArchived = document.getElementById(id('tabArchivedId','rxReg_tabArchived'));
      const tabPrinted = document.getElementById(id('tabPrintedId','rxReg_tabPrinted'));
      const on = ['bg-[var(--soft-teal)]', 'text-white'];
      const off = ['bg-gray-100', 'text-gray-700'];
      function setTab(btn, active) {
        if (!btn) return;
        btn.classList.remove(...(active ? off : on));
        btn.classList.add(...(active ? on : off));
      }
      setTab(tabActive, tab === 'active');
      setTab(tabArchived, tab === 'archived');
      setTab(tabPrinted, tab === 'printed');
      activeSection.classList.toggle('hidden', tab !== 'active');
      archivedSection.classList.toggle('hidden', tab !== 'archived');
      printedSection.classList.toggle('hidden', tab !== 'printed');
    };

    window.togglePrescription = function(groupKey) {
      if (expandedPrescriptions.has(groupKey)) {
        expandedPrescriptions.delete(groupKey);
      } else {
        expandedPrescriptions.add(groupKey);
      }
      render();
    };

    function formatPrintedAt(val) {
      if (!val) return 'N/A';
      const s = String(val);
      return s.length >= 16 ? s.slice(0, 16).replace('T', ' ') : s.replace('T', ' ');
    }

    async function render(){
      try {
        const list = await repoListRx();
        const active = list.filter(r => !r.archived);
        const archived = list.filter(r => r.archived);
        
        const activeGroups = groupPrescriptions(active);
        const archivedGroups = groupPrescriptions(archived);
        
        // Render active prescriptions (compact with view button)
        if (activeGroups.length === 0) {
          activeSection.innerHTML = '<div class="text-center py-12 text-gray-400"><p>No active prescriptions</p></div>';
        } else {
          activeSection.innerHTML = activeGroups.map(g => {
            const groupKey = `${g.petId}_${g.date}_${g.prescriber}`;
            const isExpanded = expandedPrescriptions.has(groupKey);
            const statusColor = g.dispensed ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700';
            return `
              <div class="bg-white rounded-lg border border-gray-200 hover:shadow-md transition-all duration-200">
                <div class="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-3 p-4">
                  <div class="flex-1 min-w-0">
                      <div class="flex items-center gap-3 mb-1">
                        <h5 class="text-base font-semibold text-gray-800 truncate">${g.pet || 'Unknown Pet'}</h5>
                        <span class="px-2 py-0.5 rounded-full text-xs font-medium ${statusColor}">${g.dispensed ? 'Dispensed' : 'Pending'}</span>
                        <span class="px-2 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-700">${(g.products[0]?.rxStatus || 'SAVED')}</span>
                      </div>
                      <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-gray-600">
                        <span>${g.owner || 'N/A'}</span>
                        <span class="text-gray-400">${RX_SEP.trim()}</span>
                        <span>${g.date || 'N/A'}</span>
                        <span class="text-gray-400">${RX_SEP.trim()}</span>
                        <span>${g.products.length} line${g.products.length === 1 ? '' : 's'}</span>
                        ${g.printCount ? `<span class="text-gray-400">${RX_SEP.trim()}</span><span class="text-blue-700">Printed ${g.printCount}x</span>` : ''}
                      </div>
                      ${g.prescriberLicenseNo ? `<div class="text-[11px] text-gray-500 mt-1">Licensed No. ${g.prescriberLicenseNo}</div>` : ''}
                  </div>
                  <div class="flex flex-wrap items-center gap-2 shrink-0">
                      <button onclick="togglePrescription('${groupKey}')" class="px-3 py-1.5 text-xs font-medium border border-gray-300 rounded-lg hover:bg-gray-50 transition-all">
                        ${isExpanded ? 'Hide' : 'View'}
                      </button>
                      <button onclick="printRxGroup('${groupKey}')" class="px-3 py-1.5 text-xs font-medium bg-[var(--soft-teal)] text-white rounded-lg hover:bg-[#158a8a] transition-all">Print</button>
                      ${(role==='admin'||role==='vet') && (typeof canEditPrescriptionGroup !== 'function' || canEditPrescriptionGroup(g.products)) ? `<button onclick="editRxGroup('${groupKey}')" class="px-3 py-1.5 text-xs font-medium border border-[var(--soft-teal)] text-[var(--soft-teal)] rounded-lg hover:bg-[var(--soft-teal)] hover:text-white">Edit</button>` : ''}
                      ${role==='admin' && !g.dispensed ? `<button onclick="dispenseGroup('${groupKey}')" class="px-3 py-1.5 text-xs font-medium bg-green-600 text-white rounded-lg hover:bg-green-700 transition-all" title="Override: use POS for normal fulfillment">Dispense</button>`:''}
                      <button onclick="removeGroup('${groupKey}')" class="p-1.5 text-red-600 hover:text-red-800 hover:bg-red-50 rounded-lg transition-all" title="Delete prescription">
                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                        </svg>
                      </button>
                    </div>
                </div>
                ${isExpanded ? `
                  <div class="border-t border-gray-200 p-4 bg-gray-50">
                    <p class="text-xs font-semibold text-gray-600 mb-3 uppercase">Medications (${g.products.length})</p>
                    <div class="space-y-2">
                        ${g.products.map((p, idx) => `
                        <div class="text-sm bg-white rounded-lg p-3 border border-gray-200">
                          <p class="font-medium text-gray-800">${rxLineBullet(idx)} ${p.drug || 'N/A'} ${p.dosage ? `<span class="text-gray-600">${p.dosage}</span>` : ''}</p>
                          ${p.directions ? `<p class="text-xs text-gray-600 mt-1">Sig: ${p.directions}</p>` : ''}
                          ${p.dispenseQty ? `<p class="text-xs text-gray-600">Disp: ${p.dispenseQty} piece(s)</p>` : ''}
                        </div>
                      `).join('')}
                    </div>
                  </div>
                ` : ''}
              </div>
            `;
          }).join('');
        }
        
        // Render archived prescriptions (compact)
        if (archivedGroups.length === 0) {
          archivedSection.innerHTML = '<div class="text-center py-12 text-gray-400"><p>No archived prescriptions</p></div>';
        } else {
          archivedSection.innerHTML = archivedGroups.map(g => {
            const groupKey = `${g.petId}_${g.date}_${g.prescriber}`;
            const isExpanded = expandedPrescriptions.has(groupKey);
            return `
              <div class="bg-gray-50 rounded-lg border border-gray-200 opacity-90">
                <div class="flex items-center justify-between p-4">
                  <div class="flex items-center gap-4 flex-1 min-w-0">
                    <div class="flex-1 min-w-0">
                      <div class="flex items-center gap-3 mb-1">
                        <h5 class="text-base font-semibold text-gray-800 truncate">${g.pet || 'Unknown Pet'}</h5>
                        <span class="px-2 py-0.5 rounded-full text-xs font-medium bg-gray-200 text-gray-700">Archived</span>
                      </div>
                      <div class="flex items-center gap-3 text-xs text-gray-600">
                        <span>${g.owner || 'N/A'}</span>
                        <span class="text-gray-400">${RX_SEP.trim()}</span>
                        <span>${g.date || 'N/A'}</span>
                        <span class="text-gray-400">${RX_SEP.trim()}</span>
                        <span>${g.products.length} line${g.products.length === 1 ? '' : 's'}</span>
                      </div>
                    </div>
                    <div class="flex items-center gap-2 flex-shrink-0">
                      <button onclick="togglePrescription('${groupKey}')" class="px-3 py-1.5 text-xs font-medium border border-gray-300 rounded-lg hover:bg-gray-100 transition-all">
                        ${isExpanded ? 'Hide' : 'View'}
                      </button>
                      <button onclick="unarchiveGroup('${groupKey}')" class="px-3 py-1.5 text-xs font-medium border-2 border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-all">Unarchive</button>
                      <button onclick="removeGroup('${groupKey}')" class="p-1.5 text-red-600 hover:text-red-800 hover:bg-red-50 rounded-lg transition-all" title="Delete prescription">
                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                        </svg>
                      </button>
                    </div>
                  </div>
                </div>
                ${isExpanded ? `
                  <div class="border-t border-gray-200 p-4 bg-white">
                    <p class="text-xs font-semibold text-gray-600 mb-3 uppercase">Medications (${g.products.length})</p>
                    <div class="space-y-2">
                      ${g.products.map((p, idx) => `
                        <div class="text-sm bg-gray-50 rounded-lg p-3 border border-gray-200">
                          <p class="font-medium text-gray-800">${rxLineBullet(idx)} ${p.drug || 'N/A'} ${p.dosage ? `<span class="text-gray-600">${p.dosage}</span>` : ''}</p>
                          ${p.directions ? `<p class="text-xs text-gray-600 mt-1">Sig: ${p.directions}</p>` : ''}
                          ${p.dispenseQty ? `<p class="text-xs text-gray-600">Disp: ${p.dispenseQty} piece(s)</p>` : ''}
                        </div>
                      `).join('')}
                    </div>
                  </div>
                ` : ''}
              </div>
            `;
          }).join('');
        }

        const printedGroups = groupPrescriptions(list).filter(
          (g) => (g.printCount || 0) > 0 || g.printedAt
        );
        if (printedGroups.length === 0) {
          printedSection.innerHTML = '<div class="text-center py-12 text-gray-400"><p>No printed prescription records yet</p></div>';
        } else {
          printedSection.innerHTML = printedGroups.map(g => {
            const groupKey = `${g.petId}_${g.date}_${g.prescriber}`;
            const isExpanded = expandedPrescriptions.has(groupKey);
            return `
              <div class="bg-blue-50/60 rounded-lg border border-blue-100">
                <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 p-4">
                  <div class="flex-1 min-w-0">
                    <div class="flex flex-wrap items-center gap-2 mb-1">
                      <h5 class="text-base font-semibold text-gray-800 truncate">${g.pet || 'Unknown Pet'}</h5>
                      <span class="px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">Printed record</span>
                    </div>
                    <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-gray-600">
                      <span>${g.owner || 'N/A'}</span>
                      <span class="text-gray-400">${RX_SEP.trim()}</span>
                      <span>Rx date: ${g.date || 'N/A'}</span>
                      <span class="text-gray-400">${RX_SEP.trim()}</span>
                      <span>Last printed: ${formatPrintedAt(g.printedAt)}</span>
                      <span class="text-gray-400">${RX_SEP.trim()}</span>
                      <span>${g.printCount || 1}x</span>
                    </div>
                    <div class="text-[11px] text-gray-500 mt-1">Prescriber: ${g.prescriber || 'N/A'}${g.prescriberLicenseNo ? ` · Licensed No. ${g.prescriberLicenseNo}` : ''}</div>
                  </div>
                  <div class="flex flex-wrap items-center gap-2 shrink-0">
                    <button onclick="togglePrescription('${groupKey}')" class="px-3 py-1.5 text-xs font-medium border border-gray-300 rounded-lg hover:bg-white transition-all">${isExpanded ? 'Hide' : 'View'}</button>
                    <button onclick="printRxGroup('${groupKey}')" class="px-3 py-1.5 text-xs font-medium bg-[var(--soft-teal)] text-white rounded-lg hover:bg-[#158a8a] transition-all">Reprint</button>
                  </div>
                </div>
                ${isExpanded ? `
                  <div class="border-t border-blue-100 p-4 bg-white/80">
                    <p class="text-xs font-semibold text-gray-600 mb-3 uppercase">Medications (${g.products.length})</p>
                    <div class="space-y-2">
                      ${g.products.map((p, idx) => `
                        <div class="text-sm bg-white rounded-lg p-3 border border-gray-200">
                          <p class="font-medium text-gray-800">${rxLineBullet(idx)} ${p.drug || 'N/A'} ${p.dosage ? `<span class="text-gray-600">${p.dosage}</span>` : ''}</p>
                          ${p.directions ? `<p class="text-xs text-gray-600 mt-1">Sig: ${p.directions}</p>` : ''}
                          ${p.dispenseQty ? `<p class="text-xs text-gray-600">Disp: ${p.dispenseQty} piece(s)</p>` : ''}
                        </div>
                      `).join('')}
                    </div>
                  </div>
                ` : ''}
              </div>
            `;
          }).join('');
        }
      } catch(err) {
        console.error('Error rendering prescriptions:', err);
        activeSection.innerHTML = '<div class="text-center py-12 text-red-500"><p>Error loading prescriptions</p></div>';
      }
    }

    issueBtn?.addEventListener('click', () => openForm());

    function prescriberName() {
      try {
        return (
          (typeof getUserName === 'function' ? getUserName() : '') ||
          localStorage.getItem('userDisplayName') ||
          localStorage.getItem('username') ||
          'Vet'
        );
      } catch (_) {
        return 'Vet';
      }
    }

    async function savePadToApi(data, existingRxs) {
      const vetNotes =
        (typeof getClinicSettings === 'function' ? getClinicSettings().rxBlankTemplate : '') || '';
      const prescriber = data.prescriber || prescriberName();
      const licenseNo =
        data.prescriberLicenseNo ||
        (typeof resolveVetLicenseNo === 'function' ? await resolveVetLicenseNo(prescriber) : '');
      const petId = Number(data.petId);
      const lines = (data.lines || []).filter((l) => l.drug && String(l.drug).trim());
      if (!lines.length) throw new Error('Add at least one medicine on the prescription pad.');

      const saved = [];
      for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        const payload = {
          petId,
          pet: data.pet,
          owner: data.owner,
          drug: line.drug.trim(),
          dosage: line.dosage || null,
          directions: line.directions || (i === 0 ? null : 'As directed'),
          dispenseQty: line.dispenseQty != null && line.dispenseQty !== '' ? Number(line.dispenseQty) : null,
          diagnosis: null,
          daysSupply: i === 0 ? data.followUp || data.daysSupply || null : null,
          rxStatus: data.rxStatus || 'SAVED',
          vetNotes: i === 0 ? vetNotes || null : null,
          prescriber,
          prescriberLicenseNo: licenseNo || null,
          date: data.date,
          dispensed: false,
        };
        if (existingRxs && existingRxs[i] && existingRxs[i].id) {
          saved.push(await repoUpdateRx({ ...existingRxs[i], ...payload, id: existingRxs[i].id }));
        } else {
          saved.push(await repoCreateRx(payload));
        }
      }
      if (existingRxs) {
        for (let j = lines.length; j < existingRxs.length; j++) {
          await repoDeleteRx(existingRxs[j].id);
        }
      }
      return saved;
    }

    async function printPadPdf(data, existingRxs) {
      let first = existingRxs && existingRxs[0];
      if (!first || !first.id) {
        const saved = await savePadToApi(data, existingRxs);
        first = saved[0];
      }
      const groupKey = `${first.petId}_${first.date}_${first.prescriber}`;
      const blob = await Api.rx.downloadPdf(first.id, groupKey);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `Prescription_${first.pet || 'Pet'}_${first.date}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      render();
    }

    async function openForm(prefillPetId, existingRxs) {
      if (typeof BayportRxPadEditor === 'undefined') {
        alert('Prescription pad editor failed to load. Refresh the page.');
        return;
      }
      const pets = await repoListPets();
      const pre =
        prefillPetId != null && Number.isFinite(Number(prefillPetId))
          ? Number(prefillPetId)
          : Number(new URLSearchParams(location.search).get('petId') || 0) || '';

      let initialData;
      if (existingRxs && existingRxs.length) {
        const first = existingRxs[0];
        initialData = BayportRxPadEditor.normalizeData({
          petId: first.petId,
          pet: first.pet,
          owner: first.owner,
          date: first.date,
          prescriber: first.prescriber,
          followUp: first.daysSupply,
          rxStatus: first.rxStatus,
          lines: existingRxs.map((r) => ({
            id: r.id,
            drug: r.drug,
            dosage: r.dosage,
            directions: r.directions,
            dispenseQty: r.dispenseQty,
          })),
        });
      } else {
        const pet = pets.find((p) => Number(p.id) === pre);
        initialData = BayportRxPadEditor.normalizeData({
          petId: pre || '',
          pet: pet?.name || '',
          owner: pet?.owner || '',
          date: new Date().toISOString().slice(0, 10),
          prescriber: prescriberName(),
        });
        if (pre && typeof ownerForPet === 'function') {
          try {
            initialData.owner = (await ownerForPet(pre)) || initialData.owner;
          } catch (_) {}
        }
      }

      BayportRxPadEditor.openModal(modalRoot, {
        pets,
        initialData,
        showPetPicker: !existingRxs,
        onSave: async (data) => {
          if (!data.owner?.trim()) {
            alert('Owner name is required.');
            return;
          }
          await savePadToApi(data, existingRxs);
          modalRoot.innerHTML = '';
          render();
          alert('Prescription saved.');
        },
        onPrint: async (data) => {
          if (!data.owner?.trim()) {
            alert('Owner name is required.');
            return;
          }
          await printPadPdf(data, existingRxs);
          modalRoot.innerHTML = '';
          render();
        },
        onClose: () => {
          modalRoot.innerHTML = '';
        },
      });
    }

    function closeModal() {
      modalRoot.innerHTML = '';
    }
    window.closeModal = closeModal;

    window.editRxGroup = async function editRxGroup(groupKey) {
      try {
        const list = await repoListRx();
        const groupRxs = list.filter(r => `${r.petId}_${r.date}_${r.prescriber}` === groupKey);
        if (!groupRxs.length) return alert('Prescription not found');
        if (typeof canEditPrescriptionGroup === 'function' && !canEditPrescriptionGroup(groupRxs)) {
          return alert('This prescription is complete and marked Done. Only incomplete records can be edited.');
        }
        await openForm(groupRxs[0].petId, groupRxs);
      } catch (e) {
        alert('Unable to edit: ' + (e.message || 'Unknown error'));
      }
    };

    async function dispenseGroup(groupKey) {
      if (!confirm('Mark all products in this prescription as dispensed? This will automatically archive the prescription.')) return;
      try {
        const list = await repoListRx();
        const groupRxs = list.filter(r => `${r.petId}_${r.date}_${r.prescriber}` === groupKey && !r.dispensed);
        // Dispense all products
        await Promise.all(groupRxs.map(rx => repoDispenseRx(rx.id)));
        // Auto-archive after dispensing
        await Promise.all(groupRxs.map(rx => repoArchiveRx(rx.id, true)));
        render();
      } catch(err) {
        console.error('Error dispensing:', err);
        alert('Error marking as dispensed');
      }
    }
    
    async function printRxGroup(groupKey) {
      try {
        const list = await repoListRx();
        const groupRxs = list.filter(r => `${r.petId}_${r.date}_${r.prescriber}` === groupKey);
        if (groupRxs.length === 0) {
          alert('No prescriptions found for this group');
          return;
        }
        // Use first prescription ID and pass group key
        const firstId = groupRxs[0].id;
        const blob = await Api.rx.downloadPdf(firstId, groupKey);
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `Prescription_${groupRxs[0].pet}_${groupRxs[0].date}.pdf`;
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        render();
      } catch(err) {
        alert('Unable to generate PDF: ' + (err.message || 'Unexpected error'));
      }
    }
    
    async function unarchiveGroup(groupKey) {
      try {
        const list = await repoListRx();
        const groupRxs = list.filter(r => `${r.petId}_${r.date}_${r.prescriber}` === groupKey && r.archived);
        await Promise.all(groupRxs.map(rx => repoArchiveRx(rx.id, false)));
        render();
      } catch(err) {
        console.error('Error unarchiving:', err);
        alert('Error unarchiving prescription');
      }
    }
    
    async function removeGroup(groupKey) {
      if (!confirm('Delete this entire prescription with all products?')) return;
      try {
        const list = await repoListRx();
        const groupRxs = list.filter(r => `${r.petId}_${r.date}_${r.prescriber}` === groupKey);
        await Promise.all(groupRxs.map(rx => repoDeleteRx(rx.id)));
        render();
      } catch(err) {
        console.error('Error deleting:', err);
        alert('Error deleting prescription');
      }
    }
    
    async function printRx(id){
      try {
        const list = await repoListRx();
        const rx = list.find(r => r.id === id);
        if (!rx) {
          alert('Prescription not found');
          return;
        }
        const groupKey = `${rx.petId}_${rx.date}_${rx.prescriber}`;
        await printRxGroup(groupKey);
      } catch(err) {
        alert('Unable to generate PDF: ' + (err.message || 'Unexpected error'));
      }
    }

    window.printRxGroup = printRxGroup;
    window.dispenseGroup = dispenseGroup;
    window.removeGroup = removeGroup;
    window.unarchiveGroup = unarchiveGroup;
    window.printRx = printRx;

    render();

    if (opts.autoOpenIssueFromQuery !== false) {
      (async () => {
        try {
          const q = new URLSearchParams(window.location.search);
          if (q.get('issue') !== '1' || !q.get('petId')) return;
          if (role !== 'admin' && role !== 'vet') return;
          const u = new URL(window.location.href);
          u.searchParams.delete('petId');
          u.searchParams.delete('issue');
          const tail = u.searchParams.toString();
          history.replaceState({}, '', u.pathname + (tail ? '?' + tail : '') + u.hash);
          await openForm(Number(q.get('petId')));
        } catch (e) {
          console.error(e);
        }
      })();
    }

  return { refresh: render, openIssue: openForm };
};
