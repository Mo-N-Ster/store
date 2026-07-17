import { useTranslation } from 'react-i18next';

export function CriticalDialog({ message, onClose }: { message: string; onClose: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="modal" onMouseDown={onClose}>
      <section
        className="form-modal critical-dialog"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <h2>{t('attention')}</h2>
        <p>{message}</p>
        <button className="danger" onClick={onClose}>
          {t('close')}
        </button>
      </section>
    </div>
  );
}
