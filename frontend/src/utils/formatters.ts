import i18n from '../i18n/i18n';

export const formatMoney = (value: number, currency = 'EUR', locale?: string) =>
  new Intl.NumberFormat(locale || i18n.resolvedLanguage || i18n.language, {
    style: 'currency',
    currency,
  }).format(value || 0);
export { todayIso } from './localDate';

export const formatNumber = (value: number, locale?: string, options?: Intl.NumberFormatOptions) =>
  new Intl.NumberFormat(locale || i18n.resolvedLanguage || i18n.language, options).format(value || 0);
