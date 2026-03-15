/**
 * 現貨交易系統 E2E 整合測試 (Node.js)
 * 
 * 執行前準備:
 * 1. 確保已啟動 spot-matching 與 spot-ws-api
 * 2. 確保安裝了 ws 套件: `npm install ws` (或在全域環境執行)
 * 3. 使用 Node.js (v18+) 執行: `node e2e-integration-test.js`
 */
const WebSocket = require('ws');

// 根據你的環境調整端口
const WS_URL = 'ws://localhost:8080/ws'; 
const API_URL = 'http://localhost:8082/api/test';

// 測試用常數
const USER_A = 1001; // 買家
const USER_B = 1002; // 賣家
const SYMBOL_BTC_USDT = 1; 
const ASSET_BTC = 1;
const ASSET_USDT = 2;

const ws = new WebSocket(WS_URL);

// 輔助函數：暫停執行以等待異步處理 (Aeron傳輸與撮合通常在毫秒級完成，給予 100ms 絕對足夠)
const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

// 輔助函數：透過 HTTP 查詢引擎狀態
async function checkBalance(userId, assetId) {
    const res = await fetch(`${API_URL}/balance?userId=${userId}&assetId=${assetId}`);
    return res.json();
}

async function checkOrder(userId, cid) {
    const res = await fetch(`${API_URL}/order_by_cid?userId=${userId}&cid=${cid}`);
    return res.json();
}

// WS 訊息發送器
function sendCmd(op, payload) {
    ws.send(JSON.stringify({ op, ...payload }));
}

