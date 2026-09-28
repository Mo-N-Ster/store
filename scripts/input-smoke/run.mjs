import { app, BrowserWindow } from 'electron';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';

const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'store-input-smoke-'));
app.setPath('userData', profile);
app.whenReady().then(async () => {
const win = new BrowserWindow({ show: false, webPreferences: { contextIsolation: true, nodeIntegration: false, backgroundThrottling: false } });
const js = (code) => win.webContents.executeJavaScript(code);
const pause = () => new Promise((resolve) => setTimeout(resolve, 75));
async function waitFor(expression) {
  for (let i = 0; i < 80; i++) { if (await js(`Boolean(${expression})`)) return; await pause(); }
  throw new Error(`Timeout: ${expression}`);
}
async function type(text) { for (const character of text) { win.webContents.sendInputEvent({ type: 'char', keyCode: character }); await pause(); } }
try {
  await win.loadFile(path.resolve('artifacts/input-smoke/index.html'));
  console.log('Fixture loaded');
  await waitFor('document.querySelector(".attendance-sheet__row button")');
  await js('document.querySelector(".attendance-sheet__row button").click()');
  await waitFor('document.activeElement?.type === "password"');
  await type('wrong-password');
  assert.equal(await js('document.querySelector("input[type=password]").value'), 'wrong-password');
  await js('document.querySelector(".password-visibility").click()');
  assert.equal(await js('document.querySelector(".password-input input").type'), 'text');
  await js('document.querySelector(".ops-form").requestSubmit()');
  await waitFor('window.smokeAttempts === 1 && document.querySelector("[role=alert]")');
  await js('document.querySelector(".password-input input").focus()');
  await type('Test-only-123');
  assert.equal(await js('document.querySelector(".password-input input").value'), 'Test-only-123');
  await js('document.querySelector(".ops-form").requestSubmit()');
  await waitFor('window.smokeAttempts === 2 && !document.querySelector("[role=dialog]")');
  for (const [id, value] of [['test-text', 'Continuous typing'], ['test-number', '1234'], ['test-memo', 'Several characters']]) {
    await js(`document.getElementById('${id}').focus()`);
    await type(value);
    assert.equal(await js(`document.getElementById('${id}').value`), value);
  }
  await js('document.getElementById("test-number").focus()');
  win.webContents.sendInputEvent({ type: 'keyDown', keyCode: 'Up' });
  win.webContents.sendInputEvent({ type: 'keyUp', keyCode: 'Up' });
  await pause();
  assert.equal(await js('document.getElementById("test-number").value'), '1234');
  await js('document.getElementById("test-check").click()');
  assert.equal(await js('document.getElementById("test-check").checked'), true);
  await js('const select = document.getElementById("test-select"); select.value="b"; select.dispatchEvent(new Event("change", {bubbles:true}))');
  assert.equal(await js('document.getElementById("test-select").value'), 'b');
  assert.equal(await js('document.getElementById("test-disabled").disabled'), true);
  console.log('PASS: shared text, number, textarea, select, checkbox and disabled fields; numeric stepping blocked.');
  await js('document.getElementById("test-decimal").focus(); document.getElementById("test-decimal").select()');
  await type('12,34');
  assert.equal(await js('document.getElementById("test-decimal").value'), '12.34');
  assert.equal(await js('document.getElementById("test-decimal").checkValidity()'), true);
  await type('5');
  assert.equal(await js('document.getElementById("test-decimal").checkValidity()'), false);
  await js('document.getElementById("switch-open").click()');
  await waitFor('document.querySelector("[aria-labelledby=switch-user-title]")');
  await js('document.querySelector("[autocomplete=username]").focus()'); await type('other');
  await js('document.querySelector("[autocomplete=current-password]").focus()'); await type('wrong');
  await js('const f=document.querySelector("[aria-labelledby=switch-user-title]"); f.requestSubmit(); f.requestSubmit();');
  await waitFor('window.smokeSwitchAttempts === 1 && document.querySelector("[aria-labelledby=switch-user-title] [role=alert]")');
  await waitFor('document.activeElement?.getAttribute("autocomplete") === "current-password"');
  await type('Switch-only-123');
  await js('document.querySelector("[aria-labelledby=switch-user-title]").requestSubmit()');
  await waitFor('window.smokeSwitchAttempts === 2 && !document.querySelector("[aria-labelledby=switch-user-title]")');
  console.log('PASS: comma decimals, precision validation, user-switch double-submit protection and retry.');
  console.log('PASS: actual PresencePage continuous typing, visibility, rejected credential, retry and successful close (isolated mocked attendance API).');
  win.destroy();
  app.exit(0);
} catch (error) { console.error(error); win.destroy(); app.exit(1); }
});
