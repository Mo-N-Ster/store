import { useEffect, useRef, useState } from 'react';
import {
  Check,
  ChevronLeft,
  ChevronRight,
  CircleCheck,
  LockKeyhole,
  Store,
  UserRound,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button, Select, TextInput } from '../../design-system';
import { PasswordInput } from '../../components/UI/PasswordInput';
import { authService } from '../../services/authService';
import { settingsService } from '../../services/settingsService';
import { SUPPORTED_CURRENCIES } from '../../hooks/useStorePreferences';
import type { User } from '../../types';

type Step = 'welcome' | 'shop' | 'owner' | 'preferences' | 'verification' | 'ready';
type SetupDraft = {
  storeName: string;
  address: string;
  phone: string;
  storeEmail: string;
  firstName: string;
  lastName: string;
  username: string;
  email: string;
  password: string;
  securityQuestion: string;
  securityAnswer: string;
  currency: string;
  language: 'fr' | 'en';
};
const steps: Step[] = ['welcome', 'shop', 'owner', 'preferences', 'verification', 'ready'];
const initialDraft: SetupDraft = {
  storeName: 'STORE',
  address: '',
  phone: '',
  storeEmail: '',
  firstName: '',
  lastName: '',
  username: '',
  email: '',
  password: '',
  securityQuestion: '',
  securityAnswer: '',
  currency: 'EUR',
  language: 'fr',
};

export function Setup({ onDone }: { onDone: (user: User) => void }) {
  const { t, i18n } = useTranslation();
  const [step, setStep] = useState<Step>('welcome');
  const [draft, setDraft] = useState(initialDraft);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [createdOwner, setCreatedOwner] = useState<User | null>(null);
  const lock = useRef(false);
  const heading = useRef<HTMLHeadingElement>(null);
  const index = steps.indexOf(step);
  const update = (key: keyof SetupDraft, value: string) =>
    setDraft((current) => ({ ...current, [key]: value }));
  useEffect(() => {
    heading.current?.focus();
  }, [step]);
  const valid =
    step === 'welcome' ||
    (step === 'shop' && (!draft.storeEmail || draft.storeEmail.includes('@'))) ||
    step === 'preferences' ||
    (step === 'owner' &&
      Boolean(
        draft.firstName.trim() &&
        draft.lastName.trim() &&
        draft.username.trim() &&
        draft.email.includes('@') &&
        draft.password.length >= 8 &&
        draft.securityQuestion.trim() &&
        draft.securityAnswer.trim(),
      ));
  const next = () => {
    if (!valid || index >= steps.length - 1) return;
    setError('');
    setStep(steps[index + 1]);
  };
  const back = () => {
    if (index <= 0 || submitting || createdOwner) return;
    setError('');
    setStep(steps[index - 1]);
  };
  const initialize = async () => {
    if (lock.current) return;
    lock.current = true;
    setSubmitting(true);
    setError('');
    let ownerCreated = Boolean(createdOwner);
    try {
      let owner = createdOwner;
      if (!owner) {
        owner = await authService.setupAdmin({
          firstName: draft.firstName,
          lastName: draft.lastName,
          username: draft.username,
          email: draft.email,
          password: draft.password,
          securityQuestion: draft.securityQuestion,
          securityAnswer: draft.securityAnswer,
        });
        ownerCreated = true;
        setCreatedOwner(owner);
        setDraft((current) => ({ ...current, password: '', securityAnswer: '' }));
      }
      await settingsService.save({
        storeName: draft.storeName.trim() || 'STORE',
        address: draft.address.trim(),
        phone: draft.phone.trim(),
        email: draft.storeEmail.trim(),
        currency: draft.currency,
      });
      await i18n.changeLanguage(draft.language);
      localStorage.setItem('lang', draft.language);
      setStep('ready');
    } catch (caught) {
      const code = caught instanceof Error ? caught.message : '';
      setError(
        t(
          ownerCreated || code.includes('SETUP_ALREADY_COMPLETED')
            ? 'setupPreferencesFailed'
            : 'checkSetupFields',
        ),
      );
    } finally {
      lock.current = false;
      setSubmitting(false);
    }
  };
  return (
    <main className="setup-shell">
      <section className="setup-wizard" aria-labelledby="setup-title">
        <header className="setup-brand">
          <img src="./store-logo.png" alt="STORE" />
          <div>
            <strong>STORE</strong>
            <span>{t('setupLocalFirst')}</span>
          </div>
        </header>
        <ol
          className="setup-progress"
          aria-label={t('setupProgress')}
          aria-valuemin={1}
          aria-valuemax={6}
          aria-valuenow={index + 1}
        >
          {steps.map((item, itemIndex) => (
            <li
              key={item}
              className={itemIndex === index ? 'current' : itemIndex < index ? 'complete' : ''}
              aria-current={itemIndex === index ? 'step' : undefined}
            >
              <span>{itemIndex < index ? <Check /> : itemIndex + 1}</span>
              <small>{t(`setupStep_${item}`)}</small>
            </li>
          ))}
        </ol>
        <div className="setup-stage">
          <span className="eyebrow">
            {t('setupStepCount', { current: index + 1, total: steps.length })}
          </span>
          <h1 id="setup-title" ref={heading} tabIndex={-1}>
            {t(`setupTitle_${step}`)}
          </h1>
          <p className="setup-lead">{t(`setupLead_${step}`)}</p>
          {step === 'welcome' && <Welcome />}
          {step === 'shop' && <ShopStep draft={draft} update={update} />}
          {step === 'owner' && <OwnerStep draft={draft} update={update} />}
          {step === 'preferences' && <PreferencesStep draft={draft} update={update} />}
          {step === 'verification' && <Verification draft={draft} onEdit={setStep} />}
          {step === 'ready' && <Ready />}
          {error && (
            <div className="setup-error" role="alert">
              <strong>{t('setupCouldNotComplete')}</strong>
              <span>{error}</span>
            </div>
          )}
        </div>
        <footer className="setup-actions">
          {index > 0 && step !== 'ready' && (
            <Button
              variant="secondary"
              icon={ChevronLeft}
              disabled={submitting || Boolean(createdOwner)}
              onClick={back}
            >
              {t('back')}
            </Button>
          )}
          <span />
          {step === 'verification' ? (
            <Button
              loading={submitting}
              loadingLabel={t('initializingStore')}
              disabled={submitting}
              onClick={initialize}
            >
              {createdOwner ? t('retryPreferences') : t('configureStore')}
            </Button>
          ) : step === 'ready' ? (
            <Button onClick={() => onDone(createdOwner!)}>{t('continueToStore')}</Button>
          ) : (
            <Button icon={ChevronRight} disabled={!valid} onClick={next}>
              {t('continue')}
            </Button>
          )}
        </footer>
      </section>
    </main>
  );
}

