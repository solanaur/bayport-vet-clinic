/**
 * Find a Java 17+ executable on Windows and macOS.
 * Candidates are checked in order. The first path that exists wins.
 */
const path = require('path');
const fs = require('fs');
const { execFileSync } = require('child_process');

function javaBinaryName(platform) {
  return platform === 'win32' ? 'java.exe' : 'java';
}

function pathFor(platform) {
  return platform === 'win32' ? path.win32 : path.posix;
}

function javaExecutable(home, platform) {
  if (!home) return '';
  return pathFor(platform).join(home, 'bin', javaBinaryName(platform));
}

function homesFromDirectory(root, platform) {
  if (!root || !fs.existsSync(root)) return [];
  let names;
  try {
    names = fs.readdirSync(root, { withFileTypes: true });
  } catch {
    return [];
  }
  const homes = [];
  for (const entry of names) {
    if (!entry.isDirectory()) continue;
    const home = path.join(root, entry.name);
    const nested = path.join(home, 'Contents', 'Home');
    if (fs.existsSync(javaExecutable(nested, platform))) {
      homes.push(nested);
      continue;
    }
    if (fs.existsSync(javaExecutable(home, platform))) {
      homes.push(home);
    }
  }
  homes.sort((a, b) => {
    const score = (value) => (/17|21/.test(value) ? 0 : 1);
    return score(a) - score(b);
  });
  return homes;
}

function darwinJavaHome() {
  try {
    const home = execFileSync('/usr/libexec/java_home', ['-v', '17'], { encoding: 'utf8' }).trim();
    return home || '';
  } catch {
    return '';
  }
}

function candidateHomes(platform, env) {
  const homes = [];
  if (env && env.JAVA_HOME) homes.push(env.JAVA_HOME);
  if (platform === 'darwin') {
    const reported = darwinJavaHome();
    if (reported) homes.push(reported);
    homes.push('/opt/homebrew/opt/openjdk@17');
    homes.push('/usr/local/opt/openjdk@17');
    homes.push('/opt/homebrew/opt/openjdk@21');
    homes.push('/usr/local/opt/openjdk@21');
    homes.push(...homesFromDirectory('/Library/Java/JavaVirtualMachines', platform));
  }
  if (platform === 'win32') {
    const programFiles = [env && env.ProgramFiles, env && env['ProgramFiles(x86)']].filter(Boolean);
    const folders = ['Java', 'Eclipse Adoptium', 'Microsoft', 'Amazon Corretto', 'Zulu'];
    for (const root of programFiles) {
      for (const folder of folders) {
        homes.push(...homesFromDirectory(path.join(root, folder), platform));
      }
    }
  }
  return homes;
}

function firstExisting(paths, exists) {
  const seen = new Set();
  for (const candidate of paths) {
    if (!candidate || seen.has(candidate)) continue;
    seen.add(candidate);
    if (exists(candidate)) return candidate;
  }
  return '';
}

function resolveJavaExecutable(options) {
  const platform = options.platform;
  const exists = options.exists || fs.existsSync;
  const binary = javaBinaryName(platform);
  const homes = candidateHomes(platform, options.env || process.env);
  const fromTools = (options.toolHomes || []).map((home) => javaExecutable(home, platform));
  const embedded = options.embeddedJava ? [options.embeddedJava] : [];
  const preferred = options.preferEmbedded
    ? [...embedded, ...homes.map((home) => javaExecutable(home, platform)), ...fromTools]
    : [...homes.map((home) => javaExecutable(home, platform)), ...fromTools, ...embedded];
  const found = firstExisting(preferred, exists);
  return found || binary;
}

module.exports = {
  javaBinaryName,
  javaExecutable,
  candidateHomes,
  firstExisting,
  resolveJavaExecutable,
};
