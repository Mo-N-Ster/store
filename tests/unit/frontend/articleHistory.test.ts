import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { articleHistorySeries } from '../../../frontend/src/pages/Dashboard/charts/ArticleHistoryChart';
import { InvoicePreview } from '../../../frontend/src/pages/Cashier/InvoicePreview';

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));
vi.mock('../../../frontend/src/utils/formatters', () => ({ formatMoney: (value: number, currency: string) => `${value} ${currency}` }));

describe('Article history and receipt', () => {
  it('aggregates each article and period without mixing money and quantity', () => {
    const rows = [
      { period: '2026-09', productId: 1, product: 'A', quantity: 2, revenue: 600 },
      { period: '2026-09', productId: 1, product: 'A', quantity: 3, revenue: 900 },
      { period: '2026-10', productId: 1, product: 'A', quantity: 1, revenue: 400 },
      { period: '2026-09', productId: 2, product: 'B', quantity: 4, revenue: 800 },
    ];
    expect(articleHistorySeries(rows, 'quantity').map((row) => row.averagePrice)).toEqual([5, 1, 4]);
    expect(articleHistorySeries(rows, 'revenue').map((row) => row.averagePrice)).toEqual([1500, 400, 800]);
    expect(articleHistorySeries([], 'quantity')).toEqual([]);
  });
  it('orders receipt actions with the new sale as primary, and includes its timestamp', () => {
    const html = renderToStaticMarkup(createElement(InvoicePreview, { data: { invoice: { id: 'TEST', invoice_date: '2026-09-26T13:14:00Z', subtotal: 0, total_amount: 0 }, lines: [] }, close: () => {}, currency: 'XAF', discountsEnabled: false, saleCompleted: true }));
    expect(html.indexOf('>print<')).toBeLessThan(html.indexOf('>newSale<'));
    expect(html.indexOf('>newSale<')).toBeLessThan(html.indexOf('>savePdf<'));
    expect(html).toMatch(/ds-button--primary[^>]*>.*?newSale/s);
    expect(html).toContain('dateAndTime');
    expect(html).not.toContain('Invalid Date');
  });
});
