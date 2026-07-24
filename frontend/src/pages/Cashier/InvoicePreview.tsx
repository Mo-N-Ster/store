import { useTranslation } from 'react-i18next';
import { formatMoney } from '../../utils/formatters';
export function InvoicePreview({
  data,
  close,
  currency,
  discountsEnabled,
}: {
  data: any;
  close: () => void;
  currency: string;
  discountsEnabled: boolean;
}) {
  const { t } = useTranslation();
  const invoice = data.invoice;
  const printInvoice = () => {
    const cleanup = () => document.body.classList.remove('invoice-print-mode');
    document.body.classList.add('invoice-print-mode');
    window.addEventListener('afterprint', cleanup, { once: true });
    window.print();
    window.setTimeout(cleanup, 1000);
  };
  return (
    <div className="modal" onMouseDown={close}>
      <section className="receipt" onMouseDown={(event) => event.stopPropagation()}>
        <div className="receipt-brand">STORE</div>
        <h2>
          {t('invoice')} {invoice.id}
        </h2>
        <p>
          {new Date(invoice.invoice_date).toLocaleString()} · {invoice.seller} ({invoice.initials})
        </p>
        <table>
          <tbody>
            {data.lines.map((line: any) => (
              <tr key={line.id}>
                <td>{line.product_name}</td>
                <td>
                  {line.quantity} × {formatMoney(line.unit_price, currency)}
                </td>
                <td>{formatMoney(line.total_line, currency)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p>
          {t('categories')}: {[...new Set(data.lines.map((line: any) => line.category))].join(', ')}
        </p>
        <p>
          {t('subtotal')}: {formatMoney(invoice.subtotal, currency)}
        </p>
        {discountsEnabled && (
          <p>
            {t('discount')}: {formatMoney(invoice.discount, currency)}
          </p>
        )}
        <h2>
          {t('total')}: {formatMoney(invoice.total_amount, currency)}
        </h2>
        <div>
          <button onClick={printInvoice}>{t('print')}</button>
          <button className="ghost" onClick={close}>
            {t('close')}
          </button>
        </div>
      </section>
    </div>
  );
}
