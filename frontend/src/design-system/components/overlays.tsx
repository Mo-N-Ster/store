import { useEffect, useId, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';
import { Button, IconButton } from './controls';

const focusableSelector = 'button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),a[href],[tabindex]:not([tabindex="-1"])';

export function overlayFocusIndex(shiftKey: boolean, activeIndex: number, count: number) {
  if (count <= 0) return -1;
  if (shiftKey && activeIndex === 0) return count - 1;
  if (!shiftKey && activeIndex === count - 1) return 0;
  return null;
}

function useOverlayFocus(onClose: () => void, dismissible: boolean) {
  const ref = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;
  const dismissibleRef = useRef(dismissible);
  dismissibleRef.current = dismissible;
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const root = ref.current;
    const focusable = () => Array.from(root?.querySelectorAll<HTMLElement>(focusableSelector) ?? []);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const frame = requestAnimationFrame(() => {
      const requested = root?.querySelector<HTMLElement>('[data-autofocus], [autofocus]');
      if (requested) requested.focus();
      else if (!root?.contains(document.activeElement)) (focusable()[0] ?? root)?.focus();
    });
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && dismissibleRef.current) { event.preventDefault(); onCloseRef.current(); return; }
      if (event.key !== 'Tab') return;
      const items = focusable();
      if (!items.length) { event.preventDefault(); root?.focus(); return; }
      const activeIndex = items.indexOf(document.activeElement as HTMLElement);
      const nextIndex = overlayFocusIndex(event.shiftKey, activeIndex, items.length);
      if (nextIndex !== null) { event.preventDefault(); items[nextIndex]?.focus(); }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      cancelAnimationFrame(frame);
      document.body.style.overflow = previousOverflow;
      previous?.focus();
    };
  }, []);
  return ref;
}

export function Modal({ title, description, closeLabel, onClose, children, actions, dismissible = true }: { title: ReactNode; description?: ReactNode; closeLabel: string; onClose: () => void; children: ReactNode; actions?: ReactNode; dismissible?: boolean }) {
  const titleId = useId();
  const descriptionId = useId();
  const ref = useOverlayFocus(onClose, dismissible);
  return (
    <div className="ds-overlay" onMouseDown={(event) => dismissible && event.target === event.currentTarget && onClose()}>
      <div ref={ref} className="ds-modal" role="dialog" aria-modal="true" aria-labelledby={titleId} aria-describedby={description ? descriptionId : undefined} tabIndex={-1}>
        <header className="ds-modal__header"><div><h2 id={titleId} className="ds-section-title">{title}</h2>{description && <p id={descriptionId} className="ds-secondary">{description}</p>}</div>{dismissible && <IconButton icon={X} label={closeLabel} onClick={onClose} />}</header>
        <div>{children}</div>
        {actions && <footer className="ds-modal__actions">{actions}</footer>}
      </div>
    </div>
  );
}

export function ConfirmDialog({ title, description, confirmLabel, cancelLabel, onConfirm, onClose, danger = false }: { title: ReactNode; description: ReactNode; confirmLabel: string; cancelLabel: string; onConfirm: () => void; onClose: () => void; danger?: boolean }) {
  return <Modal title={title} description={description} closeLabel={cancelLabel} onClose={onClose} actions={<><Button variant="secondary" onClick={onClose}>{cancelLabel}</Button><Button variant={danger ? 'danger' : 'primary'} data-autofocus onClick={onConfirm}>{confirmLabel}</Button></>}><span /></Modal>;
}

export function Drawer({ side = 'right', title, description, closeLabel, onClose, children, dismissible = true, className = '' }: { side?: 'right' | 'left' | 'bottom'; title: ReactNode; description?: ReactNode; closeLabel: string; onClose: () => void; children: ReactNode; dismissible?: boolean; className?: string }) {
  const titleId = useId();
  const descriptionId = useId();
  const ref = useOverlayFocus(onClose, dismissible);
  return (
    <div className={`ds-overlay ds-overlay--drawer-${side}`} onMouseDown={(event) => dismissible && event.target === event.currentTarget && onClose()}>
      <aside ref={ref} className={`ds-drawer ${className}`.trim()} role="dialog" aria-modal="true" aria-labelledby={titleId} aria-describedby={description ? descriptionId : undefined} tabIndex={-1}>
        <header className="ds-drawer__header"><div><h2 id={titleId} className="ds-section-title">{title}</h2>{description && <p id={descriptionId} className="ds-secondary">{description}</p>}</div><IconButton icon={X} label={closeLabel} onClick={onClose} /></header>
        {children}
      </aside>
    </div>
  );
}
