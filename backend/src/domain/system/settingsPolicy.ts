export const editableSettings = ['storeName', 'address', 'phone', 'email', 'currency', 'discountsEnabled', 'smtpHost', 'smtpPort', 'smtpUser', 'smtpPassword', 'smtpFrom', 'smtpSecure'] as const;
const currencies = ['EUR', 'XOF', 'XAF', 'CAD', 'GBP', 'CHF', 'NGN', 'GHS'];
export function validateSettings(values: unknown): asserts values is Record<string, string> {
  if (!values || typeof values !== 'object' || Array.isArray(values)) throw new Error('VALIDATION_ERROR');
  for (const [key, value] of Object.entries(values)) {
    if (!(editableSettings as readonly string[]).includes(key) || typeof value !== 'string' || value.length > (key === 'smtpPassword' ? 4096 : 1000)) throw new Error('VALIDATION_ERROR');
    if (key !== 'smtpPassword' && /[\r\n\0]/.test(value)) throw new Error('VALIDATION_ERROR');
    if (key === 'storeName' && !value.trim()) throw new Error('VALIDATION_ERROR');
    if (key === 'currency' && !currencies.includes(value)) throw new Error('VALIDATION_ERROR');
    if (['discountsEnabled', 'smtpSecure'].includes(key) && !['true', 'false'].includes(value)) throw new Error('VALIDATION_ERROR');
    if (['email', 'smtpFrom'].includes(key) && value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) throw new Error('VALIDATION_ERROR');
    if (key === 'smtpHost' && value && !/^[A-Za-z0-9.-]+$/.test(value)) throw new Error('VALIDATION_ERROR');
    if (key === 'smtpPort' && (!/^\d+$/.test(value) || Number(value) < 1 || Number(value) > 65535)) throw new Error('VALIDATION_ERROR');
  }
}
