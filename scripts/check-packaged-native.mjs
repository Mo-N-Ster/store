import { spawnSync } from 'node:child_process';
import path from 'node:path';
import electron from 'electron';
const binding = path.resolve(process.argv[2] || 'artifacts/release-candidate', 'win-unpacked/resources/app.asar/node_modules/better-sqlite3');
const code = `const Database=require(${JSON.stringify(binding)}); const db=new Database(':memory:'); if(db.pragma('integrity_check',{simple:true})!=='ok') throw Error('SQLITE_INTEGRITY'); db.close(); console.log('Packaged SQLite loads under Electron ABI '+process.versions.modules);`;
const result = spawnSync(electron, ['-e', code], { env: { ...process.env, ELECTRON_RUN_AS_NODE: '1' }, stdio: 'inherit', windowsHide: true });
if (result.error) throw result.error;
process.exit(result.status ?? 1);
