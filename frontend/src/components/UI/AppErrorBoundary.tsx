import { Component, type ErrorInfo, type ReactNode } from 'react';
import i18n from '../../i18n/i18n';

type State = { failed: boolean };

export class AppErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  componentDidCatch(error: Error, details: ErrorInfo) {
    console.error('Renderer error', error, details.componentStack);
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <main className="fatal-error">
        <div className="logo">S</div>
        <h1>{i18n.t('appRecoveryTitle')}</h1>
        <p>{i18n.t('appRecoveryMessage')}</p>
        <button onClick={() => window.location.reload()}>{i18n.t('reloadApp')}</button>
      </main>
    );
  }
}
