/**
 * Run with the desktop Electron binary:
 *   electron.exe java-lookup-check.js
 */
'use strict';

const assert = require('assert');
const { firstExisting, javaExecutable, resolveJavaExecutable } = require('./java-lookup');

const present = new Set([
  'C:\\Program Files\\Java\\jdk-17\\bin\\java.exe',
  '/opt/homebrew/opt/openjdk@17/bin/java',
]);

function exists(file) {
  return present.has(file);
}

assert.strictEqual(
  firstExisting(['missing', 'C:\\Program Files\\Java\\jdk-17\\bin\\java.exe'], exists),
  'C:\\Program Files\\Java\\jdk-17\\bin\\java.exe'
);

assert.strictEqual(
  resolveJavaExecutable({
    platform: 'win32',
    env: { JAVA_HOME: 'C:\\Program Files\\Java\\jdk-17' },
    exists,
    preferEmbedded: false,
  }),
  'C:\\Program Files\\Java\\jdk-17\\bin\\java.exe'
);

assert.strictEqual(
  resolveJavaExecutable({
    platform: 'darwin',
    env: { JAVA_HOME: '/opt/homebrew/opt/openjdk@17' },
    exists,
    preferEmbedded: false,
  }),
  '/opt/homebrew/opt/openjdk@17/bin/java'
);

assert.strictEqual(
  resolveJavaExecutable({
    platform: 'darwin',
    env: {},
    exists: () => false,
    preferEmbedded: false,
  }),
  'java'
);

assert.strictEqual(javaExecutable('/Library/Java/jdk-17', 'darwin'), '/Library/Java/jdk-17/bin/java');
assert.strictEqual(javaExecutable('C:\\Java\\jdk-17', 'win32'), 'C:\\Java\\jdk-17\\bin\\java.exe');

console.log('Java lookup checks passed');
process.exit(0);
