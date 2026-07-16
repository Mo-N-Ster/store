import type { ReactNode } from 'react';
export function AuthCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main className="auth">
      <section className="auth-card">
        <div className="logo">S</div>
        <h1>{title}</h1>
        {children}
      </section>
    </main>
  );
}
