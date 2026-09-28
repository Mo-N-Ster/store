import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ReportData } from './ReportData';

export function PriceLineChart({ rows, label, showTable = true }: { rows: { period: string; productId: number; productName: string; averagePrice: number }[]; label?: string; showTable?: boolean }) {
  const { t } = useTranslation();
  const [hidden, setHidden] = useState<Set<number>>(new Set());
  const periods = [...new Set(rows.map((row) => row.period))].sort();
  const products = [...new Map(rows.map((row) => [row.productId, row.productName])).entries()].sort((a,b) => a[0]-b[0]);
  const max = Math.max(1, ...rows.filter((row) => !hidden.has(row.productId)).map((row) => row.averagePrice));
  const x = (period: string) => 64 + periods.indexOf(period) / Math.max(1, periods.length - 1) * 672;
  const y = (value: number) => 220 - value / max * 180;
  const color = (index: number) => `hsl(${index * 137.508 % 360} 65% 48%)`;
  return <div className="price-chart">
    <div className="report-legend">{products.map(([id,name], index) => <button type="button" key={id} aria-pressed={!hidden.has(id)} onClick={() => setHidden((current) => { const next = new Set(current); if (next.has(id)) next.delete(id); else next.add(id); return next; })}><i style={{ background: color(index) }} />{name}</button>)}</div>
    <svg viewBox="0 0 800 270" role="img" aria-label={label || t('priceEvolutionReport')}>
      {[0,1,2,3,4].map((tick) => <g key={tick}><line x1="64" x2="736" y1={y(max*tick/4)} y2={y(max*tick/4)} /><text x="56" y={y(max*tick/4)+4} textAnchor="end">{(max*tick/4).toLocaleString(undefined,{maximumFractionDigits:2})}</text></g>)}
      {products.map(([id,name],index) => hidden.has(id) ? null : <g key={id}>
        <polyline points={rows.filter((row) => row.productId===id).sort((a,b)=>a.period.localeCompare(b.period)).map((row)=>`${x(row.period)},${y(row.averagePrice)}`).join(' ')} style={{stroke:color(index)}} />
        {rows.filter((row)=>row.productId===id).map((row)=><circle key={row.period} cx={x(row.period)} cy={y(row.averagePrice)} r="4" fill={color(index)}><title>{name} · {row.period} : {row.averagePrice}</title></circle>)}
      </g>)}
      {periods.filter((_,index)=>index % Math.max(1,Math.ceil(periods.length/6))===0 || index===periods.length-1).map((period)=><text key={period} x={x(period)} y="250" textAnchor="middle">{period}</text>)}
    </svg>
    {showTable && <ReportData><div className="ds-responsive-table"><table>
      <thead><tr><th>{t('period')}</th><th>{t('product')}</th><th>{t('unitPrice')}</th></tr></thead>
      <tbody>{rows.map((row, index) => <tr key={index}><td>{row.period}</td><td>{row.productName}</td><td>{row.averagePrice.toLocaleString()}</td></tr>)}</tbody>
    </table></div></ReportData>}
  </div>;
}
