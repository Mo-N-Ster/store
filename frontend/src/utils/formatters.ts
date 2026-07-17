export const formatMoney = (value: number, currency = 'EUR', locale?: string) =>
  new Intl.NumberFormat(locale, { style: 'currency', currency }).format(value || 0);
export const todayIso = () => new Date().toISOString().slice(0, 10);
