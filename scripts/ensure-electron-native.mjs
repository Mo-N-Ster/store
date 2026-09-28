import { spawnSync } from 'node:child_process';
import path from 'node:path';

import electron from 'electron';

const projectRoot = process.cwd();
const checkScript = path.join(projectRoot, 'scripts', 'check-sqlite-native.mjs');
const rebuildCli = path.join(
  projectRoot,
  'node_modules',
  '@electron',
  'rebuild',
  'lib',
  'cli.js',
);

const checkEnvironment = {
  ...process.env,
  ELECTRON_RUN_AS_NODE: '1',
};
const check = spawnSync(electron, [checkScript], {
  env: checkEnvironment,
  stdio: 'ignore',
});

if (check.status === 0) {
  console.log('Dépendances natives Electron déjà compatibles.');
  process.exit(0);
}

console.log('Recompilation des dépendances natives pour Electron…');
const rebuildEnvironment = { ...process.env };
delete rebuildEnvironment.ELECTRON_RUN_AS_NODE;

const rebuild = spawnSync(
  process.execPath,
  [rebuildCli, '--force', '--which-module', 'better-sqlite3'],
  {
    cwd: projectRoot,
    env: rebuildEnvironment,
    stdio: 'inherit',
  },
);

if (rebuild.error) {
  throw rebuild.error;
}

if (rebuild.status !== 0) {
  process.exit(rebuild.status ?? 1);
}

const verification = spawnSync(electron, [checkScript], {
  env: checkEnvironment,
  stdio: 'inherit',
});

if (verification.status !== 0) {
  console.error('La dépendance SQLite reste incompatible avec Electron.');
  process.exit(verification.status ?? 1);
}

console.log('Dépendances natives Electron vérifiées.');
