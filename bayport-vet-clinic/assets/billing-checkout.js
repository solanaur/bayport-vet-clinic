/**
 * Bayport — in-page payment checkout (Receive payment modal).
 */
(function (global) {
  let inventory = [];
  let pets = [];
  let cart = [];
  let lastCheckout = null;
  let deps = { toast: () => {}, onComplete: async () => {} };
  const WALK_IN_PET_VALUE = "walkin";

  function isWalkInCustomer() {
    return document.getElementById("payPetSel")?.value === WALK_IN_PET_VALUE;
  }

  function customerSelected() {
    const val = document.getElementById("payPetSel")?.value;
    return !!val;
  }

  function isWalkInBlockedCartLine(line) {
    if (!line) return false;
    if (line.kind === "billing") return true;
    if (line.kind === "inv") {
      const item = inventory.find((x) => x.id === line.id);
      return !!(item && isService(item));
    }
    return false;
  }

  function sanitizeCartForWalkIn() {
    if (!isWalkInCustomer()) return false;
    const before = cart.length;
    cart = cart.filter((l) => !isWalkInBlockedCartLine(l));
    return cart.length < before;
  }

  function updateWalkInHint() {
    const hint = document.getElementById("payWalkInHint");
    if (!hint) return;
    hint.classList.toggle("hidden", !isWalkInCustomer());
  }

  function peso(n) {
    const x = Number(n);
    const num = Number.isFinite(x) ? x : 0;
    if (typeof global.formatPrice === "function") return global.formatPrice(num);
    return num.toLocaleString("en-PH", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function esc(s) {
    return String(s ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/"/g, "&quot;");
  }

  function isService(item) {
    return String(item?.category || "").toUpperCase() === "SERVICE";
  }

  function itemStock(item) {
    if (isService(item)) return null;
    return Number(item?.quantity ?? item?.stock ?? 0);
  }

  function cartSubtotal() {
    return cart.reduce((sum, l) => sum + (Number(l.unitPrice) || 0) * (Number(l.qty) || 1), 0);
  }

  function cartTotal() {
    return cartSubtotal();
  }

  function selectedPet() {
    const id = document.getElementById("payPetSel")?.value;
    if (!id) return null;
    return pets.find((p) => String(p.id) === String(id)) || null;
  }

  function paymentMethod() {
    return document.getElementById("payMethod")?.value || "";
  }

  function updateCartQty(idx, delta) {
    const line = cart[idx];
    if (!line) return;
    const next = (Number(line.qty) || 1) + delta;
    if (next <= 0) {
      cart.splice(idx, 1);
    } else {
      line.qty = next;
    }
    renderCart();
  }

  function setCartQty(idx, qty) {
    const line = cart[idx];
    if (!line) return;
    const q = Math.max(1, parseInt(qty, 10) || 1);
    line.qty = q;
    renderCart();
  }

  function renderCart() {
    const el = document.getElementById("payCartBody");
    const totEl = document.getElementById("payTotal");
    if (!el) return;

    if (!cart.length) {
      el.innerHTML =
        '<p class="text-sm text-slate-400 text-center py-8">Add an invoice line or inventory item.</p>';
    } else {
      el.innerHTML = cart
        .map((l, i) => {
          const unit = Number(l.unitPrice) || 0;
          const qty = Number(l.qty) || 1;
          const lineTotal = unit * qty;
          return `<div class="pay-cart-line flex gap-3 py-3 border-b border-slate-100 text-sm">
            <div class="min-w-0 flex-1">
              <div class="font-medium text-slate-900 truncate">${esc(l.name)}</div>
              <div class="flex items-center mt-1.5">
                <div class="inline-flex items-center rounded-lg border border-slate-200 bg-slate-50 overflow-hidden">
                  <button type="button" class="pay-cart-qty px-2 py-1 text-slate-600 hover:bg-slate-100" data-idx="${i}" data-d="-1" aria-label="Decrease">−</button>
                  <span class="px-2 py-1 min-w-[2rem] text-center tabular-nums font-semibold text-slate-800">${qty}</span>
                  <button type="button" class="pay-cart-qty px-2 py-1 text-slate-600 hover:bg-slate-100" data-idx="${i}" data-d="1" aria-label="Increase">+</button>
                </div>
              </div>
            </div>
            <div class="flex flex-col items-end justify-between shrink-0">
              <button type="button" class="pay-cart-rm text-slate-400 hover:text-red-600 text-lg leading-none" data-idx="${i}" aria-label="Remove">&times;</button>
              <span class="font-bold tabular-nums text-slate-900">₱${peso(lineTotal)}</span>
            </div>
          </div>`;
        })
        .join("");

      el.querySelectorAll(".pay-cart-rm").forEach((btn) => {
        btn.onclick = () => {
          cart.splice(Number(btn.getAttribute("data-idx")), 1);
          renderCart();
        };
      });
      el.querySelectorAll(".pay-cart-qty").forEach((btn) => {
        btn.onclick = () => {
          updateCartQty(Number(btn.getAttribute("data-idx")), Number(btn.getAttribute("data-d")));
        };
      });
    }

    if (totEl) totEl.textContent = peso(cartTotal());
    updatePaymentFields();
  }

  function updatePaymentFields() {
    const method = paymentMethod();
    const cashWrap = document.getElementById("payTenderWrap");
    const refWrap = document.getElementById("payRefWrap");
    const cashBlock = document.getElementById("payCashSummary");
    const errEl = document.getElementById("payCashError");
    const tenderInp = document.getElementById("payTender");
    const totalDue = cartTotal();
    const isCash = method === "Cash";

    if (cashWrap) cashWrap.classList.toggle("hidden", !isCash);
    if (refWrap) refWrap.classList.toggle("hidden", isCash);

    let insufficient = false;
    if (isCash && tenderInp) {
      const tender = parseFloat(tenderInp.value);
      const hasTender = tenderInp.value !== "" && Number.isFinite(tender);
      const change = hasTender ? tender - totalDue : null;
      insufficient = hasTender && change < -0.001;

      tenderInp.classList.toggle("border-red-500", insufficient);
      tenderInp.classList.toggle("ring-1", insufficient);
      tenderInp.classList.toggle("ring-red-200", insufficient);

      if (errEl) {
        errEl.classList.toggle("hidden", !insufficient);
      }
      if (cashBlock) {
        cashBlock.classList.toggle("hidden", !hasTender);
        const dueEl = document.getElementById("payCashDue");
        const recEl = document.getElementById("payCashReceived");
        const chEl = document.getElementById("payCashChange");
        if (dueEl) dueEl.textContent = "₱" + peso(totalDue);
        if (recEl) recEl.textContent = "₱" + peso(tender);
        if (chEl) {
          chEl.textContent = "₱" + peso(Math.max(0, change));
          chEl.className = insufficient
            ? "font-bold tabular-nums text-right text-red-600"
            : "font-bold tabular-nums text-right text-emerald-700";
        }
      }
    } else {
      tenderInp?.classList.remove("border-red-500", "ring-1", "ring-red-200");
      errEl?.classList.add("hidden");
      cashBlock?.classList.add("hidden");
    }

    updateSubmitState(insufficient);
  }

  function updateSubmitState(cashInsufficient) {
    const btn = document.getElementById("paySubmitBtn");
    if (!btn) return;
    const petOk = customerSelected();
    const cartOk = cart.length > 0;
    const method = paymentMethod();
    const methodOk = !!method;
    let cashOk = true;
    if (method === "Cash") {
      const tender = parseFloat(document.getElementById("payTender")?.value);
      cashOk = Number.isFinite(tender) && tender + 0.001 >= cartTotal();
    } else if (method === "GCash" || method === "Card") {
      const ref = (document.getElementById("payRefNo")?.value || "").trim();
      cashOk = ref.length > 0;
    }
    const disabled = !petOk || !cartOk || !methodOk || !cashOk || cashInsufficient;
    btn.disabled = disabled;
    btn.classList.toggle("opacity-50", disabled);
    btn.classList.toggle("cursor-not-allowed", disabled);
  }

  async function loadInventory() {
    inventory = (await Api.inventory.list().catch(() => [])) || [];
  }

  async function loadPets() {
    pets = (await Api.pets.list().catch(() => [])) || [];
    const sel = document.getElementById("payPetSel");
    if (!sel) return;
    const cur = sel.value;
    sel.innerHTML =
      '<option value="">— Select customer —</option>' +
      `<option value="${WALK_IN_PET_VALUE}">Walk-in customer (shop only)</option>` +
      pets
        .map((p) => `<option value="${p.id}">${esc(p.name || "Pet")} · ${esc(p.owner || "")}</option>`)
        .join("");
    if (cur) sel.value = cur;
    updateWalkInHint();
    updateSubmitState(false);
  }

  function onCustomerChange() {
    if (sanitizeCartForWalkIn()) {
      deps.toast("Removed clinic services and invoices — walk-in is for shop purchases only.", false);
      renderCart();
    }
    updateWalkInHint();
    renderPendingForPet();
    renderItemPicker();
    updateSubmitState(false);
  }

  function renderItemPicker() {
    const q = (document.getElementById("payItemSearch")?.value || "").trim().toLowerCase();
    const el = document.getElementById("payItemList");
    if (!el) return;
    let list = inventory.filter((i) => {
      if (isWalkInCustomer() && isService(i)) return false;
      if (!q) return true;
      return (
        (i.name || "").toLowerCase().includes(q) ||
        (i.sku || "").toLowerCase().includes(q) ||
        (i.category || "").toLowerCase().includes(q)
      );
    });
    list = list.slice(0, 48);
    if (!list.length) {
      el.innerHTML = '<p class="text-xs text-slate-400 py-4 text-center col-span-2">No items match.</p>';
      return;
    }
    el.innerHTML = list
      .map((i) => {
        const svc = isService(i);
        const stock = itemStock(i);
        const out = !svc && stock != null && stock <= 0;
        const price = Number(i.unitPrice) || 0;
        const cat = esc(i.category || (svc ? "Service" : "Product"));
        const stockTxt = svc ? "Service" : out ? "Out of stock" : `${stock} in stock`;
        return `<button type="button" class="pay-add-item text-left rounded-lg border p-3 ${
          out
            ? "border-[#E5E7EB] bg-[#F5F7FA] opacity-50 cursor-not-allowed"
            : "border-[#E5E7EB] bg-white hover:border-[#0F5CC0]/40"
        }" data-id="${i.id}" ${out ? "disabled" : ""}>
          <div class="font-medium text-[#111827] text-sm truncate">${esc(i.name)}</div>
          <div class="text-[11px] text-[#6B7280] mt-1">${cat}</div>
          <div class="flex items-center justify-between mt-2 gap-2">
            <span class="text-[11px] ${out ? "text-red-600 font-medium" : "text-[#6B7280]"}">${stockTxt}</span>
            <span class="text-sm font-bold tabular-nums text-[#0F5CC0]">₱${peso(price)}</span>
          </div>
        </button>`;
      })
      .join("");
    el.querySelectorAll(".pay-add-item:not([disabled])").forEach((btn) => {
      btn.onclick = () => {
        const id = Number(btn.getAttribute("data-id"));
        const item = inventory.find((x) => x.id === id);
        if (!item) return;
        if (isWalkInCustomer() && isService(item)) {
          deps.toast("Walk-in customers can only purchase shop products.", true);
          return;
        }
        const stock = itemStock(item);
        if (!isService(item) && stock != null && stock <= 0) return;
        const existing = cart.find((l) => l.kind === "inv" && l.id === id);
        if (existing) existing.qty += 1;
        else
          cart.push({
            kind: "inv",
            id,
            name: item.name,
            unitPrice: Number(item.unitPrice) || 0,
            qty: 1,
          });
        renderCart();
      };
    });
  }

  function addBillingLine(bill) {
    if (!bill) return;
    if (isWalkInCustomer()) {
      deps.toast("Walk-in customers cannot pay clinic invoices. Select a registered pet instead.", true);
      return;
    }
    const subtotal = Number(bill.subtotalAmount != null ? bill.subtotalAmount : bill.amount) || 0;
    const existing = cart.find((l) => l.kind === "billing" && l.billingId === bill.id);
    if (existing) return;
    cart.push({
      kind: "billing",
      billingId: bill.id,
      name: bill.description || `Invoice #${bill.id}`,
      unitPrice: subtotal,
      qty: 1,
    });
    if (bill.petId && document.getElementById("payPetSel")) {
      document.getElementById("payPetSel").value = String(bill.petId);
    }
    renderCart();
  }

  async function buildReceiptHtml(saleId) {
    const receipt = await Api.sales.receipt(saleId);
    const clinic = typeof getClinicSettings === "function" ? getClinicSettings() || {} : {};
    if (global.BayportThermalReceipt?.buildHtml) {
      return global.BayportThermalReceipt.buildHtml(receipt || {}, clinic);
    }
    return `<html><body><pre>Receipt #${saleId}</pre></body></html>`;
  }

  async function printReceipt(saleId) {
    const html = await buildReceiptHtml(saleId);
    const preferred =
      typeof getPreferredReceiptPrinter === "function" ? getPreferredReceiptPrinter() : "";
    if (global.desktopBridge?.printReceipt) {
      const res = await global.desktopBridge.printReceipt({
        html,
        silent: false,
        deviceName: preferred || undefined,
      });
      if (res?.ok) return;
    }
    if (typeof queueCloudPrint === "function") {
      try {
        await queueCloudPrint(html, preferred || "");
        return;
      } catch (_) {
        /* fall through to browser print */
      }
    }
    printHtmlInHiddenFrame(html);
  }

  function printHtmlInHiddenFrame(html) {
    let frame = document.getElementById("payReceiptPrintFrame");
    if (!frame) {
      frame = document.createElement("iframe");
      frame.id = "payReceiptPrintFrame";
      frame.title = "Print receipt";
      frame.setAttribute("aria-hidden", "true");
      frame.style.cssText = "position:fixed;width:0;height:0;border:0;opacity:0;pointer-events:none";
      document.body.appendChild(frame);
    }
    frame.onload = () => {
      try {
        frame.contentWindow.focus();
        frame.contentWindow.print();
      } catch (_) {
        /* ignore */
      }
    };
    frame.srcdoc = html;
  }

  function showReceiptPreview(html, title) {
    ensureModalDom();
    const modal = document.getElementById("payReceiptPreviewModal");
    const frame = document.getElementById("payReceiptPreviewFrame");
    const titleEl = document.getElementById("payReceiptPreviewTitle");
    if (!modal || !frame) return;
    if (titleEl) titleEl.textContent = title || "Receipt";
    frame.srcdoc = html;
    modal.classList.remove("hidden");
    modal.classList.add("flex");
  }

  function hideReceiptPreview() {
    const modal = document.getElementById("payReceiptPreviewModal");
    if (modal) {
      modal.classList.add("hidden");
      modal.classList.remove("flex");
    }
  }

  async function viewReceipt(saleId) {
    const html = await buildReceiptHtml(saleId);
    showReceiptPreview(html, "Invoice / receipt");
  }

  function linesForCheckout() {
    return cart.map((l) => {
      if (l.kind === "billing") {
        return {
          customName: l.name,
          customAmount: Number(l.unitPrice) || 0,
          quantity: l.qty || 1,
          billingRecordId: l.billingId,
        };
      }
      if (l.kind === "sku") {
        return { sku: l.sku, quantity: l.qty, unitPriceOverride: l.unitPrice };
      }
      return { inventoryItemId: l.id, quantity: l.qty || 1 };
    });
  }

  function invoiceLabel(saleId) {
    const y = new Date().getFullYear();
    return `RCPT-${y}-${String(saleId || 0).padStart(4, "0")}`;
  }

  function showSuccessModal(data) {
    lastCheckout = data;
    const m = document.getElementById("paySuccessModal");
    if (!m) return;
    document.getElementById("paySuccessInvoice").textContent = data.invoiceNo || "—";
    document.getElementById("paySuccessPatient").textContent = data.patientName || "—";
    document.getElementById("paySuccessWhen").textContent = data.when || "—";
    document.getElementById("paySuccessMethod").textContent = data.method || "—";
    document.getElementById("paySuccessTotal").textContent = "₱" + peso(data.total || 0);
    m.classList.remove("hidden");
    m.classList.add("flex");
  }

  function hideSuccessModal() {
    const m = document.getElementById("paySuccessModal");
    if (m) {
      m.classList.add("hidden");
      m.classList.remove("flex");
    }
    const hadCheckout = lastCheckout;
    lastCheckout = null;
    if (hadCheckout && typeof deps.onComplete === "function") {
      Promise.resolve(deps.onComplete()).catch(() => {});
    }
  }

  async function submitCheckout() {
    if (!cart.length) {
      deps.toast("Add at least one line to the cart.", true);
      return;
    }
    const sel = document.getElementById("payPetSel")?.value;
    if (!sel) {
      deps.toast("Select a customer for this payment.", true);
      return;
    }
    if (isWalkInCustomer()) {
      const blocked = cart.some((l) => isWalkInBlockedCartLine(l));
      if (blocked) {
        deps.toast("Walk-in customers can only purchase shop products.", true);
        return;
      }
    }
    const totalDue = cartTotal();
    const payMethod = paymentMethod();
    if (!payMethod) {
      deps.toast("Select a payment method.", true);
      return;
    }
    if (payMethod === "Cash") {
      const tender = parseFloat(document.getElementById("payTender")?.value) || 0;
      if (tender + 0.001 < totalDue) {
        deps.toast("Cash received must cover the total.", true);
        return;
      }
    } else if (payMethod === "GCash" || payMethod === "Card") {
      const ref = (document.getElementById("payRefNo")?.value || "").trim();
      if (!ref) {
        deps.toast("Enter a reference number.", true);
        return;
      }
    }
    const btn = document.getElementById("paySubmitBtn");
    if (btn) btn.disabled = true;
    try {
      const refNo = (document.getElementById("payRefNo")?.value || "").trim();
      let methodForApi = payMethod;
      if ((payMethod === "GCash" || payMethod === "Card") && refNo) {
        methodForApi = `${payMethod} · Ref ${refNo}`;
      }
      const res = await Api.pos.checkout({
        petId: isWalkInCustomer() ? null : Number(sel),
        paymentMethod: methodForApi,
        discountAmount: 0,
        lines: linesForCheckout(),
        fulfillPrescriptionIds: [],
      });
      const pet = selectedPet();
      const when = new Date().toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
      closeModal(false);
      showSuccessModal({
        saleId: res.saleId,
        invoiceNo: invoiceLabel(res.saleId),
        patientName: isWalkInCustomer() ? "Walk-in customer" : pet?.name || "—",
        when,
        method: payMethod,
        total: res.total != null ? res.total : totalDue,
      });
      await deps.onComplete();
      try {
        global.dispatchEvent(new CustomEvent("bayport:sale-recorded", { detail: { saleId: res.saleId } }));
      } catch (_) {
        /* ignore */
      }
    } catch (e) {
      deps.toast((e.message || "Payment failed").slice(0, 180), true);
    } finally {
      if (btn) updateSubmitState(false);
    }
  }

  function closeModal(clearCart) {
    if (clearCart !== false) cart = [];
    const m = document.getElementById("payModal");
    if (m) {
      m.classList.add("hidden");
      m.classList.remove("flex");
    }
  }

  async function renderPendingForPet() {
    const petId = document.getElementById("payPetSel")?.value;
    const wrap = document.getElementById("payPendingBills");
    if (!wrap) return;
    if (!petId || isWalkInCustomer()) {
      wrap.innerHTML = "";
      wrap.classList.add("hidden");
      updateSubmitState(false);
      return;
    }
    const bills = (await Api.billing.list().catch(() => [])) || [];
    const pending = bills.filter(
      (b) => Number(b.petId) === Number(petId) && String(b.status || "").toUpperCase() === "PENDING",
    );
    if (!pending.length) {
      wrap.innerHTML = "";
      wrap.classList.add("hidden");
      updateSubmitState(false);
      return;
    }
    wrap.classList.remove("hidden");
    wrap.innerHTML =
      `<div class="text-xs font-semibold text-slate-500 uppercase mb-1">Pending invoices</div>` +
      pending
        .map(
          (b) =>
            `<button type="button" class="pay-add-bill w-full text-left text-xs px-2 py-1.5 rounded border border-amber-200 bg-amber-50 hover:bg-amber-100 mb-1" data-bid="${b.id}">
              ${esc(b.description || "Invoice")} · ₱${peso(b.amount)}</button>`,
        )
        .join("");
    wrap.querySelectorAll(".pay-add-bill").forEach((btn) => {
      btn.onclick = () => {
        const bill = pending.find((b) => String(b.id) === btn.getAttribute("data-bid"));
        addBillingLine(bill);
      };
    });
    updateSubmitState(false);
  }

  async function openModal(opts) {
    opts = opts || {};
    cart = [];
    hideSuccessModal();
    const m = document.getElementById("payModal");
    if (!m) return;
    m.classList.remove("hidden");
    m.classList.add("flex");
    document.getElementById("payTender").value = "";
    document.getElementById("payRefNo").value = "";
    document.getElementById("payMethod").value = "Cash";
    document.getElementById("payItemSearch").value = "";
    await Promise.all([loadInventory(), loadPets()]);
    if (opts.petId) document.getElementById("payPetSel").value = String(opts.petId);
    else if (opts.walkIn) document.getElementById("payPetSel").value = WALK_IN_PET_VALUE;
    if (opts.billingRow) addBillingLine(opts.billingRow);
    await renderPendingForPet();
    updateWalkInHint();
    renderItemPicker();
    renderCart();
  }

  function wireReceiptPreviewModal() {
    const closeBtn = document.getElementById("payReceiptPreviewClose");
    const printBtn = document.getElementById("payReceiptPreviewPrint");
    const modal = document.getElementById("payReceiptPreviewModal");
    if (!modal || modal.dataset.bpWired === "1") return;
    modal.dataset.bpWired = "1";
    closeBtn.onclick = hideReceiptPreview;
    printBtn.onclick = () => {
      const frame = document.getElementById("payReceiptPreviewFrame");
      if (frame?.contentWindow) {
        try {
          frame.contentWindow.focus();
          frame.contentWindow.print();
        } catch (_) {
          deps.toast("Unable to print from preview.", true);
        }
      }
    };
    modal.addEventListener("click", (e) => {
      if (e.target.id === "payReceiptPreviewModal") hideReceiptPreview();
    });
  }

  function ensureReceiptPreviewDom() {
    if (document.getElementById("payReceiptPreviewModal")) {
      wireReceiptPreviewModal();
      return;
    }
    const wrap = document.createElement("div");
    wrap.innerHTML = `
<div id="payReceiptPreviewModal" class="hidden fixed inset-0 bg-black/50 z-[270] items-center justify-center p-4">
  <div class="bg-white rounded-xl w-full max-w-4xl h-[85vh] flex flex-col shadow-xl border border-slate-200 overflow-hidden">
    <div class="flex items-center justify-between px-4 py-3 border-b border-slate-100 shrink-0">
      <h3 id="payReceiptPreviewTitle" class="font-semibold text-slate-900">Invoice / receipt</h3>
      <div class="flex items-center gap-2">
        <button type="button" id="payReceiptPreviewPrint" class="px-3 py-1.5 rounded-lg border border-slate-200 text-sm font-medium hover:bg-slate-50">Print</button>
        <button type="button" id="payReceiptPreviewClose" class="px-3 py-1.5 rounded-lg border border-slate-200 text-sm font-medium hover:bg-slate-50">Close</button>
      </div>
    </div>
    <iframe id="payReceiptPreviewFrame" class="w-full flex-1 min-h-0 border-0 bg-white" title="Receipt preview"></iframe>
  </div>
</div>`;
    while (wrap.firstElementChild) document.body.appendChild(wrap.firstElementChild);
    wireReceiptPreviewModal();
  }

  function ensureModalDom() {
    ensureReceiptPreviewDom();
    if (document.getElementById("payModal")) return;
    const wrap = document.createElement("div");
    wrap.innerHTML = `
<div id="payModal" class="hidden fixed inset-0 bg-black/50 z-[250] items-center justify-center p-3 sm:p-4">
  <div class="bg-white rounded-lg w-full max-w-4xl max-h-[94vh] flex flex-col shadow-lg overflow-hidden border border-[#E5E7EB]">
    <div class="flex items-center justify-between px-5 py-4 border-b border-[#E5E7EB] shrink-0">
      <h2 class="text-lg font-semibold text-[#111827]">Receive payment</h2>
      <button type="button" id="payCloseBtn" class="p-2 rounded-lg hover:bg-[#F5F7FA] text-[#6B7280] text-xl leading-none">&times;</button>
    </div>
    <div class="flex-1 overflow-y-auto p-4 sm:p-5">
      <div class="grid lg:grid-cols-2 gap-5 lg:gap-6">
        <div class="space-y-3 min-w-0">
          <label class="block text-xs font-medium text-slate-600">Customer <span class="text-red-500">*</span>
            <select id="payPetSel" class="mt-1 w-full border border-[#E5E7EB] rounded-lg px-3 py-2.5 text-sm bg-white focus:ring-2 focus:ring-[#0F5CC0]/20 focus:border-[#0F5CC0]"></select>
          </label>
          <p id="payWalkInHint" class="hidden text-xs text-slate-500 bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 leading-relaxed">Shop purchases only — no consultation, clinic services, or pending invoices.</p>
          <div id="payPendingBills" class="hidden"></div>
          <label class="block text-xs font-medium text-slate-600">Search inventory
            <input id="payItemSearch" type="search" class="mt-1 w-full border border-[#E5E7EB] rounded-lg px-3 py-2.5 text-sm focus:ring-2 focus:ring-[#0F5CC0]/20" placeholder="Medicine, vaccine, service…"/>
          </label>
          <div id="payItemList" class="grid grid-cols-1 sm:grid-cols-2 gap-2 max-h-[min(42vh,360px)] overflow-y-auto pr-1"></div>
        </div>
        <div class="space-y-4 min-w-0 flex flex-col">
          <div class="text-xs font-semibold text-slate-500 uppercase tracking-wide">Cart</div>
          <div id="payCartBody" class="border border-[#E5E7EB] rounded-lg px-3 min-h-[140px] max-h-[min(34vh,280px)] overflow-y-auto bg-[#F5F7FA]"></div>
          <div class="rounded-lg border border-[#E5E7EB] bg-white p-4 text-sm">
            <div class="flex justify-between items-end gap-4">
              <span class="text-xs font-medium uppercase tracking-wide text-[#6B7280]">Total due</span>
              <span class="text-2xl font-bold tabular-nums text-[#0F5CC0]">₱<span id="payTotal">0.00</span></span>
            </div>
          </div>
          <label class="block text-xs font-medium text-slate-600">Payment method <span class="text-red-500">*</span>
            <select id="payMethod" class="mt-1 w-full border border-[#E5E7EB] rounded-lg px-3 py-2.5 text-sm bg-white focus:ring-2 focus:ring-[#0F5CC0]/20">
              <option value="Cash">Cash</option>
              <option value="GCash">GCash</option>
              <option value="Card">Credit / Debit Card</option>
            </select>
          </label>
          <div id="payTenderWrap">
            <label class="block text-xs font-medium text-slate-600">Cash received (₱)
              <input id="payTender" type="number" min="0" step="0.01" class="mt-1 w-full border border-[#E5E7EB] rounded-lg px-3 py-2.5 text-sm tabular-nums focus:ring-2 focus:ring-[#0F5CC0]/20" placeholder="0.00"/>
            </label>
            <p id="payCashError" class="hidden text-xs text-red-600 font-medium mt-1">Insufficient payment.</p>
            <div id="payCashSummary" class="hidden mt-3 rounded-lg bg-slate-50 border border-slate-200 p-3 space-y-2 text-sm">
              <div class="flex justify-between"><span class="text-slate-500">Total due</span><span id="payCashDue" class="tabular-nums font-medium text-right">₱0.00</span></div>
              <div class="flex justify-between"><span class="text-slate-500">Cash received</span><span id="payCashReceived" class="tabular-nums font-medium text-right">₱0.00</span></div>
              <div class="flex justify-between border-t border-slate-200 pt-2"><span class="text-slate-700 font-medium">Change</span><span id="payCashChange" class="font-bold tabular-nums text-right text-emerald-700">₱0.00</span></div>
            </div>
          </div>
          <div id="payRefWrap" class="hidden">
            <label class="block text-xs font-medium text-slate-600">Reference number <span class="text-red-500">*</span>
              <input id="payRefNo" type="text" class="mt-1 w-full border border-slate-200 rounded-lg px-3 py-2.5 text-sm" placeholder="Transaction / reference no."/>
            </label>
          </div>
          <button type="button" id="paySubmitBtn" class="w-full py-3.5 rounded-lg bg-[#0F5CC0] text-white font-semibold text-sm disabled:opacity-50 disabled:cursor-not-allowed mt-auto">Complete payment</button>
        </div>
      </div>
    </div>
  </div>
</div>
<div id="paySuccessModal" class="hidden fixed inset-0 bg-black/50 z-[260] items-center justify-center p-4">
  <div class="bg-white rounded-2xl w-full max-w-md shadow-2xl overflow-hidden border border-slate-200">
    <div class="px-6 pt-8 pb-4 text-center">
      <div class="mx-auto w-14 h-14 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center text-2xl font-bold mb-4">✓</div>
      <h3 class="text-xl font-bold text-slate-900">Payment successful</h3>
    </div>
    <div class="px-6 pb-2 space-y-3 text-sm">
      <div class="flex justify-between gap-3"><span class="text-slate-500">Invoice number</span><span id="paySuccessInvoice" class="font-semibold tabular-nums text-right">—</span></div>
      <div class="flex justify-between gap-3"><span class="text-slate-500">Customer</span><span id="paySuccessPatient" class="font-semibold text-right">—</span></div>
      <div class="flex justify-between gap-3"><span class="text-slate-500">Date &amp; time</span><span id="paySuccessWhen" class="font-medium text-right">—</span></div>
      <div class="flex justify-between gap-3"><span class="text-slate-500">Payment method</span><span id="paySuccessMethod" class="font-medium text-right">—</span></div>
      <div class="flex justify-between gap-3 border-t border-[#E5E7EB] pt-3"><span class="text-[#374151] font-semibold">Total paid</span><span id="paySuccessTotal" class="text-lg font-bold tabular-nums text-[#0F5CC0] text-right">₱0.00</span></div>
    </div>
    <div class="px-6 py-5 flex flex-col sm:flex-row gap-2">
      <button type="button" id="paySuccessPrint" class="flex-1 py-2.5 rounded-lg border border-slate-200 text-sm font-semibold hover:bg-slate-50">Print receipt</button>
      <button type="button" id="paySuccessView" class="flex-1 py-2.5 rounded-lg border border-slate-200 text-sm font-semibold hover:bg-slate-50">View invoice</button>
      <button type="button" id="paySuccessDone" class="flex-1 py-2.5 rounded-lg bg-[#0F5CC0] text-white text-sm font-semibold">Done</button>
    </div>
  </div>
</div>`;
    if (!document.getElementById("pay-checkout-styles")) {
      const style = document.createElement("style");
      style.id = "pay-checkout-styles";
      style.textContent = "";
      document.head.appendChild(style);
    }
    while (wrap.firstElementChild) document.body.appendChild(wrap.firstElementChild);

    document.getElementById("payCloseBtn").onclick = () => closeModal();
    document.getElementById("payModal").addEventListener("click", (e) => {
      if (e.target.id === "payModal") closeModal();
    });
    document.getElementById("payItemSearch").addEventListener("input", renderItemPicker);
    document.getElementById("payMethod").addEventListener("change", () => {
      renderCart();
    });
    document.getElementById("payTender").addEventListener("input", updatePaymentFields);
    document.getElementById("payRefNo").addEventListener("input", () => updateSubmitState(false));
    document.getElementById("payPetSel").addEventListener("change", onCustomerChange);
    document.getElementById("paySubmitBtn").onclick = submitCheckout;

    document.getElementById("paySuccessDone").onclick = hideSuccessModal;
    document.getElementById("paySuccessPrint").onclick = () => {
      if (!lastCheckout?.saleId) return;
      printReceipt(lastCheckout.saleId)
        .then(() => deps.toast("Sent to printer.", false))
        .catch((e) => deps.toast((e.message || "Print failed").slice(0, 120), true));
    };
    document.getElementById("paySuccessView").onclick = () => {
      if (!lastCheckout?.saleId) return;
      viewReceipt(lastCheckout.saleId).catch((e) =>
        deps.toast((e.message || "Could not open invoice").slice(0, 120), true),
      );
    };
    document.getElementById("paySuccessModal").addEventListener("click", (e) => {
      if (e.target.id === "paySuccessModal") hideSuccessModal();
    });
  }

  global.initBayportBillingCheckout = function (options) {
    deps = { ...deps, ...options };
    ensureModalDom();
    if (!global.BayportThermalReceipt && !document.querySelector('script[src*="thermal-receipt.js"]')) {
      const s = document.createElement("script");
      s.src = "assets/thermal-receipt.js";
      document.head.appendChild(s);
    }
  };

  global.openBayportPaymentModal = openModal;
})(window);
