import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { formatMoney } from '../../utils/formatters';
import { ModalBackdrop } from '../../components/UI/ModalBackdrop';
import { NumberInput } from '../../design-system';

export function CashSessionDialog({
  mode,
  expectedAmount = 0,
  currency,
  onSubmit,
  onClose,
}: {
  mode: 'open' | 'close';
  expectedAmount?: number;
  currency: string;
  onSubmit: (amount: number) => Promise<void>;
  onClose?: () => void;
}) {
  const { t } = useTranslation();
  const [amount, setAmount] = useState(mode === 'close' ? String(expectedAmount) : '0');
  const [busy, setBusy] = useState(false);
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    try {
      await onSubmit(Number(amount));
    } finally {
      setBusy(false);
    }
  };
  return (
    <ModalBackdrop onClose={onClose} dismissible={Boolean(onClose) && !busy}>
      <form className="form-modal cash-session-dialog" onSubmit={submit} onMouseDown={(event) => event.stopPropagation()}>
        <span className="eyebrow">{t('cashSession')}</span>
        <h2>{t(mode === 'open' ? 'openCashSession' : 'closeCashSession')}</h2>
        {mode === 'close' && <p>{t('expectedCash')}: <b>{formatMoney(expectedAmount, currency)}</b></p>}
        <NumberInput label={t(mode === 'open' ? 'openingCash' : 'countedCash')} disabled={busy} min="0" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} autoFocus required />
        <div className="dialog-actions">
          {onClose && <button type="button" disabled={busy} className="ghost" onClick={onClose}>{t('cancel')}</button>}
          <button disabled={busy}>{busy ? t('saving') : t('validate')}</button>
        </div>
      </form>
    </ModalBackdrop>
  );
}
