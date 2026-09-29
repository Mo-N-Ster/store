// Disposable existing-format control only: NOT PdfDocument/PDFBox-Android proof.
// Uses the already installed pdf-lib read-only; no root dependency modification.
import { PDFDocument, StandardFonts, rgb } from 'pdf-lib';
import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const out = fileURLToPath(new URL('./out/', import.meta.url));
const prefix = 'STORE_DATA_V1:';
const maximum = 2_000_000;
const fields = ['name', 'hashtag', 'category', 'description', 'price', 'stockQuantity', 'minStockThreshold'];
function validate(value) {
  assert.ok(value && typeof value === 'object' && !Array.isArray(value));
  assert.equal(value.kind, 'stocks');
  assert.equal(value.version, 1);
  assert.ok(Array.isArray(value.products) && value.products.length <= 10_000);
  return { kind: 'stocks', version: 1, products: value.products.map((item) => {
    assert.ok(item && typeof item === 'object' && !Array.isArray(item));
    const record = {};
    for (const field of fields) record[field] = item[field];
    for (const field of fields.slice(0, 4)) {
      assert.equal(typeof record[field], 'string');
      assert.ok(record[field].length <= 64_000); // Spike input bound, not a new production rule.
    }
    assert.ok(record.name.trim() && record.category.trim());
    assert.ok(Number.isFinite(record.price) && record.price >= 0);
    assert.ok(Math.abs(record.price * 100 - Math.round(record.price * 100)) <= 1e-8);
    for (const field of fields.slice(5))
      assert.ok(Number.isSafeInteger(record[field]) && record[field] >= 0);
    return record;
  }) };
}
function canonical(value) {
  const json = JSON.stringify(validate(value)); // Explicit key ordering, record order retained.
  assert.ok(Buffer.byteLength(json, 'utf8') <= maximum);
  return json;
}
function decode(subject) {
  assert.equal(typeof subject, 'string');
  assert.ok(subject.startsWith(prefix));
  const encoded = subject.slice(prefix.length);
  assert.ok(encoded.length <= Math.ceil(maximum / 3) * 4);
  assert.match(encoded, /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/);
  const bytes = Buffer.from(encoded, 'base64');
  assert.ok(bytes.length <= maximum);
  assert.equal(bytes.toString('base64'), encoded);
  const json = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  return validate(JSON.parse(json));
}
const payload = { kind: 'stocks', version: 1, products: [
  { name: 'Café torréfié', hashtag: '#café', category: 'Épices', description: 'Récolte locale', price: 1250.25, stockQuantity: 21, minStockThreshold: 3 },
  { name: 'Piment séché', hashtag: '#piment', category: 'Aliments', description: 'Goût relevé', price: 0.50, stockQuantity: 0, minStockThreshold: 1 },
  { name: 'Thé vert', hashtag: '#thé', category: 'Boissons', description: 'Données synthétiques', price: 999.99, stockQuantity: 7, minStockThreshold: 2 },
] };

async function document(subject) {
  const pdf = await PDFDocument.create();
  pdf.setCreationDate(new Date('2026-01-01T00:00:00Z'));
  pdf.setModificationDate(new Date('2026-01-01T00:00:00Z'));
  if (subject !== undefined) pdf.setSubject(subject);
  pdf.setProducer('STORE isolated HOST fixture - no user data');
  const page = pdf.addPage([595, 842]);
  const font = await pdf.embedFont(StandardFonts.Helvetica);
  page.drawText('STORE - Catalogue synthetique', { x: 42, y: 792, size: 18, font });
  page.drawText('HOST CONTROL ONLY - not an Android-generated document', { x: 42, y: 766, size: 9, font });
  page.drawText('Article', { x: 42, y: 724, size: 11, font });
  page.drawText('Prix (FCFA)', { x: 325, y: 724, size: 11, font });
  page.drawText('Stock', { x: 468, y: 724, size: 11, font });
  payload.products.forEach((item, index) => {
    const y = 693 - index * 40;
    page.drawText(item.name, { x: 42, y, size: 12, font, color: rgb(0, 0, 0) });
    page.drawText(item.price.toFixed(2), { x: 325, y, size: 12, font });
    page.drawText(String(item.stockQuantity), { x: 468, y, size: 12, font });
  });
  page.drawText('Visible rows are presentation; only validated metadata carries structured data.',
    { x: 42, y: 90, size: 9, font });
  return pdf.save();
}
async function extract(bytes) {
  assert.ok(bytes.length <= 25_000_000);
  const pdf = await PDFDocument.load(bytes, { ignoreEncryption: false, updateMetadata: false });
  return decode(pdf.getSubject());
}

