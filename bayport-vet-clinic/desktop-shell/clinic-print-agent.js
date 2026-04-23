#!/usr/bin/env node
const fs = require("fs");
const os = require("os");
const path = require("path");
const { spawnSync } = require("child_process");

const API_BASE = (process.env.BAYPORT_API_BASE || "http://localhost:8080/api").replace(/\/+$/, "");
const API_TOKEN = process.env.BAYPORT_API_TOKEN || "";
const BRANCH_CODE = process.env.BAYPORT_BRANCH_CODE || "default-clinic";
const PRINT_COMMAND = process.env.BAYPORT_PRINT_COMMAND || "";
const POLL_MS = Number(process.env.BAYPORT_PRINT_POLL_MS || 5000);

async function api(pathname, method = "GET", body) {
  const res = await fetch(`${API_BASE}${pathname}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(API_TOKEN ? { Authorization: `Bearer ${API_TOKEN}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`${method} ${pathname} failed: ${res.status}`);
  return res.json();
}

function runPrintCommand(htmlPath) {
  if (!PRINT_COMMAND) {
    throw new Error("BAYPORT_PRINT_COMMAND is not configured");
  }
  const command = PRINT_COMMAND.replace("{file}", `"${htmlPath}"`);
  const result = spawnSync(command, { shell: true, stdio: "inherit" });
  if (result.status !== 0) {
    throw new Error(`Print command failed with status ${result.status}`);
  }
}

async function handleJob(job) {
  await api(`/print-jobs/${job.id}/processing`, "POST");
  const tempFile = path.join(os.tmpdir(), `bayport-receipt-${job.id}.html`);
  try {
    fs.writeFileSync(tempFile, job.receiptHtml || "", "utf8");
    runPrintCommand(tempFile);
    await api(`/print-jobs/${job.id}/printed`, "POST");
    console.log(`Printed job #${job.id}`);
  } catch (err) {
    await api(`/print-jobs/${job.id}/failed`, "POST", { error: err.message || "Print failed" });
    console.error(`Failed job #${job.id}:`, err.message || err);
  } finally {
    try { fs.unlinkSync(tempFile); } catch {}
  }
}

async function pollLoop() {
  console.log(`Bayport print agent started for branch "${BRANCH_CODE}" at ${API_BASE}`);
  while (true) {
    try {
      const jobs = await api(`/print-jobs/poll?branchCode=${encodeURIComponent(BRANCH_CODE)}`);
      for (const job of jobs || []) {
        await handleJob(job);
      }
    } catch (err) {
      console.error("Polling failed:", err.message || err);
    }
    await new Promise((r) => setTimeout(r, POLL_MS));
  }
}

pollLoop();
