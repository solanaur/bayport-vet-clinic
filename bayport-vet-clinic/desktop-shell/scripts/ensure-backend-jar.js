#!/usr/bin/env node
/**
 * Ensures bayport-backend JAR exists and is up to date before launching Electron.
 */
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const backendRoot = path.resolve(__dirname, '..', '..', 'bayport-backend');
const jarPath = path.join(backendRoot, 'target', 'bayport-backend-0.0.1-SNAPSHOT.jar');

const watchPaths = [
  path.join(backendRoot, 'src', 'main', 'java'),
  path.join(backendRoot, 'src', 'main', 'resources'),
  path.join(backendRoot, 'pom.xml'),
];

function newestMtime() {
  let newest = 0;

  function walk(entry) {
    if (!fs.existsSync(entry)) return;
    let st;
    try {
      st = fs.statSync(entry);
    } catch {
      return;
    }
    if (st.isFile()) {
      if (st.mtimeMs > newest) newest = st.mtimeMs;
      return;
    }
    if (!st.isDirectory()) return;
    for (const name of fs.readdirSync(entry)) {
      walk(path.join(entry, name));
    }
  }

  for (const p of watchPaths) walk(p);
  return newest;
}

function needsRebuild() {
  if (!fs.existsSync(jarPath)) return true;
  const jarMtime = fs.statSync(jarPath).mtimeMs;
  return newestMtime() > jarMtime;
}

if (fs.existsSync(jarPath) && !needsRebuild()) {
  console.log('[desktop] Backend JAR is up to date:', jarPath);
  process.exit(0);
}

console.log('[desktop] Building backend JAR (sources changed or first run)...');
const result = spawnSync(
  process.execPath,
  [path.join(__dirname, 'mvnw.js'), 'package', '-DskipTests'],
  { stdio: 'inherit', cwd: path.join(__dirname, '..') }
);

if (result.status !== 0 || !fs.existsSync(jarPath)) {
  console.error('[desktop] Failed to build backend. Close any running Bayport/Java, then try again.');
  process.exit(result.status || 1);
}

console.log('[desktop] Backend JAR ready.');
