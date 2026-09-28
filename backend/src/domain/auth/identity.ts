/** Identity matching only: passwords and accent distinctions are preserved. */
export function foldIdentity(value: unknown) {
  return typeof value === 'string' ? value.normalize('NFC').trim().replace(/\s+/g, ' ').toLowerCase() : '';
}
