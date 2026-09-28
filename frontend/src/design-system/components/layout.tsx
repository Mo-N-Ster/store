import type { HTMLAttributes, ReactNode } from 'react';

export function AppShell({ header, sidebar, children, className = '' }: { header: ReactNode; sidebar?: ReactNode; children: ReactNode; className?: string }) {
  return <div className={`ds-app-shell ${className}`.trim()}><div className="ds-app-shell__header">{header}</div>{sidebar && <div className="ds-app-shell__sidebar">{sidebar}</div>}<main className="ds-app-shell__main">{children}</main></div>;
}

export function ShellHeader({ children, className = '', ...props }: HTMLAttributes<HTMLElement>) {
  return <header {...props} className={`ds-shell-header ${className}`.trim()}>{children}</header>;
}

export function ShellSidebar({ children, label, className = '', ...props }: HTMLAttributes<HTMLElement> & { label: string }) {
  return <nav {...props} className={`ds-shell-sidebar ${className}`.trim()} aria-label={label}>{children}</nav>;
}
