import fs from 'node:fs';
import path from 'node:path';

const manifest = JSON.parse(fs.readFileSync(path.resolve('package.json'), 'utf8'));
const releaseDirectory = path.resolve(process.argv[2] || 'release');
const unpackedDirectory = path.join(releaseDirectory, 'win-unpacked');

const nativeModule = path.resolve(
  unpackedDirectory,
  'resources',
  'app.asar.unpacked',
  'node_modules',
  'better-sqlite3',
  'build',
  'Release',
  'better_sqlite3.node',
);

if (!fs.existsSync(nativeModule)) {
  throw new Error(`Paquet invalide : module SQLite natif absent (${nativeModule})`);
}

const { size } = fs.statSync(nativeModule);
if (size < 100_000) {
  throw new Error(`Paquet invalide : module SQLite natif incomplet (${size} octets)`);
}

const requiredFiles = [
  path.join(unpackedDirectory, 'STORE.exe'),
  path.join(unpackedDirectory, 'resources', 'app.asar'),
  path.resolve('dist', 'frontend', 'store-logo.png'),
  path.resolve('dist', 'frontend', 'index.html'),
];

for (const file of requiredFiles) {
  if (!fs.existsSync(file) || fs.statSync(file).size === 0) {
    throw new Error(`Paquet invalide : fichier absent ou vide (${file})`);
  }
}

const installerPrefix = `STORE Setup ${manifest.version}-`;
const installer = fs.readdirSync(releaseDirectory).find(
  (name) => name.startsWith(installerPrefix) && name.endsWith('.exe'),
);

if (!installer) {
  throw new Error(`Paquet invalide : installateur ${installerPrefix}<architecture>.exe absent`);
}

const installerPath = path.join(releaseDirectory, installer);
if (fs.statSync(installerPath).size < 1_000_000) {
  throw new Error(`Paquet invalide : installateur incomplet (${installerPath})`);
}
