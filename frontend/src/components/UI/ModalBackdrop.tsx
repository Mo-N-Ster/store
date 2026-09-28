import { useEffect, useRef, type ReactNode } from 'react';

const focusableSelector =
  'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function ModalBackdrop({
  children,
  onClose,
  dismissible = true,
  className = '',
}: {
  children: ReactNode;
  onClose?: () => void;
  dismissible?: boolean;
  className?: string;
}) {
  const rootRef = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;
  const dismissibleRef = useRef(dismissible);
  dismissibleRef.current = dismissible;
  useEffect(() => {
    const previousFocus = document.activeElement as HTMLElement | null;
    const root = rootRef.current;
    const focusable = () => Array.from(root?.querySelectorAll<HTMLElement>(focusableSelector) ?? []);
    const frame = window.requestAnimationFrame(() => {
      const requested = root?.querySelector<HTMLElement>('[data-autofocus], [autofocus]');
      if (requested) requested.focus();
      else if (!root?.contains(document.activeElement)) focusable()[0]?.focus();
    });
    const handleKeyDown = (event: KeyboardEvent) => {
      if (className === 'chat-compose-overlay' && (event.key === 'Escape' || event.key === 'Tab')) event.stopPropagation();
      if (event.key === 'Escape' && dismissibleRef.current && onCloseRef.current) {
        event.preventDefault();
        onCloseRef.current();
        return;
      }
      if (event.key !== 'Tab') return;
      const items = focusable();
      if (!items.length) return;
      const first = items[0];
      const last = items.at(-1)!;
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', handleKeyDown, className === 'chat-compose-overlay');
    return () => {
      document.removeEventListener('keydown', handleKeyDown, className === 'chat-compose-overlay');
      window.cancelAnimationFrame(frame);
      previousFocus?.focus();
    };
  }, [className]);

  return (
    <div
      ref={rootRef}
      className={`modal ${className}`}
      role="dialog"
      aria-modal="true"
      onMouseDown={(event) => {
        if (dismissible && onClose && event.target === event.currentTarget) onClose();
      }}
    >
      {children}
    </div>
  );
}
