import { app, BrowserWindow, ipcMain, dialog } from 'electron';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { api, initDatabase } from '../database/database.js';

const __dirname=path.dirname(fileURLToPath(import.meta.url));
function createWindow(){const win=new BrowserWindow({width:1440,height:900,minWidth:1100,minHeight:700,show:false,backgroundColor:'#f5f2e9',webPreferences:{preload:path.join(__dirname,'preload.js'),contextIsolation:true,nodeIntegration:false,sandbox:false}});win.once('ready-to-show',()=>win.show());win.webContents.on('did-fail-load',(_e,code,description,url)=>console.error('Renderer load failed',{code,description,url}));if(process.env.VITE_DEV_SERVER_URL)win.loadURL(process.env.VITE_DEV_SERVER_URL);else win.loadFile(path.join(__dirname,'../../frontend/index.html'));}
app.whenReady().then(()=>{initDatabase();for(const [name,fn] of Object.entries(api))ipcMain.handle(`store:${name}`,(_event,...args)=>(fn as any)(...args));ipcMain.handle('store:saveExport',async(_e,{name,content}:{name:string;content:string})=>{const result=await dialog.showSaveDialog({defaultPath:name});if(result.canceled||!result.filePath)return null;await import('node:fs/promises').then(fs=>fs.writeFile(result.filePath!,content,'utf8'));return result.filePath;});createWindow();app.on('activate',()=>{if(BrowserWindow.getAllWindows().length===0)createWindow();});});
app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit();});
