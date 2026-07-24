const colors = ['#19a974', '#3976ef', '#e59d25', '#d45353', '#8b5cf6', '#0891b2'];

export function PriceLineChart({
  rows,
}: {
  rows: { period: string; productId: number; productName: string; averagePrice: number }[];
}) {
  const periods = [...new Set(rows.map((row) => row.period))];
  const products = [
    ...new Map(rows.map((row) => [row.productId, row.productName])).entries(),
  ].slice(0, 6);
  const values = rows.map((row) => row.averagePrice);
  const min = Math.min(...values, 0);
  const max = Math.max(...values, 1);
  const point = (period: string, value: number) => {
    const x = 42 + (periods.indexOf(period) / Math.max(1, periods.length - 1)) * 718;
    const y = 208 - ((value - min) / Math.max(1, max - min)) * 170;
    return `${x},${y}`;
  };
  return (
    <div className="price-chart">
      <div className="report-legend">
        {products.map(([id, name], index) => (
          <span key={id}>
            <i style={{ background: colors[index] }} />
            {name}
          </span>
        ))}
      </div>
      <svg viewBox="0 0 800 245" role="img">
        {[0, 1, 2, 3, 4].map((line) => (
          <line key={line} x1="42" x2="760" y1={38 + line * 42.5} y2={38 + line * 42.5} />
        ))}
        {products.map(([id], index) => {
          const productRows = rows.filter((row) => row.productId === id);
          return (
            <polyline
              key={id}
              points={productRows.map((row) => point(row.period, row.averagePrice)).join(' ')}
              style={{ stroke: colors[index] }}
            />
          );
        })}
        {periods.map((period) => (
          <text
            key={period}
            x={42 + (periods.indexOf(period) / Math.max(1, periods.length - 1)) * 718}
            y="233"
          >
            {period}
          </text>
        ))}
      </svg>
    </div>
  );
}
