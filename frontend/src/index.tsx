import React from 'react';
import { createRoot } from 'react-dom/client';
import './i18n/i18n';
import './styles/index.css';
import './styles/enhancements.css';
import './styles/presence.css';
import App from './App';
createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
