/**
 * Snapshot checks for thermal receipt text. Run with the desktop Electron binary:
 *   electron.exe assets/thermal-receipt-check.js
 * from the clinic folder (the folder that contains assets/).
 */
"use strict";

const path = require("path");
global.window = global;
require(path.join(__dirname, "thermal-receipt.js"));

const build = global.BayportThermalReceipt.buildPlainText;
const html = global.BayportThermalReceipt.buildHtml;
const W = global.BayportThermalReceipt.RECEIPT_CHARS;
const clinic = { name: "Bayport Veterinary Clinic", address: "322 Quirino Ave" };

function fail(message) {
  console.error("FAIL " + message);
  process.exitCode = 1;
}

function assert(cond, message) {
  if (!cond) fail(message);
}

function linesOf(receipt) {
  return build(receipt, clinic).split("\n");
}

function widthsOk(rows, label) {
  rows.forEach((row, i) => {
    if (row.length > W) fail(label + " line " + i + " is " + row.length + " columns");
  });
}

const one = {
  saleId: 193,
  occurredAt: "2026-10-16T09:00:00",
  petName: "Milo",
  ownerName: "Ana Cruz",
  paymentMethod: "Cash",
  paymentReference: "OR-19",
  total: 350,
  lines: [{ itemName: "Consultation Fee", quantity: 1, unitPrice: 350, lineTotal: 350 }],
};
const oneRows = linesOf(one);
widthsOk(oneRows, "one item");
assert(oneRows.some((r) => r === "Consultation Fee"), "item name on its own line");
assert(oneRows.some((r) => r.startsWith("1 x 350.00") && r.endsWith("\u20B1350.00")), "qty x price and line total");
assert(!oneRows.some((r) => r.startsWith("1 x Consultation")), "qty is not prefixed onto the item name");
assert(!oneRows.includes(""), "no blank lines");
assert(!oneRows.some((r) => r.startsWith("Subtotal")), "no subtotal without a discount");
assert(!oneRows.some((r) => r.startsWith("Discount")), "no discount line without a discount");
assert(oneRows.filter((r) => r.startsWith("Paid by ")).length === 1, "payment once");
assert(oneRows.includes("Paid by Cash OR-19"), "method and reference together");
assert(oneRows.filter((r) => r.startsWith("Code:")).length === 1, "code once");
assert(oneRows.includes("Code: INV-193"), "invoice code from sale id");
assert(oneRows.filter((r) => r.indexOf("Thank you") >= 0).length === 1, "thank-you once");

const manyLines = [];
for (let i = 1; i <= 12; i++) {
  manyLines.push({ itemName: "Item " + i, quantity: 2, unitPrice: 10, lineTotal: 20 });
}
const many = linesOf({ saleId: 2, petName: "A", ownerName: "B", paymentMethod: "GCash", total: 240, lines: manyLines });
widthsOk(many, "many items");
assert(many.filter((r) => r.startsWith("2 x 10.00")).length === 12, "twelve quantity lines");

const longName = "Annual Wellness Examination with Blood Panel and Deworming";
const longRows = linesOf({
  saleId: 3,
  petName: "C",
  ownerName: "D",
  paymentMethod: "Cash",
  total: 1800,
  lines: [{ itemName: longName, quantity: 1, unitPrice: 1800, lineTotal: 1800 }],
});
widthsOk(longRows, "long name");
assert(longRows[0].length <= W, "wrapped");
assert(!longRows.some((r) => r.startsWith("1 x Annual")), "long name is not prefixed with quantity");
assert(longRows.some((r) => r.startsWith("1 x 1800.00")), "price line after the name");

const discountRows = linesOf({
  saleId: 4,
  petName: "E",
  ownerName: "F",
  paymentMethod: "Cash",
  discountAmount: 50,
  total: 300,
  lines: [{ itemName: "Vaccine", quantity: 1, unitPrice: 350, lineTotal: 350 }],
});
widthsOk(discountRows, "discount");
assert(discountRows.some((r) => r.startsWith("Subtotal") && r.endsWith("\u20B1350.00")), "subtotal when discounted");
assert(discountRows.some((r) => r.startsWith("Discount") && r.indexOf("50.00") >= 0), "discount line");
assert(discountRows.some((r) => r.startsWith("TOTAL") && r.endsWith("\u20B1300.00")), "total unchanged by the formatter");

const noteRows = linesOf({
  saleId: 9,
  petName: "I",
  ownerName: "J",
  paymentMethod: "Cash",
  total: 300,
  note: "Payment: Cash. Discount: -\u20B150.00. Consultation Fee",
  lines: [{ itemName: "Consultation Fee", quantity: 1, unitPrice: 350, lineTotal: 350 }],
});
assert(noteRows.some((r) => r.startsWith("Subtotal")), "discount stored in the sale note");
assert(noteRows.some((r) => r.startsWith("Discount") && r.indexOf("50.00") >= 0), "peso discount from the sale note");
assert(noteRows.some((r) => r.startsWith("TOTAL") && r.endsWith("\u20B1300.00")), "note discount does not change the total");

const partialRows = linesOf({
  saleId: 5,
  petName: "G",
  ownerName: "H",
  paymentMethod: "Cash",
  total: 500,
  amountPaid: 200,
  lines: [{ itemName: "Surgery deposit", quantity: 1, unitPrice: 500, lineTotal: 500 }],
});
widthsOk(partialRows, "partial");
assert(partialRows.some((r) => r.startsWith("Paid") && r.endsWith("\u20B1200.00")), "amount paid");
assert(partialRows.some((r) => r.startsWith("Balance") && r.endsWith("\u20B1300.00")), "balance");
assert(!partialRows.some((r) => r.startsWith("Subtotal")), "partial payment does not invent a subtotal");

const page = html(one, clinic);
assert(page.indexOf("@page { size: 80mm auto; margin: 2mm; }") >= 0, "80mm page size");
assert(page.indexOf("height: auto") >= 0, "auto height to avoid a blank page");
assert(page.indexOf("max-height: 28px") >= 0, "smaller logo");

if (process.exitCode) {
  console.error("Receipt text checks failed");
  process.exit(1);
}
console.log("Receipt text checks passed");
process.exit(0);
