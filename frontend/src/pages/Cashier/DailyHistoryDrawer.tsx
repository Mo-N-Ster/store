import { Receipt } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Drawer } from '../../design-system';
import { formatMoney } from '../../utils/formatters';

export function DailyHistoryDrawer({ invoices, currency, onSelect, onClose }: { invoices: any[]; currency: string; onSelect: (id: string) => void; onClose: () => void }) {
  const { t } = useTranslation();
  return <Drawer title={t('salesToday')} description={t('dailyHistoryHint')} closeLabel={t('close')} onClose={onClose}><div className="pos-history">
    {!invoices.length ? <p className="ds-secondary">{t('noSalesToday')}</p> : invoices.map((invoice) => <button type="button" key={invoice.id} onClick={() => onSelect(invoice.id)}><Receipt aria-hidden="true" /><span><strong>{invoice.id}</strong><small>{new Date(invoice.invoiceDate).toLocaleTimeString()}</small></span><b>{formatMoney(Number(invoice.totalAmount ?? invoice.total_amount ?? 0), currency)}</b></button>)}
  </div></Drawer>;
}
