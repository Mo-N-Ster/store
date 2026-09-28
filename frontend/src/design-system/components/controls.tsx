import {
  forwardRef,
  useEffect,
  useId,
  useRef,
  useState,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type MutableRefObject,
  type ReactNode,
  type SelectHTMLAttributes,
} from 'react';
import { LoaderCircle, Search, X, type LucideIcon } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { normalizeDecimal, validDecimal } from '../../utils/decimalInput';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ControlSize = 'sm' | 'md' | 'lg';

export const Button = forwardRef<HTMLButtonElement, ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ControlSize;
  loading?: boolean;
  loadingLabel?: string;
  icon?: LucideIcon;
}>(({ variant = 'primary', size = 'md', loading = false, loadingLabel, icon: Icon, children, disabled, className = '', ...props }, ref) => (
  <button
    {...props}
    ref={ref}
    className={`ds-button ds-button--${variant} ds-button--${size} ${className}`.trim()}
    disabled={disabled || loading}
    aria-busy={loading || undefined}
  >
    {loading ? <LoaderCircle className="ds-button__spinner" size={18} aria-hidden="true" /> : Icon ? <Icon size={18} aria-hidden="true" /> : null}
    {loading && loadingLabel ? loadingLabel : children}
  </button>
));
Button.displayName = 'Button';

export const IconButton = forwardRef<HTMLButtonElement, ButtonHTMLAttributes<HTMLButtonElement> & {
  label: string;
  icon: LucideIcon;
  size?: number;
}>(({ label, icon: Icon, size = 20, className = '', title, ...props }, ref) => (
  <button
    {...props}
    ref={ref}
    type={props.type ?? 'button'}
    className={`ds-icon-button ${className}`.trim()}
    aria-label={label}
    title={title ?? label}
  >
    <Icon size={size} aria-hidden="true" />
  </button>
));
IconButton.displayName = 'IconButton';

export function Field({
  id,
  label,
  required,
  help,
  error,
  children,
}: {
  id: string;
  label: ReactNode;
  required?: boolean;
  help?: ReactNode;
  error?: ReactNode;
  children: ReactNode;
}) {
  const helpId = help ? `${id}-help` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  return (
    <div className="ds-field" data-invalid={error ? 'true' : undefined}>
      <label className="ds-field__label" htmlFor={id}>
        {label}{required && <span className="ds-field__required" aria-hidden="true">*</span>}
      </label>
      {children}
      {help && <p className="ds-field__help" id={helpId}>{help}</p>}
      {error && <p className="ds-field__error" id={errorId} role="alert">{error}</p>}
    </div>
  );
}

type TextInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> & {
  label: ReactNode;
  help?: ReactNode;
  error?: ReactNode;
  clearLabel?: string;
  onClear?: () => void;
};

export const TextInput = forwardRef<HTMLInputElement, TextInputProps>(
  ({ id: suppliedId, label, help, error, required, type = 'text', clearLabel, onClear, className = '', ...props }, ref) => {
    const generatedId = useId();
    const id = suppliedId ?? generatedId;
    const describedBy = [help && `${id}-help`, error && `${id}-error`, props['aria-describedby']].filter(Boolean).join(' ') || undefined;
    return (
      <Field id={id} label={label} help={help} error={error} required={required}>
        <div className="ds-input-wrap">
          {type === 'search' && <Search size={18} aria-hidden="true" />}
          <input
            {...props}
            ref={ref}
            id={id}
            type={type}
            required={required}
            className={`ds-control ${className}`.trim()}
            aria-invalid={error ? true : undefined}
            aria-describedby={describedBy}
          />
          {onClear && clearLabel && <IconButton icon={X} label={clearLabel} onClick={onClear} />}
        </div>
      </Field>
    );
  },
);
TextInput.displayName = 'TextInput';

export const NumberInput = forwardRef<HTMLInputElement, Omit<TextInputProps, 'type'>>(
  ({ inputMode = 'decimal', ...props }, ref) => props.step === 'any' || (props.step !== undefined && Number(props.step) < 1)
    ? <DecimalInput {...props} ref={ref} inputMode={inputMode} />
    : <TextInput {...props} ref={ref} type="number" inputMode={inputMode} />,
);
NumberInput.displayName = 'NumberInput';

const DecimalInput = forwardRef<HTMLInputElement, Omit<TextInputProps, 'type'>>(
  ({ value, defaultValue, onChange, min, max, step, ...props }, forwardedRef) => {
    const { t } = useTranslation();
    const inputRef = useRef<HTMLInputElement | null>(null);
    const [draft, setDraft] = useState(String(value ?? defaultValue ?? ''));
    useEffect(() => {
      if (value !== undefined) setDraft((current) => Number(current) === Number(value) ? current : String(value));
    }, [value]);
    useEffect(() => { inputRef.current?.setCustomValidity(validDecimal(draft, min, max, step) ? '' : t('invalidDecimal')); }, [draft, min, max, step, t]);
    return <TextInput {...props} type="text" value={draft} ref={(node) => { inputRef.current = node; if (typeof forwardedRef === 'function') forwardedRef(node); else if (forwardedRef) forwardedRef.current = node; }} onChange={(event) => {
      const normalized = normalizeDecimal(event.currentTarget.value);
      if (!/^-?\d*(?:\.\d*)?$/.test(normalized)) { event.currentTarget.value = draft; return; }
      event.currentTarget.value = normalized;
      event.currentTarget.setCustomValidity(validDecimal(normalized, min, max, step) ? '' : t('invalidDecimal'));
      setDraft(normalized);
      if (normalized === '' || /^-?(?:\d+(?:\.\d*)?|\.\d+)$/.test(normalized)) onChange?.(event);
    }} />;
  },
);
DecimalInput.displayName = 'DecimalInput';

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement> & {
  label: ReactNode;
  help?: ReactNode;
  error?: ReactNode;
  placeholder?: string;
}>(({ id: suppliedId, label, help, error, required, placeholder, children, className = '', ...props }, ref) => {
  const generatedId = useId();
  const id = suppliedId ?? generatedId;
  const describedBy = [help && `${id}-help`, error && `${id}-error`, props['aria-describedby']].filter(Boolean).join(' ') || undefined;
  return (
    <Field id={id} label={label} help={help} error={error} required={required}>
      <select {...props} ref={ref} id={id} required={required} className={`ds-control ${className}`.trim()} aria-invalid={error ? true : undefined} aria-describedby={describedBy}>
        {placeholder && <option value="">{placeholder}</option>}
        {children}
      </select>
    </Field>
  );
});
Select.displayName = 'Select';

export const Checkbox = forwardRef<HTMLInputElement, Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> & {
  label: ReactNode;
  indeterminate?: boolean;
}>(({ label, indeterminate = false, className = '', ...props }, forwardedRef) => {
  const internalRef = useRef<HTMLInputElement | null>(null);
  useEffect(() => {
    if (internalRef.current) internalRef.current.indeterminate = indeterminate;
  }, [indeterminate]);
  const setRefs = (node: HTMLInputElement | null) => {
    internalRef.current = node;
    if (typeof forwardedRef === 'function') forwardedRef(node);
    else if (forwardedRef) (forwardedRef as MutableRefObject<HTMLInputElement | null>).current = node;
  };
  return (
    <label className={`ds-checkbox ${className}`.trim()}>
      <input {...props} ref={setRefs} type="checkbox" />
      <span>{label}</span>
    </label>
  );
});
Checkbox.displayName = 'Checkbox';
