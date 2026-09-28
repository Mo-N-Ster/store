import { forwardRef, useState, type InputHTMLAttributes } from 'react';
import { useTranslation } from 'react-i18next';
import { Eye, EyeOff } from 'lucide-react';

export const PasswordInput = forwardRef<
  HTMLInputElement,
  Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>
>(function PasswordInput(props, ref) {
  const { t } = useTranslation();
  const [visible, setVisible] = useState(false);

  return (
    <span className="password-input">
      <input {...props} ref={ref} type={visible ? 'text' : 'password'} />
      <button
        type="button"
        disabled={props.disabled}
        className="password-visibility"
        aria-label={t(visible ? 'hidePassword' : 'showPassword')}
        aria-pressed={visible}
        title={t(visible ? 'hidePassword' : 'showPassword')}
        onMouseDown={(event) => event.preventDefault()}
        onClick={() => setVisible((current) => !current)}
      >
        {visible ? <EyeOff size={19} aria-hidden="true" /> : <Eye size={19} aria-hidden="true" />}
      </button>
    </span>
  );
});
