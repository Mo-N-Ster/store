import { useTranslation } from 'react-i18next';

export function Spinner() {
  const { t } = useTranslation();
  return <div className="spinner" role="status" aria-label={t('loading')} />;
}
