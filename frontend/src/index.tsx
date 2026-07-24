import React from 'react';
import { createRoot } from 'react-dom/client';
import './i18n/i18n';
import './styles/index.css';
import './styles/enhancements.css';
import './styles/presence.css';
import './styles/cart-controls.css';
import './styles/password-reset.css';
import App from './App';
import { AppErrorBoundary } from './components/UI/AppErrorBoundary';
createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AppErrorBoundary>
      <App />
    </AppErrorBoundary>
  </React.StrictMode>,
);
