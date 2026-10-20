/**
 * Bayport thermal receipt formatting — edit this file to match your printer workflow.
 *
 * - buildHtml()     → passed to desktop print bridge / browser print (80mm-oriented CSS).
 * - buildPlainText()→ 40-column plain text for raw ESC/POS, email, or a custom agent.
 *
 * Replace or wrap these functions when integrating a real thermal driver (USB, network, etc.).
 */
(function (global) {
  var RECEIPT_CHARS = 40;

  function escapeHtml(str) {
    return String(str ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function money(n) {
    var x = Number(n);
    if (typeof x !== "number" || Number.isNaN(x)) return "0.00";
    return x.toFixed(2);
  }

  function centerLine(text, width) {
    var t = String(text).trim();
    var w = width || RECEIPT_CHARS;
    if (t.length >= w) return t.slice(0, w);
    var pad = Math.max(0, Math.floor((w - t.length) / 2));
    return " ".repeat(pad) + t;
  }

  function wrapWords(text, width) {
    var words = String(text).split(/\s+/).filter(Boolean);
    var lines = [];
    var cur = "";
    for (var i = 0; i < words.length; i++) {
      var w = words[i];
      var next = cur ? cur + " " + w : w;
      if (next.length <= width) cur = next;
      else {
        if (cur) lines.push(cur);
        cur = w.length > width ? w.slice(0, width) : w;
        while (cur.length > width) {
          lines.push(cur.slice(0, width));
          cur = cur.slice(width);
        }
      }
    }
    if (cur) lines.push(cur);
    return lines.length ? lines : [""];
  }

  function itemBlock(row, width) {
    var name = String((row && row.itemName) || "Item").trim() || "Item";
    var qty = Number(row && row.quantity) || 0;
    var lineTotal = Number(row && (row.lineTotal != null ? row.lineTotal : 0)) || 0;
    var unit =
      row && row.unitPrice != null && row.unitPrice !== ""
        ? Number(row.unitPrice)
        : qty
          ? lineTotal / qty
          : lineTotal;
    var rows = wrapWords(name, width);
    rows.push(twoCol(qty + " x " + money(unit), "\u20B1" + money(lineTotal), width));
    return rows;
  }

  function twoCol(left, right, width) {
    var l = String(left || "").trim();
    var r = String(right || "").trim();
    if (!r) return l.slice(0, width);
    if (l.length + 1 + r.length <= width) {
      return l + " ".repeat(width - l.length - r.length) + r;
    }
    return l.slice(0, Math.max(0, width - r.length - 1)) + " " + r;
  }

  /**
   * @param {object} receipt - API receipt DTO (saleId, lines[], total, petName, paymentMethod, occurredAt, …)
   * @param {object} clinic - { name, address }
   */
  function buildPlainText(receipt, clinic) {
    var c = clinic || {};
    var W = RECEIPT_CHARS;
    var SEP = "-".repeat(W);
    var out = [];

    out.push(SEP);
    out.push(centerLine(c.name || "Bayport Veterinary Clinic", W));
    wrapWords((c.address || "322 Quirino Ave., Brgy. Don Galo, Parañaque City").replace(/\s+/g, " ").trim(), W).forEach(function (ln) {
      out.push(ln);
    });
    out.push(SEP);

    var sid = receipt && receipt.saleId != null ? String(receipt.saleId) : "";
    var occurred = receipt && receipt.occurredAt;
    if (occurred) {
      try {
        var d = new Date(occurred);
        if (!Number.isNaN(d.getTime())) {
          out.push(String(d.toLocaleString(undefined, { dateStyle: "short", timeStyle: "short" })).slice(0, W));
        }
      } catch (e) {
        out.push(String(occurred).slice(0, W));
      }
    }
    out.push(("Pet: " + String((receipt && receipt.petName) || "Walk-in")).slice(0, W));
    out.push(("Owner: " + String((receipt && (receipt.ownerName || receipt.owner)) || "—")).slice(0, W));
    out.push(SEP);

    var lines = Array.isArray(receipt && receipt.lines) ? receipt.lines : [];
    for (var i = 0; i < lines.length; i++) {
      var block = itemBlock(lines[i], W);
      for (var r = 0; r < block.length; r++) out.push(block[r]);
    }

    var subtotal = 0;
    for (var s = 0; s < lines.length; s++) subtotal += Number(lines[s].lineTotal) || 0;
    var discount = Number(receipt && (receipt.discountAmount != null ? receipt.discountAmount : receipt.discount)) || 0;
    if (!(discount > 0.009) && receipt && receipt.note) {
      var discountMatch = String(receipt.note).match(/Discount:\s*-?\s*[^\d]*([0-9][0-9,]*(?:\.[0-9]+)?)/i);
      if (discountMatch) discount = Number(discountMatch[1].replace(/,/g, "")) || 0;
    }
    var total = Number(receipt && receipt.total) || 0;
    var paidRaw = receipt && (receipt.amountPaid != null ? receipt.amountPaid : receipt.paidAmount);
    out.push(SEP);
    if (discount > 0.009) {
      out.push(twoCol("Subtotal", "\u20B1" + money(subtotal), W));
      out.push(twoCol("Discount", "-\u20B1" + money(discount), W));
    }
    out.push(twoCol("TOTAL", "\u20B1" + money(total), W));
    if (paidRaw != null && paidRaw !== "" && Number(paidRaw) + 0.009 < total) {
      var paid = Number(paidRaw) || 0;
      out.push(twoCol("Paid", "\u20B1" + money(paid), W));
      out.push(twoCol("Balance", "\u20B1" + money(Math.max(0, total - paid)), W));
    }
    var method = String((receipt && receipt.paymentMethod) || "").trim();
    var reference = String((receipt && (receipt.paymentReference || receipt.reference)) || "").trim();
    if (method || reference) {
      var pay = method && reference ? method + " " + reference : method || reference;
      wrapWords("Paid by " + pay, W).forEach(function (ln) {
        out.push(ln);
      });
    }
    var code = String((receipt && (receipt.invoiceNo || receipt.transactionCode || receipt.txnCode)) || "").trim();
    if (!code && sid) code = "INV-" + sid;
    if (code) out.push(("Code: " + code).slice(0, W));
    out.push("Thank you for your visit!");

    return out.join("\n");
  }

  function logoDataUrl(clinic) {
    if (clinic && clinic.logoDataUrl && String(clinic.logoDataUrl).trim()) {
      return String(clinic.logoDataUrl).trim();
    }
    if (typeof global.location !== "undefined" && global.location && global.location.href) {
      try {
        return new URL("assets/logo.png", global.location.href).href;
      } catch (e) {
        return "";
      }
    }
    return "";
  }

  function buildHtml(receipt, clinic) {
    var plain = buildPlainText(receipt, clinic);
    var logo = logoDataUrl(clinic);
    var title = "Receipt #" + escapeHtml((receipt && receipt.saleId) || "");
    return (
      "<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"/><title>" +
      title +
      "</title>\n<style>\n" +
      "@page { size: 80mm auto; margin: 2mm; }\n" +
      "html, body { height: auto; margin: 0; padding: 0; background: #fff; }\n" +
      "body { font-family: ui-monospace, 'Cascadia Mono', 'Consolas', monospace; font-size: 11px; line-height: 1.25; padding: 1mm 2mm; color: #000; width: 72mm; }\n" +
      "pre { margin: 0; white-space: pre-wrap; word-break: break-word; font-family: inherit; font-size: inherit; page-break-inside: avoid; }\n" +
      ".logo { max-height: 28px; max-width: 28mm; display: block; margin: 0 auto 2px; page-break-after: avoid; }\n" +
      "</style></head><body>\n" +
      (logo ? '<img class="logo" src="' + escapeHtml(logo) + '" alt=""/>\n' : "") +
      "<pre>" +
      escapeHtml(plain) +
      "</pre>\n</body></html>"
    );
  }

  global.BayportThermalReceipt = {
    RECEIPT_CHARS: RECEIPT_CHARS,
    buildPlainText: buildPlainText,
    buildHtml: buildHtml,
  };
})(typeof window !== "undefined" ? window : this);
