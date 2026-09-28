import type { ReportFilters } from '../../../services/reportService';

export type ReportTab =
  'summary' | 'sales' | 'products' | 'stock' | 'purchases' | 'team' | 'finance';

export function localIsoDate(date = new Date()) {
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 10);
}

export function defaultReportFilters(date = new Date()): ReportFilters {
  return {
    from: localIsoDate(new Date(date.getFullYear(), date.getMonth(), 1)),
    to: localIsoDate(date),
    grain: 'day',
  };
}

export function periodFilters(days: number, date = new Date()): ReportFilters {
  const start = new Date(date.getFullYear(), date.getMonth(), date.getDate() - days + 1);
  return { from: localIsoDate(start), to: localIsoDate(date), grain: 'day' };
}

export function contextualReportFilters(tab: ReportTab, filters: ReportFilters): ReportFilters {
  const period = { from: filters.from, to: filters.to, grain: filters.grain };
  if (tab === 'sales' || tab === 'products' || tab === 'stock')
    return { ...period, productId: filters.productId, category: filters.category };
  if (tab === 'purchases')
    return { ...period, supplierId: filters.supplierId, productId: filters.productId };
  if (tab === 'team')
    return { ...period, employeeId: filters.employeeId, attendanceState: filters.attendanceState };
  return period;
}

export function inPeriod(value: string, filters: Pick<ReportFilters, 'from' | 'to'>) {
  const day = String(value || '').slice(0, 10);
  return (!filters.from || day >= filters.from) && (!filters.to || day <= filters.to);
}

export function revenueTrend(current: number, previous: number | null | undefined) {
  return previous && previous > 0 ? ((current - previous) / previous) * 100 : null;
}

export function purchaseSummary(rows: any[], filters: ReportFilters) {
  const periodRows = rows.filter(
    (row) =>
      inPeriod(row.effective_date || row.created_at, filters) &&
      (!filters.supplierId || Number(row.supplier_id) === filters.supplierId),
  );
  const total = (status: string) =>
    periodRows
      .filter((row) => row.status === status)
      .reduce((sum, row) => sum + Number(row.total_amount || 0), 0);
  return {
    rows: periodRows,
    validated: periodRows.filter((row) => row.status === 'VALIDATED'),
    draftCount: periodRows.filter((row) => row.status === 'DRAFT').length,
    cancelledCount: periodRows.filter((row) => row.status === 'CANCELLED').length,
    validatedTotal: total('VALIDATED'),
  };
}

export function teamSummary(rows: any[]) {
  const completed = rows.filter((row) => row.endTime);
  return {
    sessions: rows.length,
    completedSessions: completed.length,
    interruptedSessions: rows.filter((row) => row.status === 'INTERRUPTED').length,
    hours: completed.reduce((sum, row) => sum + Number(row.hours || 0), 0),
  };
}

export function aggregateRanking(
  rows: any[],
  key: 'productId' | 'category',
  label: 'product' | 'category',
) {
  const values = new Map<string | number, { label: string; quantity: number; revenue: number }>();
  for (const row of rows) {
    const id = row[key] ?? row[label];
    const current = values.get(id) || { label: String(row[label] || ''), quantity: 0, revenue: 0 };
    current.quantity += Number(row.quantity || 0);
    current.revenue += Number(row.revenue || 0);
    values.set(id, current);
  }
  return [...values.values()].sort((a, b) => b.revenue - a.revenue);
}
