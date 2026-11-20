import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.jsx';
import Eval from './Eval.jsx';
import './App.css';

// /?eval opens the eval runner; everything else is the app.
const isEval = new URLSearchParams(window.location.search).has('eval');

// Offline shell: lets the page load after a refresh with no connection.
// Skipped on localhost so local development always gets fresh files.
if ('serviceWorker' in navigator && !['localhost', '127.0.0.1'].includes(location.hostname)) {
  window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js'));
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    {isEval ? <Eval /> : <App />}
  </React.StrictMode>
);
