import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { CheckCircle2, FileDown, Plus, Printer } from 'lucide-react';
import { Button } from '../../design-system';
import { formatMoney } from '../../utils/formatters';
import { ModalBackdrop } from '../../components/UI/ModalBackdrop';
export function InvoicePreview({
  data,
  close,
  currency,
  discountsEnabled,
  saleCompleted = false,
}: {
  data: any;
  close: () => void;
  currency: string;
  discountsEnabled: boolean;
  saleCompleted?: boolean;
}) {
  const { t } = useTranslation();
  const [printing, setPrinting] = useState(false);
  const [printError, setPrintError] = useState('');
  const invoice = data.invoice;
  const receiptCurrency = invoice.currency || currency;
  const printInvoice = async () => {
    if (printing) return;
    setPrinting(true);
    setPrintError('');
    document.body.classList.add('invoice-print-mode');
    try {
      await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
      await window.store.printInvoice();
    } catch {
      setPrintError(t('printFailed'));
    } finally {
      document.body.classList.remove('invoice-print-mode');
      setPrinting(false);
    }
  };
  const exportPdf = async () => {
    if (printing) return;
    setPrinting(true);
    setPrintError('');
    document.body.classList.add('invoice-print-mode');
    try {
      await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
      await window.store.exportInvoicePdf({ name: `${invoice.id}.pdf` });
    } catch {
      setPrintError(t('printFailed'));
    } finally {
      document.body.classList.remove('invoice-print-mode');
      setPrinting(false);
    }
  };
  return (
    <ModalBackdrop onClose={close}>
      <section className="receipt" onMouseDown={(event) => event.stopPropagation()}>
        {saleCompleted && <div className="pos-sale-success"><CheckCircle2 aria-hidden="true" /><h2 data-autofocus tabIndex={-1}>{t('saleSuccessful')}</h2><span>{t('saleSuccessfulHint')}</span></div>}
        <div className="receipt-brand">{invoice.store_name || 'STORE'}</div>
        {(invoice.store_address || invoice.store_phone || invoice.store_email) && (
          <p className="receipt-contact">
            {[invoice.store_address, invoice.store_phone, invoice.store_email].filter(Boolean).join(' · ')}
          </p>
        )}
        <h2>
          {t('invoice')} {invoice.id}
        </h2>
        <p>
          {t('dateAndTime')}: {new Date(invoice.invoice_date || invoice.invoiceDate).toLocaleString()} · {invoice.seller} ({invoice.initials})
        </p>
        <table>
          <tbody>
            {data.lines.map((line: any) => (
              <tr key={line.id}>
                <td>{line.product_name}</td>
                <td>
                  {line.quantity} × {formatMoney(line.unit_price, receiptCurrency)}
                </td>
                <td>{formatMoney(line.total_line, receiptCurrency)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p>
          {t('categories')}: {[...new Set(data.lines.map((line: any) => line.category))].join(', ')}
        </p>
        <p>
          {t('subtotal')}: {formatMoney(invoice.subtotal, receiptCurrency)}
        </p>
        {discountsEnabled && (
          <p>
            {t('discount')}: {formatMoney(invoice.discount, receiptCurrency)}
          </p>
        )}
        <h2>
          {t('total')}: {formatMoney(invoice.total_amount, receiptCurrency)}
        </h2>
        {data.payment && (
          <div className="receipt-payment">
            <p>{t('amountReceived')}: {formatMoney(data.payment.amount_received, receiptCurrency)}</p>
            <p>{t('changeDue')}: {formatMoney(data.payment.change_amount, receiptCurrency)}</p>
          </div>
        )}
        {invoice.status === 'cancelled' && (
          <p className="receipt-cancelled">{t('cancelled')} — {invoice.cancellation_reason}</p>
        )}
        <div className="receipt-actions">
          <Button variant="secondary" icon={Printer} disabled={printing} onClick={() => void printInvoice()}>
            {printing ? t('printing') : t('print')}
          </Button>
          <Button variant={saleCompleted ? 'primary' : 'ghost'} icon={saleCompleted ? Plus : undefined} disabled={printing} onClick={close}>{saleCompleted ? t('newSale') : t('close')}</Button>
          <Button variant="secondary" icon={FileDown} disabled={printing} onClick={() => void exportPdf()}>
            {t('savePdf')}
          </Button>
        </div>
        {printError && <p className="error" role="alert">{printError}</p>}
      </section>
    </ModalBackdrop>
  );
}
