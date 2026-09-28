import { useState } from 'react';
import { useTranslation } from 'react-i18next';

const sections = [
  ['helpStartTitle', 'helpStartBody'],
  ['helpNavigationTitle', 'helpNavigationBody'],
  ['helpCashierTitle', 'helpCashierBody'],
  ['helpStockTitle', 'helpStockBody'],
  ['helpUsersTitle', 'helpUsersBody'],
  ['helpMessagingTitle', 'helpMessagingBody'],
  ['helpReportsTitle', 'helpReportsBody'],
  ['helpSettingsTitle', 'helpSettingsBody'],
  ['helpPermissionsTitle', 'helpPermissionsBody'],
  ['helpSafetyTitle', 'helpSafetyBody'],
  ['helpContinuityTitle', 'helpContinuityBody'],
  ['helpDailyRoutineTitle', 'helpDailyRoutineBody'],
  ['helpEmergencyTitle', 'helpEmergencyBody'],
  ['helpHandoverTitle', 'helpHandoverBody'],
  ['helpTroubleshootingTitle', 'helpTroubleshootingBody'],
] as const;

export function HelpPage() {
  const { t } = useTranslation();
  const [printing, setPrinting] = useState(false);
  const [query, setQuery] = useState('');
  const [error, setError] = useState(false);
  const visible = sections.map(([title, body], index) => ({ title, body, index })).filter(({ title, body }) => `${t(title)} ${t(body)}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()));
  const printGuide = async () => {
    if (printing) return;
    setPrinting(true);
    setError(false);
    setQuery('');
    document.body.classList.add('document-print-mode');
    try {
      await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
      await window.store.printInvoice();
    } catch {
      setError(true);
    } finally {
      document.body.classList.remove('document-print-mode');
      setPrinting(false);
      setQuery(query);
    }
  };
  return (
    <article className="help-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">STORE · VIBE</span>
          <h1>{t('userGuide')}</h1>
        </div>
        <button className="ghost" disabled={printing} onClick={() => void printGuide()}>{printing ? t('printing') : t('printGuide')}</button>
      </div>
      <p className="help-intro">{t('helpIntro')}</p>
      <div className="help-search"><label htmlFor="help-search">{t('helpSearch')}</label><input id="help-search" type="search" value={query} onChange={(event) => setQuery(event.target.value)} /><span role="status">{t('helpResults', { count: visible.length })}</span>{query && <button className="ghost" onClick={() => setQuery('')}>{t('clearFilters')}</button>}</div>
      {error && <p role="alert">{t('operationFailed')}</p>}
      <nav className="help-toc" aria-label={t('guideContents')}>
        {visible.map(({ title, index }) => (
          <a href={`#guide-${index + 1}`} key={title}>{index + 1}. {t(title)}</a>
        ))}
      </nav>
      <div className="help-sections">
        {!visible.length && <p role="status">{t('helpNoResults')}</p>}
        {visible.map(({ title, body, index }) => (
          <section id={`guide-${index + 1}`} key={title}>
            <span>{String(index + 1).padStart(2, '0')}</span>
            <div><h2>{t(title)}</h2>{t(body).split('\n').map((paragraph, line) => <p key={line}>{paragraph}</p>)}<a href="#help-search">{t('helpBackToSearch')}</a></div>
          </section>
        ))}
      </div>
      <aside className="help-callout"><b>{t('important')}</b><p>{t('helpBackupReminder')}</p></aside>
    </article>
  );
}
