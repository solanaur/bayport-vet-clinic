const { spawnSync } = require('child_process');
const path = require('path');

const args = process.argv.slice(2);
const backendDir = path.resolve(__dirname, '..', '..', 'bayport-backend');
const isWin = process.platform === 'win32';
const executable = isWin ? 'mvnw.cmd' : './mvnw';

const result = spawnSync(executable, args, {
  cwd: backendDir,
  stdio: 'inherit',
  shell: isWin
});

if (result.error) {
  console.error(result.error.message);
  process.exit(1);
}

process.exit(result.status ?? 0);

