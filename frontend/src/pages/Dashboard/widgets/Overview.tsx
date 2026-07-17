import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { dashboardService } from '../../../services/dashboardService';
import { formatMoney } from '../../../utils/formatters';
import { useStorePreferences } from '../../../hooks/useStorePreferences';
export function Overview() {
  const { t } = useTranslation();
  const { currency } = useStorePreferences();
  const [data, setData] = useState<any>({});
  useEffect(() => {
    dashboardService.get().then(setData);
  }, []);
  const cards = [
    ['📦', 'products', data.products],
    ['⚠', 'lowStock', data.lowStock],
    ['🧾', 'salesToday', data.salesToday],
    ['↗', 'revenueToday', formatMoney(data.revenueToday, currency)],
    ['◫', 'revenueMonth', formatMoney(data.revenueMonth, currency)],
  ];
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">{t('overview')}</span>
          <h1>{t('home')}</h1>
        </div>
        <p>{t('overviewDescription')}</p>
      </div>
      <div className="widgets">
        {cards.map(([icon, label, value]) => (
          <article key={label}>
            <span className="widget-icon">{icon}</span>
            <span>{t(String(label))}</span>
            <b>{value}</b>
          </article>
        ))}
      </div>
    </>
  );
}
