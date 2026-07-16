import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { saleService } from '../../../services/saleService';
import { formatMoney } from '../../../utils/formatters';
import { InvoicePreview } from '../../Cashier/InvoicePreview';
export function SalesHistory() {
  const { t } = useTranslation();
  const [rows, setRows] = useState<any[]>([]);
  const [filters, setFilters] = useState({ from: '', to: '', search: '' });
  const [detail, setDetail] = useState(null);
  useEffect(() => {
    saleService.list(filters).then(setRows);
  }, [filters]);
  const csv = [
    'ID,Date,Total,Vendeur',
    ...rows.map((row) => `${row.id},${row.invoiceDate},${row.totalAmount},${row.seller}`),
  ].join('\n');
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">Transactions</span>
          <h1>{t('sales')}</h1>
        </div>
      </div>
      <div className="filters">
        <input type="date" onChange={(e) => setFilters({ ...filters, from: e.target.value })} />
        <input type="date" onChange={(e) => setFilters({ ...filters, to: e.target.value })} />
        <input
          placeholder={t('search')}
          onChange={(e) => setFilters({ ...filters, search: e.target.value })}
        />
        <button onClick={() => saleService.exportCsv('ventes.csv', csv)}>Exporter CSV</button>
      </div>
      <div className="table-shell">
        <table>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id} onClick={() => saleService.detail(row.id).then(setDetail)}>
                <td>
                  <b>{row.id}</b>
                </td>
                <td>{new Date(row.invoiceDate).toLocaleString()}</td>
                <td>{row.seller}</td>
                <td>{formatMoney(row.totalAmount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {detail && <InvoicePreview data={detail} close={() => setDetail(null)} />}
    </>
  );
}
