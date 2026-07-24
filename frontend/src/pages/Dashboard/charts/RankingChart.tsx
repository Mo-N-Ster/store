import { formatMoney } from '../../../utils/formatters';

export function RankingChart({
  rows,
  currency,
}: {
  rows: { label: string; quantity: number; revenue: number }[];
  currency: string;
}) {
  const max = Math.max(1, ...rows.map((row) => row.revenue));
  return (
    <div className="ranking-chart">
      {rows.slice(0, 10).map((row, index) => (
        <div className="ranking-row" key={row.label}>
          <b>{index + 1}</b>
          <span title={row.label}>{row.label}</span>
          <div>
            <i style={{ width: `${Math.max(2, (row.revenue / max) * 100)}%` }} />
          </div>
          <strong>{formatMoney(row.revenue, currency)}</strong>
        </div>
      ))}
    </div>
  );
}
