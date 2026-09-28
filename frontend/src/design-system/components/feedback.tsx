import type { HTMLAttributes, ReactNode } from 'react';
import { AlertCircle, CheckCircle2, Info, TriangleAlert, X } from 'lucide-react';
import { IconButton } from './controls';

type SemanticVariant = 'info' | 'success' | 'warning' | 'danger';
const icons = { info: Info, success: CheckCircle2, warning: TriangleAlert, danger: AlertCircle };

export function Badge({ variant = 'neutral', children }: { variant?: SemanticVariant | 'neutral'; children: ReactNode }) {
  return <span className={`ds-badge ds-badge--${variant}`}>{children}</span>;
}

export function Alert({ variant = 'info', title, children, action }: { variant?: SemanticVariant; title?: ReactNode; children: ReactNode; action?: ReactNode }) {
  const Icon = icons[variant];
  return (
    <section className={`ds-alert ds-alert--${variant}`} role={variant === 'danger' ? 'alert' : 'status'}>
      <Icon size={20} aria-hidden="true" />
      <div className="ds-alert__content">{title && <strong>{title}</strong>}<div>{children}</div></div>
      {action}
    </section>
  );
}

export function ToastRegion({ children, label }: { children: ReactNode; label: string }) {
  return <section className="ds-toast-region" aria-label={label}>{children}</section>;
}

export function Toast({ variant = 'info', children, dismissLabel, onDismiss }: { variant?: Exclude<SemanticVariant, 'danger'> | 'error'; children: ReactNode; dismissLabel?: string; onDismiss?: () => void }) {
  const normalized = variant === 'error' ? 'danger' : variant;
  const Icon = icons[normalized];
  return (
    <div className={`ds-toast ds-toast--${variant}`} role={variant === 'error' ? 'alert' : 'status'} aria-live={variant === 'error' ? 'assertive' : 'polite'}>
      <Icon size={20} aria-hidden="true" />
      <div className="ds-toast__message">{children}</div>
      {onDismiss && dismissLabel && <IconButton icon={X} label={dismissLabel} onClick={onDismiss} />}
    </div>
  );
}

export function Skeleton({ label, className = '', ...props }: HTMLAttributes<HTMLDivElement> & { label: string }) {
  return <div {...props} className={`ds-skeleton ${className}`.trim()} role="status" aria-label={label} />;
}

function State({ kind, icon: Icon, title, description, action }: { kind: 'empty' | 'error'; icon: typeof Info; title: ReactNode; description?: ReactNode; action?: ReactNode }) {
  return (
    <section className={`ds-state ds-state--${kind}`}>
      <Icon size={32} aria-hidden="true" />
      <h2 className="ds-section-title">{title}</h2>
      {description && <p>{description}</p>}
      {action}
    </section>
  );
}
export function EmptyState(props: Omit<Parameters<typeof State>[0], 'kind' | 'icon'> & { icon?: typeof Info }) { return <State {...props} kind="empty" icon={props.icon ?? Info} />; }
export function ErrorState(props: Omit<Parameters<typeof State>[0], 'kind' | 'icon'>) { return <State {...props} kind="error" icon={AlertCircle} />; }
