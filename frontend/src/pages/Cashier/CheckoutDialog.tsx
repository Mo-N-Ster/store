import { Banknote, Check } from 'lucide-react';
import { useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Modal, NumberInput } from '../../design-system';
import { formatMoney } from '../../utils/formatters';
import { cashChange } from './posModel';

export function CheckoutDialog({ total, received, setReceived, currency, submitting, onConfirm, onClose }: { total: number; received: string; setReceived: (value: string) => void; currency: string; submitting: boolean; onConfirm: () => void; onClose: () => void }) {
  const { t } = useTranslation();
  const inputRef = useRef<HTMLInputElement>(null);
  const numericReceived = Number(received);
  const insufficient = received.trim() !== '' && (!Number.isFinite(numericReceived) || numericReceived < total);
  const change = cashChange(total, Number.isFinite(numericReceived) ? numericReceived : 0);
  return <Modal title={t('checkout')} description={t('checkoutHint')} closeLabel={t('close')} onClose={onClose} dismissible={!submitting}>
    <form className="pos-checkout" onSubmit={(event) => { event.preventDefault(); if (!insufficient && received.trim()) onConfirm(); }}>
      <div className="pos-checkout__amount"><span>{t('amountDue')}</span><strong>{formatMoney(total, currency)}</strong></div>
      <NumberInput ref={inputRef} data-autofocus id="cash-received" label={t('amountReceived')} min={total} step="0.01" value={received} disabled={submitting} error={insufficient ? t('amountReceivedInsufficient') : undefined} onChange={(event) => setReceived(event.target.value)} />
      <Button type="button" variant="secondary" icon={Banknote} disabled={submitting} onClick={() => { setReceived(String(total)); requestAnimationFrame(() => inputRef.current?.focus()); }}>{t('exactAmount')}</Button>
      <dl className="pos-checkout__summary"><div><dt>{t('amountReceived')}</dt><dd>{formatMoney(Number.isFinite(numericReceived) ? numericReceived : 0, currency)}</dd></div><div><dt>{t('changeDue')}</dt><dd>{formatMoney(change, currency)}</dd></div></dl>
      <Button className="pos-checkout__confirm" type="submit" size="lg" icon={Check} loading={submitting} loadingLabel={t('saleInProgress')} disabled={!received.trim() || insufficient}>{t('confirmSale')}</Button>
    </form>
  </Modal>;
}
