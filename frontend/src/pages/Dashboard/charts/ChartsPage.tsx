import { useEffect, useState } from 'react';
import { dashboardService } from '../../../services/dashboardService';
import { formatMoney } from '../../../utils/formatters';
export function ChartsPage() {
  const [data, setData] = useState<any>({});
  useEffect(() => {
    dashboardService.get().then(setData);
  }, []);
  const max = Math.max(1, ...(data.salesChart || []).map((item: any) => item.value));
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">Analyses</span>
          <h1>Graphiques</h1>
        </div>
      </div>
      <section className="chart-card">
        <h3>Ventes — 30 jours</h3>
        <div className="bars">
          {(data.salesChart || []).map((item: any) => (
            <div
              key={item.label}
              title={`${item.label}: ${formatMoney(item.value)}`}
              style={{ height: `${Math.max(4, (item.value / max) * 100)}%` }}
            >
              <span>{item.label.slice(5)}</span>
            </div>
          ))}
        </div>
      </section>
      <section className="chart-card">
        <h3>Heures travaillées</h3>
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
