import fs from 'node:fs';
import { describe, expect, it } from 'vitest';
import {
  aggregateRanking,
  contextualReportFilters,
  defaultReportFilters,
  inPeriod,
  localIsoDate,
  periodFilters,
  purchaseSummary,
  revenueTrend,
  teamSummary,
} from '../../../frontend/src/pages/Dashboard/charts/reportingModel';
import { destinationIsAvailable } from '../../../frontend/src/navigation/navigation';

const read = (path: string) => fs.readFileSync(path, 'utf8');

describe('STORE 3.0 Phase F reporting', () => {
  it('uses local calendar boundaries for today and month-to-date defaults', () => {
    const date = new Date(2026, 8, 24, 23, 30);
    expect(localIsoDate(date)).toBe('2026-09-24');
    expect(defaultReportFilters(date)).toEqual({
      from: '2026-09-01',
      to: '2026-09-24',
      grain: 'day',
    });
    expect(periodFilters(7, date).from).toBe('2026-09-18');
  });

  it('keeps inclusive period filtering deterministic', () => {
    const filters = { from: '2026-09-01', to: '2026-09-30' };
    expect(inPeriod('2026-09-01 00:00:00', filters)).toBe(true);
    expect(inPeriod('2026-09-30T23:59:00', filters)).toBe(true);
    expect(inPeriod('2026-10-01', filters)).toBe(false);
  });

  it('isolates contextual filters between report domains', () => {
    const filters = {
      from: '2026-09-01',
      to: '2026-09-30',
      grain: 'day' as const,
      productId: 4,
      category: 'Épices',
      supplierId: 3,
      employeeId: 8,
      attendanceState: 'OPEN',
    };
    expect(contextualReportFilters('summary', filters)).toEqual({
      from: filters.from,
      to: filters.to,
      grain: 'day',
    });
    expect(contextualReportFilters('sales', filters)).toEqual({
      from: filters.from,
      to: filters.to,
      grain: 'day',
      productId: 4,
      category: 'Épices',
    });
    expect(contextualReportFilters('purchases', filters)).toEqual({
      from: filters.from,
      to: filters.to,
      grain: 'day',
      supplierId: 3,
      productId: 4,
    });
    expect(contextualReportFilters('team', filters)).toEqual({
      from: filters.from,
      to: filters.to,
      grain: 'day',
      employeeId: 8,
      attendanceState: 'OPEN',
    });
  });

  it('does not invent a percentage when the comparison base is empty', () => {
    expect(revenueTrend(100, 0)).toBeNull();
    expect(revenueTrend(100, null)).toBeNull();
    expect(revenueTrend(120, 100)).toBe(20);
  });

  it('excludes draft and cancelled purchases from validated totals', () => {
    const filters = { from: '2026-09-01', to: '2026-09-30', grain: 'day' as const };
    const result = purchaseSummary(
      [
        { id: 1, status: 'VALIDATED', total_amount: 100, created_at: '2026-09-02' },
        { id: 2, status: 'DRAFT', total_amount: 90, created_at: '2026-09-03' },
        { id: 3, status: 'CANCELLED', total_amount: 80, created_at: '2026-09-04' },
        { id: 4, status: 'VALIDATED', total_amount: 70, created_at: '2026-08-31' },
      ],
      filters,
    );
    expect(result.validatedTotal).toBe(100);
    expect(result.validated).toHaveLength(1);
    expect(result.draftCount).toBe(1);
    expect(result.cancelledCount).toBe(1);
  });

  it('excludes open attendance from worked-hour totals', () => {
    const result = teamSummary([
      { endTime: '2026-09-01', hours: 8, status: 'VALID' },
      { endTime: null, hours: 40, status: 'VALID' },
      { endTime: '2026-09-02', hours: 4, status: 'INTERRUPTED' },
    ]);
    expect(result.hours).toBe(12);
    expect(result.completedSessions).toBe(2);
    expect(result.interruptedSessions).toBe(1);
  });

  it('aggregates product ranking by validated backend revenue without changing precision', () => {
    expect(
      aggregateRanking(
        [
          { productId: 1, product: 'A', quantity: 2, revenue: 9.25 },
          { productId: 1, product: 'A', quantity: 1, revenue: 4.75 },
          { productId: 2, product: 'B', quantity: 5, revenue: 10 },
        ],
        'productId',
        'product',
      ),
    ).toEqual([
      { label: 'A', quantity: 3, revenue: 14 },
      { label: 'B', quantity: 5, revenue: 10 },
    ]);
  });

  it('uses effective FINANCES permission for report navigation', () => {
    expect(destinationIsAvailable([{ module: 'FINANCES', actions: ['READ'] }], 'reports')).toBe(
      true,
    );
    expect(destinationIsAvailable([{ module: 'DASHBOARD', actions: ['READ'] }], 'reports')).toBe(
      false,
    );
  });

  it('implements all supported report domains and permission-gates purchase/team', () => {
    const source = read('frontend/src/pages/Dashboard/charts/ChartsPage.tsx');
    for (const id of ['summary', 'sales', 'products', 'stock', 'purchases', 'team', 'finance'])
      expect(source).toContain(`id: '${id}'`);
    expect(source).toContain("can(permissions, 'PURCHASES', 'READ')");
    expect(source).toContain("can(permissions, 'PRESENCE', 'READ')");
  });

  it('clears stale report data while loading and distinguishes forbidden errors', () => {
    const source = read('frontend/src/pages/Dashboard/charts/ChartsPage.tsx');
    expect(source).toMatch(/setLoading\(true\);\s*setData\(null\);\s*setError\(null\)/);
    expect(source).toMatch(/'forbidden'\s*:\s*'technical'/);
    expect(source).toContain("error === 'forbidden'");
  });

  it('states cancellation and financial limitations in the visible interface', () => {
    const source = read('frontend/src/pages/Dashboard/charts/ChartsPage.tsx');
    expect(source).toContain('cancelledInvoices');
    expect(source).toContain("t('excludedFromSalesKpis')");
    expect(source).toContain("t('financeNotAccounting')");
    expect(source).toContain("t('notProfitCalculation')");
  });

  it('uses one aggregate Dashboard call rather than one call per KPI', () => {
    const source = read('frontend/src/pages/Dashboard/widgets/Overview.tsx');
    expect(source.match(/dashboardService\.get\(\)/g)).toHaveLength(1);
    expect(source).toContain('Promise.all');
    expect(source).toContain('<KpiCard');
  });

  it('keeps chart information available as text or tables', () => {
    const reports = read('frontend/src/pages/Dashboard/charts/ChartsPage.tsx');
    const dashboard = read('frontend/src/pages/Dashboard/widgets/Overview.tsx');
    expect(reports).toContain('report-accessible-list');
    expect(reports).toContain('<ResponsiveTable');
    expect(dashboard).toContain("aria-label={t('salesTrendAccessible')}");
  });

  it('supports required responsive, contrast and reduced-motion structures', () => {
    const css = read('frontend/src/design-system/reporting.css');
    expect(css).toContain('@media (max-width: 68rem)');
    expect(css).toContain('@media (max-width: 50rem)');
    expect(css).toContain('@media (prefers-contrast: more)');
    expect(css).toContain('@media (prefers-reduced-motion: reduce)');
  });

  it('preserves the E.5 employee presence server scope', () => {
    const handlers = read('backend/src/main/ipcHandlers.ts');
    const database = read('backend/src/database/storeDatabase.ts');
    expect(handlers).toContain("name === 'attendanceStatuses' && session.role === 'employee'");
    expect(database).toContain('(? IS NULL OR u.id=?)');
  });
});