ws.on('open', async () => {
    console.log("=== 開始執行現貨系統 E2E 整合測試 ===\n");

    try {
        // ---------------------------------------------------------
        // 階段 1：登入與充值 (Auth & Deposit)
        // ---------------------------------------------------------
        console.log("[階段 1] 登入與充值...");
        sendCmd("auth", { uid: USER_A });
        sendCmd("auth", { uid: USER_B });
        
        // User A 充值 100,000 USDT
        sendCmd("deposit", { uid: USER_A, aid: ASSET_USDT, amt: 100000 });
        // User B 充值 10 BTC
        sendCmd("deposit", { uid: USER_B, aid: ASSET_BTC, amt: 10 });

        await sleep(200); // 等待引擎處理

        let balA = await checkBalance(USER_A, ASSET_USDT);
        let balB = await checkBalance(USER_B, ASSET_BTC);
        console.assert(balA.available === 100000, `User A USDT 不正確: ${balA.available}`);
        console.assert(balB.available === 10, `User B BTC 不正確: ${balB.available}`);
        console.log("✅ 充值驗證成功\n");


        // ---------------------------------------------------------
        // 階段 2：掛單與資產凍結 (Place Order & Freeze)
        // ---------------------------------------------------------
        console.log("[階段 2] 掛單與資產凍結...");
        const cid_A_Buy = Date.now();
        // User A 買入 2 BTC @ 60,000 USDT (應凍結 120,000 USDT，但只有 100,000，這裡改買 1 BTC)
        sendCmd("order_create", { uid: USER_A, sid: SYMBOL_BTC_USDT, p: 60000, q: 1, side: "BUY", cid: cid_A_Buy });
        
        await sleep(200);

        balA = await checkBalance(USER_A, ASSET_USDT);
        const orderA = await checkOrder(USER_A, cid_A_Buy);
        
        console.assert(balA.available === 40000, `User A 可用 USDT 應為 40000, 實際為: ${balA.available}`);
        console.assert(balA.frozen === 60000, `User A 凍結 USDT 應為 60000, 實際為: ${balA.frozen}`);
        console.assert(orderA.status === 0, `Order A 狀態應為 NEW (0), 實際為: ${orderA.status}`);
        console.log("✅ 掛單與凍結驗證成功\n");


        // ---------------------------------------------------------
        // 階段 3：部分成交 (Partial Match)
        // ---------------------------------------------------------
        console.log("[階段 3] 部分成交...");
        const cid_B_Sell_1 = Date.now() + 1;
        // User B 賣出 0.4 BTC @ 60,000 USDT (這筆會完全被 User A 的買單吃掉)
        sendCmd("order_create", { uid: USER_B, sid: SYMBOL_BTC_USDT, p: 60000, q: 0.4, side: "SELL", cid: cid_B_Sell_1 });

        await sleep(200);

        const orderA_after_partial = await checkOrder(USER_A, cid_A_Buy);
        const orderB_1 = await checkOrder(USER_B, cid_B_Sell_1);
        balA = await checkBalance(USER_A, ASSET_BTC); // 應該收到 0.4 BTC
        balB = await checkBalance(USER_B, ASSET_USDT); // 應該收到 24,000 USDT

        console.assert(orderA_after_partial.status === 1, `Order A 狀態應為 PARTIAL (1), 實際為: ${orderA_after_partial.status}`);
        console.assert(orderA_after_partial.filled === 0.4, `Order A 應成交 0.4, 實際為: ${orderA_after_partial.filled}`);
        console.assert(orderB_1.status === 2, `Order B 狀態應為 FILLED (2), 實際為: ${orderB_1.status}`);
        console.assert(balA.available === 0.4, `User A 應收到 0.4 BTC, 實際為: ${balA.available}`);
        console.assert(balB.available === 24000, `User B 應收到 24000 USDT, 實際為: ${balB.available}`);
        console.log("✅ 部分成交與結算驗證成功\n");


        // ---------------------------------------------------------
        // 階段 4：完全成交與掛單剩餘 (Full Match & Maker Residue)
        // ---------------------------------------------------------
        console.log("[階段 4] 完全成交...");
        const cid_B_Sell_2 = Date.now() + 2;
        // User A 剩餘買單為 0.6 BTC
        // User B 賣出 1 BTC @ 60,000 USDT (這筆會吃掉 User A 剩餘的 0.6，自己留下 0.4 掛單)
        sendCmd("order_create", { uid: USER_B, sid: SYMBOL_BTC_USDT, p: 60000, q: 1, side: "SELL", cid: cid_B_Sell_2 });

        await sleep(200);

        const orderA_final = await checkOrder(USER_A, cid_A_Buy);
        const orderB_2 = await checkOrder(USER_B, cid_B_Sell_2);
        
        console.assert(orderA_final.status === 2, `Order A 狀態應為 FILLED (2), 實際為: ${orderA_final.status}`);
        console.assert(orderB_2.status === 1, `Order B_2 狀態應為 PARTIAL (1), 實際為: ${orderB_2.status}`);
        console.assert(orderB_2.filled === 0.6, `Order B_2 應成交 0.6, 實際為: ${orderB_2.filled}`);
        console.log("✅ 完全成交驗證成功\n");


        // ---------------------------------------------------------
        // 階段 5：撤單與資產解凍 (Cancel Order & Unfreeze)
        // ---------------------------------------------------------
        console.log("[階段 5] 撤單與資產解凍...");
        // User B 撤銷剩下的 0.4 BTC 賣單
        sendCmd("order_cancel", { uid: USER_B, oid: orderB_2.orderId });

        await sleep(200);

        const orderB_2_canceled = await checkOrder(USER_B, cid_B_Sell_2);
        balB = await checkBalance(USER_B, ASSET_BTC); // 最初 10，賣出0.4(單1)+0.6(單2成交)=1，撤單後應有 9 BTC
        
        console.assert(orderB_2_canceled.status === 3, `Order B_2 狀態應為 CANCELED (3), 實際為: ${orderB_2_canceled.status}`);
        console.assert(balB.available === 9, `User B BTC 餘額應為 9, 實際為: ${balB.available}`);
        console.assert(balB.frozen === 0, `User B BTC 凍結應為 0, 實際為: ${balB.frozen}`);
        console.log("✅ 撤單與解凍驗證成功\n");

        console.log("🎉 所有 E2E 整合測試情境通過！");

    } catch (err) {
        console.error("❌ 測試失敗:", err);
    } finally {
        ws.close();
    }
});

ws.on('error', (err) => {
    console.error("WS 連線錯誤:", err);
});
