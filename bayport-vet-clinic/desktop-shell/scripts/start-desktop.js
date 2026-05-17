#!/usr/bin/env node
const { spawnSync, spawn } = require('child_process');
const path = require('path');

const shellDir = path.resolve(__dirname, '..');

const ensure = spawnSync(process.execPath, [path.join(__dirname, 'ensure-backend-jar.js')], {
  stdio: 'inherit',
  cwd: shellDir,
});
if (ensure.status !== 0) process.exit(ensure.status || 1);

console.log('[desktop] Launching Bayport (backend + UI)...');
const electron = spawn(
  process.platform === 'win32' ? 'npx.cmd' : 'npx',
  ['electron', '.'],
  { cwd: shellDir, stdio: 'inherit', shell: process.platform === 'win32' }
);

electron.on('exit', (code) => process.exit(code ?? 0));
