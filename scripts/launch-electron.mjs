import { spawn } from 'node:child_process';

import electron from 'electron';

const environment = { ...process.env };
delete environment.ELECTRON_RUN_AS_NODE;

const child = spawn(electron, ['.'], {
  env: environment,
  stdio: 'inherit',
  windowsHide: false,
});

child.once('error', (error) => {
  console.error('Impossible de lancer Electron.', error);
  process.exitCode = 1;
});

child.once('exit', (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal);
    return;
  }

  process.exitCode = code ?? 1;
});
