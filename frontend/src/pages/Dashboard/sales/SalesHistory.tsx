import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { saleService } from '../../../services/saleService';
import { reportService } from '../../../services/reportService';
import { formatMoney } from '../../../utils/formatters';
import { InvoicePreview } from '../../Cashier/InvoicePreview';
import { useStorePreferences } from '../../../hooks/useStorePreferences';
import { SubTabs } from '../../../components/UI/SubTabs';

type HistoryType = 'sales' | 'purchases' | 'personnel';
export function SalesHistory({ userId }: { userId: number }) {
  const { t } = useTranslation();
  const preferences = useStorePreferences();
  const [rows, setRows] = useState<any[]>([]);
  const [filters, setFilters] = useState({ from: '', to: '' });
  const [type, setType] = useState<HistoryType>('sales');
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [detail, setDetail] = useState(null);
  const load = useCallback(
    () =>
      saleService.history({ type, ...filters }).then((items: any[]) => {
        setRows(items);
        setSelected(new Set());
      }),
    [filters, type],
  );
  useEffect(() => void load(), [load]);
  const toggleAll = () =>
    setSelected(
      selected.size === rows.length ? new Set() : new Set(rows.map((row) => String(row.id))),
    );
  const exportPdf = async () => {
    document.body.classList.add('document-print-mode');
    try {
      await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
      await reportService.exportPdf(
        `STORE-${type}-${filters.from || 'all'}-${filters.to || 'all'}.pdf`,
      );
    } finally {
      document.body.classList.remove('document-print-mode');
    }
  };
  const removeSelected = async () => {
    if (!selected.size || !confirm(t('confirmHistoryDeletion'))) return;
    if (type === 'sales') {
      for (const id of selected) await saleService.remove({ id, userId });
    } else await saleService.deleteHistory({ type, ids: [...selected].map(Number), userId });
    await load();
  };
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">{t('transactions')}</span>
          <h1>{t('histories')}</h1>
        </div>
      </div>
      <div className="filters history-toolbar">
        <label>
          {t('from')}
          <input
            type="date"
            value={filters.from}
            onChange={(event) => setFilters({ ...filters, from: event.target.value })}
          />
        </label>
        <label>
          {t('to')}
          <input
            type="date"
            value={filters.to}
            onChange={(event) => setFilters({ ...filters, to: event.target.value })}
          />
        </label>
        <button onClick={exportPdf}>{t('exportPdf')}</button>
        <button className="danger" disabled={!selected.size} onClick={removeSelected}>
          {t('deleteSelected')}
        </button>
      </div>
      <SubTabs
        ariaLabel={t('historyTypes')}
        active={type}
        onChange={(id) => setType(id as HistoryType)}
        tabs={[
          { id: 'sales', label: t('sales') },
          { id: 'purchases', label: t('purchases') },
          { id: 'personnel', label: t('personnel') },
        ]}
      />
      <label className="select-all">
        <input
          type="checkbox"
          checked={rows.length > 0 && selected.size === rows.length}
          onChange={toggleAll}
        />
        {t('selectAll')}
      </label>
      <div className="table-shell">
        <table>
          <thead>
            <tr>
              <th></th>
              {type === 'sales' ? (
                <>
                  <th>{t('invoice')}</th>
                  <th>{t('date')}</th>
                  <th>{t('seller')}</th>
                  <th>{t('total')}</th>
                </>
              ) : type === 'purchases' ? (
                <>
                  <th>{t('date')}</th>
                  <th>{t('product')}</th>
                  <th>{t('category')}</th>
                  <th>{t('quantity')}</th>
                  <th>{t('unitPrice')}</th>
                  <th>{t('total')}</th>
                </>
              ) : (
                <>
                  <th>{t('employee')}</th>
                  <th>{t('start')}</th>
                  <th>{t('end')}</th>
                  <th>{t('hoursWorked')}</th>
                </>
              )}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr
                className={type === 'sales' ? 'clickable-row' : ''}
                key={row.id}
                onClick={() => type === 'sales' && saleService.detail(row.id).then(setDetail)}
              >
                <td>
                  <input
                    type="checkbox"
                    checked={selected.has(String(row.id))}
                    onClick={(event) => event.stopPropagation()}
                    onChange={() =>
                      setSelected((current) => {
                        const next = new Set(current);
                        if (next.has(String(row.id))) next.delete(String(row.id));
                        else next.add(String(row.id));
                        return next;
                      })
                    }
                  />
                </td>
                {type === 'sales' ? (
                  <>
                    <td>{row.id}</td>
                    <td>{new Date(row.invoiceDate).toLocaleString()}</td>
                    <td>{row.seller}</td>
                    <td>{formatMoney(row.totalAmount, preferences.currency)}</td>
                  </>
                ) : type === 'purchases' ? (
                  <>
                    <td>{new Date(row.date).toLocaleString()}</td>
                    <td>{row.product}</td>
                    <td>{row.category}</td>
                    <td>{row.quantity}</td>
                    <td>{formatMoney(row.unitPrice, preferences.currency)}</td>
                    <td>{formatMoney(row.total, preferences.currency)}</td>
                  </>
                ) : (
                  <>
                    <td>{row.employee}</td>
                    <td>{new Date(row.startTime).toLocaleString()}</td>
                    <td>
                      {row.endTime ? new Date(row.endTime).toLocaleString() : t('inProgress')}
                    </td>
                    <td>{row.hours}</td>
                  </>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {detail && (
        <InvoicePreview
          data={detail}
          close={() => setDetail(null)}
          currency={preferences.currency}
          discountsEnabled={preferences.discountsEnabled}
        />
      )}
    </>
  );
}
