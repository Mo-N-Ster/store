import { useId, type HTMLAttributes, type KeyboardEvent, type ReactNode } from 'react';

export function nextTabIndex(key: string, current: number, count: number, orientation: 'horizontal' | 'vertical' = 'horizontal') {
  const previous = orientation === 'horizontal' ? 'ArrowLeft' : 'ArrowUp';
  const next = orientation === 'horizontal' ? 'ArrowRight' : 'ArrowDown';
  if (key === previous) return (current - 1 + count) % count;
  if (key === next) return (current + 1) % count;
  if (key === 'Home') return 0;
  if (key === 'End') return count - 1;
  return null;
}

export function Card({ interactive = false, className = '', children, ...props }: HTMLAttributes<HTMLElement> & { interactive?: boolean }) {
  const Element = interactive ? 'button' : 'article';
  const elementProps = interactive ? { type: 'button', ...props } : props;
  return <Element {...(elementProps as any)} className={`ds-card ${interactive ? 'ds-card--interactive' : ''} ${className}`.trim()}>{children}</Element>;
}

export function KpiCard({ label, value, trend, context, action }: { label: ReactNode; value: ReactNode; trend?: ReactNode; context?: ReactNode; action?: ReactNode }) {
  return (
    <article className="ds-card ds-kpi-card">
      <div className="ds-kpi-card__header"><span className="ds-secondary">{label}</span>{trend}</div>
      <strong className="ds-kpi-value">{value}</strong>
      {(context || action) && <footer className="ds-kpi-card__footer">{context && <span className="ds-caption">{context}</span>}{action}</footer>}
    </article>
  );
}

export function Tabs({ tabs, active, onChange, ariaLabel, orientation = 'horizontal' }: { tabs: Array<{ id: string; label: ReactNode; panel: ReactNode }>; active: string; onChange: (id: string) => void; ariaLabel: string; orientation?: 'horizontal' | 'vertical' }) {
  const baseId = useId();
  const activate = (index: number) => {
    const tab = tabs[(index + tabs.length) % tabs.length];
    onChange(tab.id);
    requestAnimationFrame(() => document.getElementById(`${baseId}-tab-${tab.id}`)?.focus());
  };
  const onKeyDown = (event: KeyboardEvent, index: number) => {
    const next = nextTabIndex(event.key, index, tabs.length, orientation);
    if (next !== null) { event.preventDefault(); activate(next); }
  };
  const selected = tabs.find((tab) => tab.id === active) ?? tabs[0];
  return (
    <div className="ds-tabs">
      <div className="ds-tabs__list" role="tablist" aria-label={ariaLabel} aria-orientation={orientation}>
        {tabs.map((tab, index) => <button id={`${baseId}-tab-${tab.id}`} key={tab.id} className="ds-tabs__tab" type="button" role="tab" aria-selected={selected.id === tab.id} aria-controls={`${baseId}-panel-${tab.id}`} tabIndex={selected.id === tab.id ? 0 : -1} onClick={() => onChange(tab.id)} onKeyDown={(event) => onKeyDown(event, index)}>{tab.label}</button>)}
      </div>
      <section id={`${baseId}-panel-${selected.id}`} role="tabpanel" aria-labelledby={`${baseId}-tab-${selected.id}`} tabIndex={0}>{selected.panel}</section>
    </div>
  );
}

export function DataList({ children, label }: { children: ReactNode; label: string }) {
  return <ul className="ds-data-list" aria-label={label}>{children}</ul>;
}

export function DataListItem({ primary, secondary, status, actions, onClick }: { primary: ReactNode; secondary?: ReactNode; status?: ReactNode; actions?: ReactNode; onClick?: () => void }) {
  const keyHandler = (event: KeyboardEvent<HTMLLIElement>) => {
    if (onClick && (event.key === 'Enter' || event.key === ' ')) { event.preventDefault(); onClick(); }
  };
  return (
    <li className={`ds-data-list__item ${onClick ? 'ds-data-list__item--interactive' : ''}`} tabIndex={onClick ? 0 : undefined} onClick={onClick} onKeyDown={keyHandler}>
      <div><div className="ds-data-list__primary">{primary}</div>{secondary && <div className="ds-data-list__secondary">{secondary}</div>}</div>
      {(status || actions) && <div>{status}{actions}</div>}
    </li>
  );
}

export function ResponsiveTable({ label, minWidth = '42rem', children }: { label: string; minWidth?: string; children: ReactNode }) {
  return <div className="ds-responsive-table" role="region" aria-label={label} tabIndex={0} style={{ '--ds-table-min-width': minWidth } as React.CSSProperties}>{children}</div>;
}
