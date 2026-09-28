export const normalizeDecimal = (value: string) => value.replace(/,/g, '.');
export function validDecimal(value: string, min?: string | number, max?: string | number, step: string | number = 'any') {
  if (!value) return true; // required is handled by the input itself
  if (!/^-?(?:\d+(?:\.\d*)?|\.\d+)$/.test(value)) return false;
  const number = Number(value);
  if (!Number.isFinite(number) || (min !== undefined && number < Number(min)) || (max !== undefined && number > Number(max))) return false;
  if (step !== 'any' && Number(step) > 0) {
    const units = (number - Number(min ?? 0)) / Number(step);
    if (Math.abs(units - Math.round(units)) > 1e-7) return false;
  }
  return true;
}
