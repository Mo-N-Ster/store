import React from'react';import{createRoot}from'react-dom/client';import'./i18n';import'./styles/global.css';import App from'./app/App';
createRoot(document.getElementById('root')!).render(<React.StrictMode><App/></React.StrictMode>);
