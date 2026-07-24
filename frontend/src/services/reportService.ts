import { storeApi } from './api';

export interface ReportFilters {
  from: string;
  to: string;
  grain: 'day' | 'week' | 'month';
  productId?: number;
  category?: string;
}

export const reportService = {
  get: (filters: ReportFilters) => storeApi.reports(filters),
  exportPdf: (name: string) => storeApi.exportReportPdf({ name }),
  emailPdf: (input: { to: string; subject: string; text: string; filename: string }) =>
    storeApi.emailReportPdf(input),
};
