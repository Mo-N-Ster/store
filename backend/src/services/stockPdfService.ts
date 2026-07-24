import { PDFDocument } from 'pdf-lib';

const PREFIX = 'STORE_DATA_V1:';
const MAX_PAYLOAD_BYTES = 2_000_000;

export async function embedStoreData(pdf: Uint8Array, data: unknown) {
  const payload = JSON.stringify(data);
  if (Buffer.byteLength(payload, 'utf8') > MAX_PAYLOAD_BYTES) throw new Error('PDF_DATA_TOO_LARGE');
  const document = await PDFDocument.load(pdf);
  document.setSubject(`${PREFIX}${Buffer.from(payload, 'utf8').toString('base64')}`);
  document.setProducer('STORE');
  return Buffer.from(await document.save());
}

export async function readStoreData(pdf: Uint8Array) {
  const document = await PDFDocument.load(pdf, {
    ignoreEncryption: false,
    updateMetadata: false,
  });
  const subject = document.getSubject() || '';
  if (!subject.startsWith(PREFIX)) throw new Error('INVALID_STORE_PDF');
  try {
    return JSON.parse(Buffer.from(subject.slice(PREFIX.length), 'base64').toString('utf8'));
  } catch {
    throw new Error('INVALID_STORE_PDF');
  }
}