function Welcome() {
  const { t } = useTranslation();
  return (
    <div className="setup-facts">
      <article>
        <Store />
        <strong>{t('setupFactStore')}</strong>
        <p>{t('setupFactStoreHint')}</p>
      </article>
      <article>
        <UserRound />
        <strong>{t('setupFactOwner')}</strong>
        <p>{t('setupFactOwnerHint')}</p>
      </article>
      <article>
        <LockKeyhole />
        <strong>{t('setupFactLocal')}</strong>
        <p>{t('setupFactLocalHint')}</p>
      </article>
    </div>
  );
}
function ShopStep({ draft, update }: StepProps) {
  const { t } = useTranslation();
  return (
    <div className="setup-form">
      <TextInput
        label={t('storeName')}
        value={draft.storeName}
        onChange={(e) => update('storeName', e.target.value)}
        help={t('setupStoreNameOptional')}
        autoFocus
      />
      <TextInput
        label={t('address')}
        value={draft.address}
        onChange={(e) => update('address', e.target.value)}
      />
      <div className="setup-form__row">
        <TextInput
          label={t('phone')}
          value={draft.phone}
          onChange={(e) => update('phone', e.target.value)}
        />
        <TextInput
          label={t('storeEmail')}
          type="email"
          value={draft.storeEmail}
          onChange={(e) => update('storeEmail', e.target.value)}
          error={
            draft.storeEmail && !draft.storeEmail.includes('@') ? t('invalidEmail') : undefined
          }
        />
      </div>
    </div>
  );
}
function OwnerStep({ draft, update }: StepProps) {
  const { t } = useTranslation();
  const passwordError =
    draft.password && draft.password.length < 8 ? t('passwordMinimum') : undefined;
  return (
    <div className="setup-form">
      <div className="setup-form__row">
        <TextInput
          label={t('firstName')}
          value={draft.firstName}
          onChange={(e) => update('firstName', e.target.value)}
          required
          autoFocus
        />
        <TextInput
          label={t('lastName')}
          value={draft.lastName}
          onChange={(e) => update('lastName', e.target.value)}
          required
        />
      </div>
      <TextInput
        label={t('usernameOnly')}
        value={draft.username}
        onChange={(e) => update('username', e.target.value)}
        required
        autoComplete="username"
      />
      <TextInput
        label={t('email')}
        type="email"
        value={draft.email}
        onChange={(e) => update('email', e.target.value)}
        required
        autoComplete="email"
        error={draft.email && !draft.email.includes('@') ? t('invalidEmail') : undefined}
      />
      <label className="ds-field">
        <span className="ds-field__label">{t('password')} *</span>
        <PasswordInput
          value={draft.password}
          onChange={(e) => update('password', e.target.value)}
          minLength={8}
          required
          autoComplete="new-password"
          aria-invalid={passwordError ? true : undefined}
          aria-describedby={passwordError ? 'setup-password-error' : 'setup-password-help'}
        />
        {passwordError ? (
          <small id="setup-password-error" className="ds-field__error" role="alert">
            {passwordError}
          </small>
        ) : (
          <small id="setup-password-help" className="ds-field__help">
            {t('passwordMinimum')}
          </small>
        )}
      </label>
      <TextInput
        label={t('securityQuestion')}
        value={draft.securityQuestion}
        onChange={(e) => update('securityQuestion', e.target.value)}
        required
      />
      <label className="ds-field">
        <span className="ds-field__label">{t('securityAnswer')} *</span>
        <PasswordInput
          value={draft.securityAnswer}
          onChange={(e) => update('securityAnswer', e.target.value)}
          required
          autoComplete="off"
        />
      </label>
      <p className="setup-security-note">{t('setupSecretsHint')}</p>
    </div>
  );
}
function PreferencesStep({ draft, update }: StepProps) {
  const { t } = useTranslation();
  return (
    <div className="setup-form">
      <Select
        label={t('language')}
        value={draft.language}
        onChange={(e) => update('language', e.target.value)}
      >
        <option value="fr">Français</option>
        <option value="en">English</option>
      </Select>
      <Select
        label={t('currency')}
        value={draft.currency}
        onChange={(e) => update('currency', e.target.value)}
        help={t('currencyHistoryWarning')}
      >
        {SUPPORTED_CURRENCIES.map((code) => (
          <option key={code} value={code}>
            {t(`currency${code}`)}
          </option>
        ))}
      </Select>
      <p className="setup-security-note">{t('setupOptionalLater')}</p>
    </div>
  );
}
function Verification({ draft, onEdit }: { draft: SetupDraft; onEdit: (step: Step) => void }) {
  const { t } = useTranslation();
  return (
    <div className="setup-review">
      <Review
        title={t('setupStep_shop')}
        action={() => onEdit('shop')}
        rows={[
          [t('storeName'), draft.storeName || 'STORE'],
          [t('address'), draft.address || '—'],
          [t('phone'), draft.phone || '—'],
        ]}
      />
      <Review
        title={t('setupStep_owner')}
        action={() => onEdit('owner')}
        rows={[
          [t('fullName'), `${draft.firstName} ${draft.lastName}`],
          [t('usernameOnly'), draft.username],
          [t('email'), draft.email],
          [t('role'), t('owner')],
        ]}
      />
      <Review
        title={t('setupStep_preferences')}
        action={() => onEdit('preferences')}
        rows={[
          [t('language'), draft.language === 'fr' ? 'Français' : 'English'],
          [t('currency'), draft.currency],
        ]}
      />
      <p className="setup-security-note">{t('setupVerificationNoSecrets')}</p>
    </div>
  );
}
function Review({ title, rows, action }: { title: string; rows: string[][]; action: () => void }) {
  const { t } = useTranslation();
  return (
    <section>
      <header>
        <h2>{title}</h2>
        <Button variant="ghost" size="sm" onClick={action}>
          {t('edit')}
        </Button>
      </header>
      <dl>
        {rows.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}
function Ready() {
  const { t } = useTranslation();
  return (
    <div className="setup-ready">
      <CircleCheck />
      <h2>{t('storeReady')}</h2>
      <p>{t('storeReadyHint')}</p>
      <ul>
        <li>{t('ownerCreated')}</li>
        <li>{t('storeInitialized')}</li>
      </ul>
    </div>
  );
}
type StepProps = { draft: SetupDraft; update: (key: keyof SetupDraft, value: string) => void };
