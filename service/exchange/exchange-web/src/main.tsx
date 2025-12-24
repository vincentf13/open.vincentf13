import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import 'antd/dist/reset.css';
import './index.css';

/**
 * 執行點，將 React App 掛到瀏覽器頁面。
 * 要點
 * 找到 HTML 中的 #root
 * 建立 React root
 * 把 <App /> 渲染進去
 * StrictMode 做開發期檢查
 */

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
