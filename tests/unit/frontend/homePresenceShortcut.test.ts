import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { PresentEmployees } from '../../../frontend/src/pages/Dashboard/widgets/PresentEmployees';
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));
vi.mock('../../../frontend/src/services/api', () => ({ storeApi: {} }));
describe('Home attendance shortcut', () => {
  it('keeps the daily-sheet button available independently of present-list loading', () => {
    const html = renderToStaticMarkup(createElement(PresentEmployees, { onOpen: () => {} }));
    expect(html).toContain('dashboard-presence-action');
    expect(html).toContain('dailyAttendanceSheet');
    expect(html).toContain('type="button"');
  });
});
