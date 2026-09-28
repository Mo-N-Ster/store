import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import '../../frontend/src/i18n/i18n';
import '../../frontend/src/design-system/tokens.css';
import '../../frontend/src/design-system/foundations.css';
import '../../frontend/src/styles/enhancements.css';
import { TextInput, NumberInput, Select, Checkbox } from '../../frontend/src/design-system';
import { installNumericInputGuards } from '../../frontend/src/utils/numericInput';

installNumericInputGuards(document);
function SharedFields() {
  const [text, setText] = useState('');
  const [number, setNumber] = useState('');
  const [memo, setMemo] = useState('');
  const [selected, setSelected] = useState('a');
  const [checked, setChecked] = useState(false);
  return <section id="shared-fields"><TextInput id="test-text" label="Text" value={text} onChange={(event) => setText(event.target.value)} /><NumberInput id="test-number" label="Number" value={number} onChange={(event) => setNumber(event.target.value)} /><textarea id="test-memo" aria-label="Memo" value={memo} onChange={(event) => setMemo(event.target.value)} /><Select id="test-select" label="Select" value={selected} onChange={(event) => setSelected(event.target.value)}><option value="a">A</option><option value="b">B</option></Select><Checkbox id="test-check" label="Check" checked={checked} onChange={(event) => setChecked(event.target.checked)} /><TextInput id="test-disabled" label="Disabled" disabled value="protected" /></section>;
}

let attempts = 0;
let switchAttempts = 0;
let present = false;
const testWindow = window as unknown as { store: unknown; smokeAttempts: number; smokeSwitchAttempts: number };
testWindow.store = {
  switchUser: async (input: { password: string }) => {
    testWindow.smokeSwitchAttempts = ++switchAttempts;
    await new Promise((resolve) => setTimeout(resolve, 250));
    if (input.password !== 'Switch-only-123') throw new Error('INVALID_CREDENTIALS');
    return { id: 2, role: 'employee', username: 'other' };
  },
  attendanceSheet: async () => [{ id: 1, username: 'test', firstName: 'emp01', lastName: '01', role: 'employee', startTime: null, endTime: null, durationMinutes: 0, state: present ? 'PRESENT' : 'ABSENT' }],
  attendanceHistory: async () => [],
  clockAttendance: async (input: { password: string }) => {
    attempts++;
    testWindow.smokeAttempts = attempts;
    if (input.password !== 'Test-only-123') throw new Error('INVALID_CREDENTIALS');
    present = true;
  },
};
const { PresencePage } = await import('../../frontend/src/pages/Dashboard/employees/PresencePage');
const { SwitchUserDialog } = await import('../../frontend/src/components/UI/SwitchUserDialog');
function SwitchAndDecimal() {
  const [open, setOpen] = useState(false);
  const [decimal, setDecimal] = useState(0);
  return <><button id="switch-open" onClick={() => setOpen(true)}>Switch</button>{open && <SwitchUserDialog onClose={() => setOpen(false)} onSuccess={() => setOpen(false)} />}<NumberInput id="test-decimal" label="Decimal" min="0" step="0.01" value={decimal} onChange={(event) => setDecimal(Number(event.target.value))} /></>;
}
createRoot(document.getElementById('root')!).render(<><PresencePage notify={() => {}} permissions={[{ module: 'PRESENCE', actions: ['READ', 'UPDATE'] }]} /><SharedFields /><SwitchAndDecimal /></>);