await mkdir(out, { recursive: true });
const tests = [];
async function test(name, operation) { await operation(); tests.push({ name, result: 'PASS' }); }
const subject = prefix + Buffer.from(canonical(payload), 'utf8').toString('base64');
const valid = await document(subject);
await writeFile(path.join(out, 'synthetic-catalog.pdf'), valid);
await test('persisted round-trip; three records; Unicode; decimal money', async () => {
  assert.deepEqual(await extract(await readFile(path.join(out, 'synthetic-catalog.pdf'))), payload);
});
await test('canonical property order; record order retained', async () => {
  const reordered = { products: payload.products.map(x => Object.fromEntries(Object.entries(x).reverse())), version: 1, kind: 'stocks' };
  assert.equal(canonical(reordered), canonical(payload));
  assert.notEqual(canonical({ ...payload, products: [...payload.products].reverse() }), canonical(payload));
});
await test('malformed JSON', async () => {
  await assert.rejects(extract(await document(prefix + Buffer.from('{broken').toString('base64'))));
});
await test('missing payload; readable unrelated PDF', async () => {
  await assert.rejects(extract(await document(undefined)));
});
await test('unsupported prefix version', async () => {
  await assert.rejects(extract(await document(subject.replace('V1:', 'V2:'))));
});
await test('unsupported payload version', async () => {
  await assert.rejects(extract(await document(prefix + Buffer.from(JSON.stringify({ ...payload, version: 2 })).toString('base64'))));
});
await test('corrupt base64', async () => {
  await assert.rejects(extract(await document(prefix + '%%%')));
});
await test('altered invalid product rejected before any persistence', async () => {
  const altered = structuredClone(payload); altered.products[0].price = -1;
  await assert.rejects(extract(await document(prefix + Buffer.from(JSON.stringify(altered)).toString('base64'))));
});
await test('invalid quantity; sub-cent amount; payload bound', async () => {
  for (const replacement of [{ stockQuantity: 1.5 }, { price: 1.001 }, { description: 'x'.repeat(maximum + 1) }]) {
    const invalid = structuredClone(payload); Object.assign(invalid.products[0], replacement);
    assert.throws(() => canonical(invalid));
  }
});
await test('truncated PDF rejected', async () => { await assert.rejects(extract(valid.slice(0, 32))); });
// Sanitized compatibility control uses precisely the source writer's JSON.stringify envelope,
// not canonical ordering. It deliberately imports no production module or DB.
const legacy = await document(prefix + Buffer.from(JSON.stringify({ products: payload.products, version: 1, kind: 'stocks' })).toString('base64'));
await writeFile(path.join(out, 'synthetic-2.0.1-envelope.pdf'), legacy);
await test('sanitized 2.0.1 envelope compatibility', async () => { assert.deepEqual(await extract(legacy), payload); });
await test('honest limit: valid tampering is not authenticated by V1', async () => {
  const changed = structuredClone(payload); changed.products[0].price = 100;
  assert.deepEqual(await extract(await document(prefix + Buffer.from(canonical(changed)).toString('base64'))), changed);
});
const evidence = { scope: 'NODE PDF-LIB HOST CONTRACT CONTROL ONLY', node: process.version,
  host_contract: 'PASS', 'SP-02': 'INCONCLUSIVE', tests,
  native_pdf_document_tested: false, pdfbox_android_tested: false,
  browser_or_electron_used: false, store_data_mutation: false,
  human_readability: 'REQUIRES separate PNG inspection',
  limit: 'V1 carries no authenticated integrity proof; valid semantic tampering cannot be detected' };
await writeFile(path.join(out, 'results.json'), JSON.stringify(evidence, null, 2));
console.log(JSON.stringify(evidence, null, 2));
