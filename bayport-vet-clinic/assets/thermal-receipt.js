/**
 * Bayport thermal receipt formatting — edit this file to match your printer workflow.
 *
 * - buildHtml()     → passed to desktop print bridge / browser print (80mm-oriented CSS).
 * - buildPlainText()→ 42-column plain text for raw ESC/POS, email, or a custom agent.
 *
 * Replace or wrap these functions when integrating a real thermal driver (USB, network, etc.).
 */
(function (global) {
  var RECEIPT_CHARS = 42;

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

  function padLeft(s, n) {
    s = String(s);
    return s.length >= n ? s.slice(-n) : " ".repeat(n - s.length) + s;
  }

  function lineItemRow(name, qty, lineTotal, width) {
    var qtyN = Number(qty) || 0;
    var first = qtyN + " x " + String(name).trim();
    var lines = wrapWords(first, width);
    var out = lines.slice(0, 3);
    var last = out[out.length - 1] || "";
    var priceStr = "\u20B1" + money(lineTotal);
    if (last.length + 1 + priceStr.length <= width) {
      out[out.length - 1] = last + " ".repeat(width - last.length - priceStr.length) + priceStr;
    } else {
      out.push(padLeft(priceStr, width));
    }
    return out;
  }

  /**
   * @param {object} receipt - API receipt DTO (saleId, lines[], total, petName, paymentMethod, occurredAt, …)
   * @param {object} clinic - { name, address }
   */
  function buildPlainText(receipt, clinic) {
    var c = clinic || {};
    var W = RECEIPT_CHARS;
    var out = [];

    out.push(centerLine(c.name || "Bayport Veterinary Clinic", W));
    wrapWords(c.address || "", W).forEach(function (ln) {
      out.push(ln);
    });
    out.push("-".repeat(W));

    var sid = receipt && receipt.saleId != null ? String(receipt.saleId) : "";
    out.push("Sale #" + sid);
    var occurred = receipt && receipt.occurredAt;
    if (occurred) {
      try {
        var d = new Date(occurred);
        if (!Number.isNaN(d.getTime())) {
          out.push(d.toLocaleString(undefined, { dateStyle: "short", timeStyle: "short" }));
        }
      } catch (e) {
        out.push(String(occurred).slice(0, W));
      }
    }
    out.push(("Pet: " + String((receipt && receipt.petName) || "Walk-in")).slice(0, W));
    out.push(("Pay: " + String((receipt && receipt.paymentMethod) || "Cash")).slice(0, W));
    out.push("-".repeat(W));

    var lines = Array.isArray(receipt && receipt.lines) ? receipt.lines : [];
    for (var i = 0; i < lines.length; i++) {
      var l = lines[i];
      var rows = lineItemRow(l.itemName || "", l.quantity, l.lineTotal ?? 0, W);
      for (var r = 0; r < rows.length; r++) out.push(rows[r]);
    }

    out.push("-".repeat(W));
    out.push(padLeft("TOTAL  \u20B1" + money(receipt && receipt.total), W));
    out.push("");
    out.push(centerLine("Thank you for your visit.", W));

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
      "body { font-family: ui-monospace, 'Cascadia Mono', 'Consolas', monospace; font-size: 11px; line-height: 1.35; margin: 0; padding: 4mm; color: #000; max-width: 72mm; }\n" +
      "pre { margin: 0; white-space: pre-wrap; word-break: break-word; font-family: inherit; font-size: inherit; }\n" +
      ".logo { max-height: 40px; max-width: 120px; display: block; margin: 0 auto 6px; }\n" +
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
