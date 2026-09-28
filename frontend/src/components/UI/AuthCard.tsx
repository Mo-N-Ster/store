import type { ReactNode } from 'react';
export function AuthCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main className="auth">
      <section className="auth-card">
        <img className="logo" src="./store-logo.png" alt="STORE" />
        <h1>{title}</h1>
        {children}
      </section>
    </main>
  );
}
