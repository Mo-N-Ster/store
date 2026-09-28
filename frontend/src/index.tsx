import React from 'react';
import { createRoot } from 'react-dom/client';
import './i18n/i18n';
import './styles/index.css';
import './styles/enhancements.css';
import './styles/presence.css';
import './styles/cart-controls.css';
import './styles/password-reset.css';
import './design-system/tokens.css';
import './design-system/foundations.css';
import './design-system/shell.css';
import './design-system/pos.css';
import './design-system/operations.css';
import './design-system/reporting.css';
import './design-system/administration.css';
import './design-system/setup.css';
import App from './App';
import { AppErrorBoundary } from './components/UI/AppErrorBoundary';
import { installNumericInputGuards } from './utils/numericInput';
import './design-system/refinements.css';
import './design-system/print-light.css';
installNumericInputGuards(document);
createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AppErrorBoundary>
      <App />
    </AppErrorBoundary>
  </React.StrictMode>,
);
