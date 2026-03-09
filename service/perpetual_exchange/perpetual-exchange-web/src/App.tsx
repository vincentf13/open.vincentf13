import {BrowserRouter, Route, Routes} from 'react-router-dom';
import Login from './pages/Login';
import Register from './pages/Register';
import Trading from './pages/Trading';
import './App.css';

/**
 路由表。負責把不同 URL 對應到不同頁面元件。

 重點

 `<BrowserRouter>` 啟用前端路由
 `<Routes>` 包一堆 `<Route>`
 `path` 決定 URL
 `element` 決定顯示哪個元件
 `Navigate` 表示導向

 行為

 `/login` → 顯示 Login
 `/register` → 顯示 Register
 `/trading` → 顯示 Trading
 `/` → 顯示 Trading（未登入時由彈窗提示登入/註冊）
 */

function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/login" element={<Login/>}/>
                <Route path="/register" element={<Register/>}/>
                <Route path="/trading" element={<Trading/>}/>
                <Route path="/" element={<Trading/>}/>
            </Routes>
        </BrowserRouter>
    );
}

export default App;
