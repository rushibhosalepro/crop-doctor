import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.jsx';
import Eval from './Eval.jsx';
import './App.css';

// /?eval opens the eval runner; everything else is the app.
const isEval = new URLSearchParams(window.location.search).has('eval');

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    {isEval ? <Eval /> : <App />}
  </React.StrictMode>
);
