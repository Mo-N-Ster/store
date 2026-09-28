import { storeApi } from './api';

export interface ReportFilters {
  from: string;
  to: string;
  grain: 'day' | 'week' | 'month';
  productId?: number;
  category?: string;
  supplierId?: number;
  employeeId?: number;
  attendanceState?: string;
}

export const reportService = {
  get: (filters: ReportFilters) => storeApi.reports(filters),
  exportPdf: (name: string, data?: unknown) => storeApi.exportReportPdf({ name, data }),
  emailPdf: (input: { to: string; subject: string; text: string; filename: string }) =>
    storeApi.emailReportPdf(input),
};
