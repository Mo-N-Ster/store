import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { ReportBars, ReportData } from '../../../frontend/src/pages/Dashboard/charts/ReportData';
import { TeamPage } from '../../../frontend/src/pages/Dashboard/employees/TeamPage';

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));
vi.mock('../../../frontend/src/services/api', () => ({ storeApi: {} }));

describe('Report disclosure and presence deep link', () => {
  it('starts collapsed with an accessible button bound to its own table', () => {
    const html = renderToStaticMarkup(createElement(ReportData, { children: createElement('table', null, createElement('tbody')) }));
    expect(html).toContain('aria-expanded="false"');
    expect(html).toContain('type="button"');
    expect(html).toContain('report-data__content');
    const id = html.match(/aria-controls="([^"]+)"/)?.[1];
    expect(id).toBeTruthy();
    expect(html).toContain(`id="${id}"`);
    expect(html).toContain('<table>');
  });
  it('uses a true zero baseline and retains exact category values', () => {
    const html = renderToStaticMarkup(createElement(ReportBars, { label: 'Sessions', rows: [{ label: 'Open', value: 0 }, { label: 'Closed', value: 8 }, { label: 'Corrected', value: 4 }] }));
    expect(html).toContain('width:0%');
    expect(html).toContain('width:100%');
    expect(html).toContain('width:50%');
    expect(html).toContain('<strong>8</strong>');
    expect(html).toContain('report-distribution__track');
    expect(html).not.toContain('class="report-bars"');
  });
  it('opens the presence tab without exposing member management to a presence-only user', () => {
    const html = renderToStaticMarkup(createElement(TeamPage, { notify: () => {}, permissions: [{ module: 'PRESENCE', actions: ['READ'] }], initialEmployeeId: 7 }));
    expect(html).toContain('presenceAndTime');
    expect(html).not.toContain('newEmployee');
    expect(html).toContain('aria-selected="true"');
  });
});
