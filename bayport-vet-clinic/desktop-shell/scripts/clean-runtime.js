const fs = require('fs');
const path = require('path');

const runtimeDir = path.join(__dirname, '..', 'runtime');

if (fs.existsSync(runtimeDir)) {
  console.log('Removing existing runtime directory...');
  fs.rmSync(runtimeDir, { recursive: true, force: true });
  console.log('Runtime directory removed.');
} else {
  console.log('Runtime directory does not exist, proceeding...');
}

