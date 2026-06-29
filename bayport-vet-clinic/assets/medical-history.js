/**
 * Pet medical history — external records, structured sections, vaccination schedule, PDF export.
 */
(function () {
  const RECORD_TYPES = [
    { value: 'VACCINATION', label: 'Vaccination history' },
    { value: 'CLINICAL_NOTE', label: 'Clinical notes' },
    { value: 'DIAGNOSTIC', label: 'Diagnostic & test results' },
    { value: 'MEDICATION', label: 'Medications & preventatives' },
    { value: 'SURGERY', label: 'Surgical history' },
    { value: 'EXTERNAL_DOCUMENT', label: 'External clinic document' },
  ];

  const TYPE_LABEL = Object.fromEntries(RECORD_TYPES.map((t) => [t.value, t.label]));

  const TYPE_SHORT = {
    VACCINATION: 'Vaccination',
    CLINICAL_NOTE: 'Clinical Note',
    DIAGNOSTIC: 'Diagnostic Result',
    MEDICATION: 'Medication',
    SURGERY: 'Surgery',
    EXTERNAL_DOCUMENT: 'Document',
  };

  function hasVal(s) {
    return s != null && String(s).trim() !== '';
  }

  function esc(s) {
    if (s == null || String(s).trim() === '') return '—';
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
  }

  function escAttr(s) {
    return String(s ?? '').replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
  }

  function fmtDate(d) {
    if (!d) return '—';
    try {
      const x = new Date(typeof d === 'string' ? d.slice(0, 10) + 'T12:00:00' : d);
      if (Number.isNaN(x.getTime())) return esc(String(d));
      return x.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
    } catch (_) {
      return esc(String(d));
    }
  }

  function statusBadge(status) {
    const s = (status || '').toLowerCase();
    let cls = 'bg-sky-100 text-sky-800';
    if (s.includes('overdue')) cls = 'bg-red-100 text-red-800';
    else if (s.includes('due today')) cls = 'bg-amber-100 text-amber-800';
    else if (s.includes('sent')) cls = 'bg-emerald-100 text-emerald-800';
    return `<span class="inline-flex text-xs font-semibold rounded-full px-2.5 py-0.5 ${cls}">${esc(status || 'Scheduled')}</span>`;
  }

  let externalExpandedId = null;

  window.MedicalHistory = {
    RECORD_TYPES,
    TYPE_LABEL,
    TYPE_SHORT,

    async loadRecords(petId) {
      return Api.medicalRecords.list(petId);
    },

    async loadSchedule(petId) {
      return Api.medicalRecords.vaccinationSchedule(petId);
    },

    async printPdf(petId, petName) {
      await Api.pets.downloadMedicalHistoryPdf(petId, petName);
    },

    renderVaccinationSchedule(container, schedule) {
      if (!container) return;
      const list = Array.isArray(schedule) ? schedule : [];
      if (!list.length) {
        container.innerHTML = `<p class="text-sm text-gray-500">No upcoming vaccination milestones. Add vaccination records or in-clinic procedures to auto-generate reminders.</p>`;
        return;
      }
      container.innerHTML = `<table class="w-full border-collapse text-sm min-w-[520px]">
        <thead><tr class="text-left text-gray-500 border-b border-gray-200">
          <th class="py-2 pr-3 font-medium">Vaccine</th>
          <th class="py-2 pr-3 font-medium">Last given</th>
          <th class="py-2 pr-3 font-medium">Next due</th>
          <th class="py-2 pr-3 font-medium">Status</th>
          <th class="py-2 font-medium">Source</th>
        </tr></thead>
        <tbody>${list.map((r) => `<tr class="border-b border-gray-100">
          <td class="py-3 pr-3 font-medium text-gray-900">${esc(r.vaccineName)}</td>
          <td class="py-3 pr-3 text-gray-700">${fmtDate(r.lastGiven)}</td>
          <td class="py-3 pr-3 text-gray-700">${fmtDate(r.nextDue)}</td>
          <td class="py-3 pr-3">${statusBadge(r.status)}</td>
          <td class="py-3 text-gray-600 text-xs">${esc(r.source)}</td>
        </tr>`).join('')}</tbody>
      </table>`;
    },

    renderRecordsByType(container, records, type) {
      if (!container) return;
      const filtered = (records || []).filter((r) => r.recordType === type);
      if (!filtered.length) {
        container.innerHTML = `<p class="text-sm text-gray-500 italic">No ${TYPE_LABEL[type] || type} recorded yet.</p>`;
        return;
      }
      container.innerHTML = filtered.map((r) => this._recordCard(r)).join('');
    },

    renderExternalRecords(container, records, petId) {
      if (!container) return;
      const list = (records || [])
        .slice()
        .sort((a, b) => String(b.recordDate || '').localeCompare(String(a.recordDate || '')));

      if (externalExpandedId && !list.some((r) => r.id === externalExpandedId)) {
        externalExpandedId = null;
      }

      if (!list.length) {
        const headerBtn = document.getElementById('extRecHeaderAddBtn');
        if (headerBtn) headerBtn.classList.add('hidden');

        container.innerHTML = `
          <div class="ext-rec-empty">
            <div class="ext-rec-empty-icon" aria-hidden="true">
              <svg width="24" height="24" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.75" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/></svg>
            </div>
            <p class="ext-rec-empty-title">No external medical records have been added yet.</p>
            <p class="ext-rec-empty-desc">Store treatments, vaccinations, surgeries, medications, laboratory results, and documents from other veterinary clinics.</p>
            <button type="button" class="ext-rec-add-btn" data-ext-add>+ Add External Record</button>
          </div>`;
        container.querySelector('[data-ext-add]')?.addEventListener('click', () => {
          if (typeof window.openExternalRecordModal === 'function') {
            window.openExternalRecordModal();
          } else {
            this.openAddModal(petId, 'EXTERNAL_DOCUMENT');
          }
        });
        return;
      }

      const headerBtn = document.getElementById('extRecHeaderAddBtn');
      if (headerBtn) headerBtn.classList.remove('hidden');

      container.innerHTML = `<div class="ext-rec-timeline">${list.map((r) => this._externalRecordCard(r)).join('')}</div>`;
      this._bindExternalAccordions(container);
    },

    _externalRecordCard(r) {
      const clinic = hasVal(r.sourceClinic) ? esc(r.sourceClinic) : 'External clinic';
      const title = hasVal(r.title) ? esc(r.title) : 'Medical record';
      const date = fmtDate(r.recordDate);
      const rid = r.id;
      const isOpen = externalExpandedId === rid;

      return `<div class="ext-rec-accordion${isOpen ? ' is-open' : ''}" data-ext-rec="${rid}">
        <button type="button" class="ext-rec-header${isOpen ? ' is-open' : ''}" data-ext-toggle="${rid}" aria-expanded="${isOpen}">
          <div class="ext-rec-summary">
            <div class="ext-rec-clinic">${clinic}</div>
            <div class="ext-rec-title">${title}</div>
            <div class="ext-rec-date">${date}</div>
          </div>
          <div class="ext-rec-chevron-wrap">
            <span class="ext-rec-toggle-lbl">${isOpen ? 'Hide details' : 'View details'}</span>
            <svg class="ext-rec-chevron" width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/></svg>
          </div>
        </button>
        <div class="ext-rec-panel${isOpen ? ' is-open' : ''}" data-ext-panel="${rid}">
          <div class="ext-rec-panel-inner">${this._externalRecordExpanded(r)}</div>
        </div>
      </div>`;
    },

    _externalRecordExpanded(r) {
      const fields = [];
      const typeLabel = TYPE_SHORT[r.recordType] || r.recordType;
      if (hasVal(typeLabel)) fields.push(this._extKv('Record type', typeLabel));
      if (hasVal(r.sourceClinic)) fields.push(this._extKv('Source clinic', r.sourceClinic));
      if (hasVal(r.veterinarian)) fields.push(this._extKv('Veterinarian', r.veterinarian));
      if (hasVal(r.recordDate)) fields.push(this._extKv('Date', fmtDate(r.recordDate)));
      if (hasVal(r.diagnosis)) fields.push(this._extKv('Diagnosis', r.diagnosis, { multiline: true }));
      if (hasVal(r.treatmentPlan)) fields.push(this._extKv('Treatment', r.treatmentPlan, { multiline: true }));
      if (hasVal(r.description)) fields.push(this._extKv('Clinical notes', r.description, { multiline: true }));
      if (hasVal(r.medications)) fields.push(this._extKv('Medications', r.medications, { multiline: true }));
      if (hasVal(r.testResults)) fields.push(this._extKv('Diagnostic results', r.testResults, { multiline: true }));

      if (r.recordType === 'VACCINATION') {
        const vaccParts = [r.vaccineType, r.doseNumber].filter(hasVal);
        if (vaccParts.length) fields.push(this._extKv('Vaccine', vaccParts.join(' · ')));
        if (hasVal(r.nextDueDate)) fields.push(this._extKv('Next due', fmtDate(r.nextDueDate)));
      }

      let attachHtml = '';
      if (hasVal(r.attachmentUrl)) {
        attachHtml = `<div class="ext-rec-attach">
          <div class="ext-rec-attach-lbl">Attachments</div>
          <a href="${esc(r.attachmentUrl)}" target="_blank" rel="noopener" class="ext-rec-attach-link">${esc(r.attachmentName || 'View document')}</a>
        </div>`;
      }

      const body = fields.length
        ? `<div class="ext-rec-kv-grid">${fields.join('')}</div>${attachHtml}`
        : attachHtml;

      const actions = `<div class="ext-rec-actions">
        <button type="button" class="ext-rec-action ext-rec-action--edit" onclick="MedicalHistory.openEditModal(${r.petId}, ${r.id}, function(){ if(typeof window.refreshMedicalHistory==='function') window.refreshMedicalHistory(); })">Edit</button>
        <button type="button" class="ext-rec-action ext-rec-action--delete" onclick="MedicalHistory.deleteRecord(${r.petId}, ${r.id})">Delete</button>
      </div>`;

      return (body || '') + actions;
    },

    _extKv(label, value, opts = {}) {
      const v = opts.multiline
        ? `<div class="ext-kv-v notes">${esc(value)}</div>`
        : `<div class="ext-kv-v">${esc(value)}</div>`;
      return `<div class="ext-kv"><div class="ext-kv-k">${label}</div>${v}</div>`;
    },

    _bindExternalAccordions(container) {
      container.querySelectorAll('[data-ext-toggle]').forEach((btn) => {
        btn.addEventListener('click', () => {
          const rid = Number(btn.dataset.extToggle);
          externalExpandedId = externalExpandedId === rid ? null : rid;
          container.querySelectorAll('.ext-rec-accordion').forEach((acc) => {
            const id = Number(acc.dataset.extRec);
            const open = id === externalExpandedId;
            acc.classList.toggle('is-open', open);
            const header = acc.querySelector('.ext-rec-header');
            const panel = acc.querySelector('.ext-rec-panel');
            header?.classList.toggle('is-open', open);
            header?.setAttribute('aria-expanded', open ? 'true' : 'false');
            panel?.classList.toggle('is-open', open);
            const lbl = header?.querySelector('.ext-rec-toggle-lbl');
            if (lbl) lbl.textContent = open ? 'Hide details' : 'View details';
          });
        });
      });
    },

    _recordCard(r) {
      const ext = r.externalRecord ? '<span class="text-xs rounded bg-violet-100 text-violet-800 px-2 py-0.5 ml-2">External</span>' : '';
      const attach = r.attachmentUrl
        ? `<a href="${esc(r.attachmentUrl)}" target="_blank" rel="noopener" class="text-xs text-[var(--soft-teal)] font-semibold hover:underline">View attachment</a>`
        : '';
      const details = [r.diagnosis, r.treatmentPlan, r.testResults, r.medications, r.description]
        .filter((x) => x && String(x).trim())
        .map((x) => esc(x))
        .join('<br>');
      return `<div class="border border-gray-200 rounded-lg p-4 mb-3 bg-gray-50/50" data-record-id="${r.id}">
        <div class="flex flex-wrap items-start justify-between gap-2 mb-2">
          <div>
            <span class="font-semibold text-gray-900">${esc(r.title)}</span>${ext}
            <div class="text-xs text-gray-500 mt-0.5">${fmtDate(r.recordDate)}${r.sourceClinic ? ' · ' + esc(r.sourceClinic) : ''}${r.veterinarian ? ' · Dr. ' + esc(r.veterinarian) : ''}</div>
          </div>
          <div class="flex gap-2">
            <button type="button" class="text-xs font-semibold text-[var(--soft-teal)] hover:underline" onclick="MedicalHistory.openEditModal(${r.petId}, ${r.id})">Edit</button>
            <button type="button" class="text-xs font-semibold text-red-600 hover:underline" onclick="MedicalHistory.deleteRecord(${r.petId}, ${r.id})">Delete</button>
          </div>
        </div>
        ${r.vaccineType ? `<div class="text-sm text-gray-700"><strong>Vaccine:</strong> ${esc(r.vaccineType)}${r.doseNumber ? ' (' + esc(r.doseNumber) + ')' : ''}${r.nextDueDate ? ' · Next due: ' + fmtDate(r.nextDueDate) : ''}</div>` : ''}
        ${details ? `<div class="text-sm text-gray-700 mt-2">${details}</div>` : ''}
        ${attach ? `<div class="mt-2">${attach}</div>` : ''}
      </div>`;
    },

    openAddModal(petId, defaultType, onSaved) {
      this._openModal(petId, null, defaultType || 'EXTERNAL_DOCUMENT', onSaved);
    },

    async openEditModal(petId, recordId, onSaved) {
      const records = await Api.medicalRecords.list(petId);
      const record = records.find((r) => r.id === recordId);
      if (!record) { alert('Record not found'); return; }
      this._openModal(petId, record, record.recordType, onSaved);
    },

    _openModal(petId, existing, defaultType, onSaved) {
      const modalRoot = document.getElementById('modalRoot');
      if (!modalRoot) return;
      const isEdit = !!existing;
      const type = existing?.recordType || defaultType || 'EXTERNAL_DOCUMENT';
      const showVacc = type === 'VACCINATION';
      modalRoot.innerHTML = `
        <div class="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50" onclick="if(event.target===this) closeModal()">
          <div class="bg-white rounded-2xl w-full max-w-2xl p-6 shadow-2xl max-h-[92vh] overflow-y-auto" onclick="event.stopPropagation()">
            <h3 class="text-xl font-semibold text-gray-800 mb-1">${isEdit ? 'Edit' : 'Add'} external record</h3>
            <p class="text-sm text-gray-500 mb-4">Import treatments, vaccinations, surgeries, medications, lab results, or documents from another veterinary clinic.</p>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <label class="text-sm text-gray-600 md:col-span-2">Record type
                <select id="mr_type" class="w-full border border-gray-300 rounded-lg p-3 mt-1">${RECORD_TYPES.map((t) =>
                  `<option value="${t.value}" ${type === t.value ? 'selected' : ''}>${t.label}</option>`).join('')}
                </select>
              </label>
              <label class="text-sm text-gray-600">Date<input id="mr_date" type="date" class="w-full border border-gray-300 rounded-lg p-3 mt-1" value="${escAttr(existing?.recordDate?.slice?.(0, 10) || new Date().toISOString().slice(0, 10))}"></label>
              <label class="text-sm text-gray-600">Title<input id="mr_title" class="w-full border border-gray-300 rounded-lg p-3 mt-1" value="${escAttr(existing?.title || '')}" placeholder="e.g. Rabies booster from ABC Vet"></label>
              <label class="text-sm text-gray-600">Source clinic<input id="mr_clinic" class="w-full border border-gray-300 rounded-lg p-3 mt-1" value="${escAttr(existing?.sourceClinic || '')}" placeholder="External clinic name"></label>
              <label class="text-sm text-gray-600">Veterinarian<input id="mr_vet" class="w-full border border-gray-300 rounded-lg p-3 mt-1" value="${escAttr(existing?.veterinarian || '')}"></label>
              <div id="mr_vacc_fields" class="md:col-span-2 grid grid-cols-1 md:grid-cols-3 gap-4 ${showVacc ? '' : 'hidden'}">
                <label class="text-sm text-gray-600">Vaccine type<input id="mr_vaccine" class="w-full border border-gray-300 rounded-lg p-3 mt-1" value="${escAttr(existing?.vaccineType || '')}" placeholder="Rabies, 5-in-1, FVRCP"></label>
                <label class="text-sm text-gray-600">Dose<input id="mr_dose" class="w-full border border-gray-300 rounded-lg p-3 mt-1" value="${escAttr(existing?.doseNumber || '')}" placeholder="1st, 2nd, Booster"></label>
                <label class="text-sm text-gray-600">Next due date<input id="mr_nextdue" type="date" class="w-full border border-gray-300 rounded-lg p-3 mt-1" value="${escAttr(existing?.nextDueDate?.slice?.(0, 10) || '')}"></label>
              </div>
              <label class="text-sm text-gray-600 md:col-span-2">Diagnosis<textarea id="mr_diagnosis" rows="2" class="w-full border border-gray-300 rounded-lg p-3 mt-1">${escAttr(existing?.diagnosis || '')}</textarea></label>
              <label class="text-sm text-gray-600 md:col-span-2">Treatment plan<textarea id="mr_treatment" rows="2" class="w-full border border-gray-300 rounded-lg p-3 mt-1">${escAttr(existing?.treatmentPlan || '')}</textarea></label>
              <label class="text-sm text-gray-600 md:col-span-2">Test results<textarea id="mr_tests" rows="2" class="w-full border border-gray-300 rounded-lg p-3 mt-1">${escAttr(existing?.testResults || '')}</textarea></label>
              <label class="text-sm text-gray-600 md:col-span-2">Medications<textarea id="mr_meds" rows="2" class="w-full border border-gray-300 rounded-lg p-3 mt-1">${escAttr(existing?.medications || '')}</textarea></label>
              <label class="text-sm text-gray-600 md:col-span-2">Notes / description<textarea id="mr_desc" rows="3" class="w-full border border-gray-300 rounded-lg p-3 mt-1">${escAttr(existing?.description || '')}</textarea></label>
              <label class="text-sm text-gray-600 md:col-span-2 flex items-center gap-2">
                <input type="checkbox" id="mr_external" ${existing?.externalRecord !== false ? 'checked' : ''}>
                <span>Record from external veterinary clinic</span>
              </label>
              <label class="text-sm text-gray-600 md:col-span-2">Upload document (PDF, image)
                <input id="mr_file" type="file" accept=".pdf,.jpg,.jpeg,.png,.webp,.gif" class="w-full border border-gray-300 rounded-lg p-2 mt-1 text-sm">
                ${existing?.attachmentName ? `<p class="text-xs text-gray-500 mt-1">Current: ${esc(existing.attachmentName)}</p>` : ''}
              </label>
            </div>
            <div class="mt-6 flex justify-end gap-2">
              <button type="button" class="px-4 py-2 border border-gray-300 rounded-lg" onclick="closeModal()">Cancel</button>
              <button type="button" id="mr_save" class="px-5 py-2 rounded-lg bg-[var(--soft-teal)] text-white font-medium">Save record</button>
            </div>
          </div>
        </div>`;

      const typeEl = document.getElementById('mr_type');
      const vaccWrap = document.getElementById('mr_vacc_fields');
      typeEl?.addEventListener('change', () => {
        vaccWrap?.classList.toggle('hidden', typeEl.value !== 'VACCINATION');
      });

      document.getElementById('mr_save').onclick = async () => {
        const payload = {
          recordType: typeEl.value,
          recordDate: document.getElementById('mr_date').value || null,
          title: document.getElementById('mr_title').value.trim(),
          sourceClinic: document.getElementById('mr_clinic').value.trim(),
          veterinarian: document.getElementById('mr_vet').value.trim(),
          vaccineType: document.getElementById('mr_vaccine')?.value.trim() || null,
          doseNumber: document.getElementById('mr_dose')?.value.trim() || null,
          nextDueDate: document.getElementById('mr_nextdue')?.value || null,
          diagnosis: document.getElementById('mr_diagnosis').value.trim(),
          treatmentPlan: document.getElementById('mr_treatment').value.trim(),
          testResults: document.getElementById('mr_tests').value.trim(),
          medications: document.getElementById('mr_meds').value.trim(),
          description: document.getElementById('mr_desc').value.trim(),
          externalRecord: document.getElementById('mr_external').checked,
        };
        if (!payload.title) { alert('Title is required'); return; }
        try {
          let saved;
          if (isEdit) saved = await Api.medicalRecords.update(petId, existing.id, payload);
          else saved = await Api.medicalRecords.create(petId, payload);
          const file = document.getElementById('mr_file')?.files?.[0];
          if (file && saved?.id) {
            await Api.medicalRecords.uploadAttachment(petId, saved.id, file);
          }
          closeModal();
          if (typeof onSaved === 'function') onSaved();
          if (typeof showToast === 'function') showToast('Medical record saved');
        } catch (e) {
          alert(e.message || 'Could not save record');
        }
      };
    },

    async deleteRecord(petId, recordId) {
      if (!confirm('Delete this external record?')) return;
      try {
        await Api.medicalRecords.remove(petId, recordId);
        if (externalExpandedId === recordId) externalExpandedId = null;
        if (typeof window.refreshMedicalHistory === 'function') window.refreshMedicalHistory();
        if (typeof showToast === 'function') showToast('Record deleted');
      } catch (e) {
        alert(e.message || 'Could not delete');
      }
    },
  };
})();
