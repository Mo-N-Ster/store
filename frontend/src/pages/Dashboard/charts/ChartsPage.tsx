import { useEffect, useState } from 'react';
import { dashboardService } from '../../../services/dashboardService';
import { formatMoney } from '../../../utils/formatters';
import { useTranslation } from 'react-i18next';
import { useStorePreferences } from '../../../hooks/useStorePreferences';
export function ChartsPage() {
  const { t } = useTranslation();
  const { currency } = useStorePreferences();
  const [data, setData] = useState<any>({});
  useEffect(() => {
    dashboardService.get().then(setData);
  }, []);
  const max = Math.max(1, ...(data.salesChart || []).map((item: any) => item.value));
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">{t('analytics')}</span>
          <h1>{t('charts')}</h1>
        </div>
      </div>
      <section className="chart-card">
        <h3>{t('salesLast30Days')}</h3>
        <div className="bars">
          {(data.salesChart || []).map((item: any) => (
            <div
              key={item.label}
              title={`${item.label}: ${formatMoney(item.value, currency)}`}
              style={{ height: `${Math.max(4, (item.value / max) * 100)}%` }}
            >
              <span>{item.label.slice(5)}</span>
            </div>
          ))}
        </div>
      </section>
      <section className="chart-card">
        <h3>{t('hoursWorked')}</h3>
        {(data.attendanceChart || []).map((item: any) => (
          <p key={item.label}>
            {item.label}
            <progress
              value={item.value}
              max={Math.max(8, ...data.attendanceChart.map((row: any) => row.value))}
            />
            {item.value} h
          </p>
        ))}
      </section>
    </>
  );
}
