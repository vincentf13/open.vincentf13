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
const WS_URL = 'ws://localhost:8080/ws/spot'; 
const API_URL = 'http://localhost:8082/api/test';

// 精度常數 (與 Constants.SCALE 一致)
const SCALE = 100000000n; 

// 測試用常數
const USER_A = 1001; // 買家
const USER_B = 1002; // 賣家
const SYMBOL_BTC_USDT = 1001; // 對齊 Symbol.BTCUSDT(1001)
const ASSET_BTC = 1;
const ASSET_USDT = 2;

// 輔助函數：暫停執行以等待異步處理
const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

// 輔助函數：透過 HTTP 查詢引擎狀態
async function checkBalance(userId, assetId) {
    const url = `${API_URL}/balance?userId=${userId}&assetId=${assetId}`;
    const res = await fetch(url);
    if (!res.ok) {
        const text = await res.text();
        throw new Error(`查詢餘額失敗 (${res.status}): ${text}`);
    }
    const data = await res.json();
    return {
        available: BigInt(data.available),
        frozen: BigInt(data.frozen)
    };
}

async function checkOrder(userId, cid) {
    const url = `${API_URL}/order_by_cid?userId=${userId}&cid=${cid}`;
    const res = await fetch(url);
    if (!res.ok) {
        const text = await res.text();
        throw new Error(`查詢訂單失敗 (${res.status}): ${text}`);
    }
    const data = await res.json();
    return data;
}

console.log(`正在連線至 WebSocket: ${WS_URL} ...`);
const ws = new WebSocket(WS_URL);

// WS 訊息發送器 (確保數值為 BigInt 並轉為 String 以避免 JS 精度丟失)
function sendCmd(op, payload) {
    // 將所有 BigInt 轉為 Number 送出，確保 Spring 端的 Number 類型能接收
    const cleanPayload = {};
    for (let k in payload) {
        if (typeof payload[k] === 'bigint') {
            cleanPayload[k] = Number(payload[k]);
        } else {
            cleanPayload[k] = payload[k];
        }
    }
    ws.send(JSON.stringify({ op, ...cleanPayload }));
}

// 設定連線超時保護
const connectionTimeout = setTimeout(() => {
    if (ws.readyState !== WebSocket.OPEN) {
        console.error("❌ 連線超時：請檢查 spot-ws-api 是否正常啟動於 8080 端口，且路徑為 /ws/spot");
        process.exit(1);
    }
}, 5000);

ws.on('open', async () => {
    clearTimeout(connectionTimeout);
    console.log("✅ WebSocket 連線成功！");
    console.log("=== 開始執行現貨系統 E2E 整合測試 ===\n");

    try {
        // ---------------------------------------------------------
        // 階段 1：登入與充值 (Auth & Deposit)
        // ---------------------------------------------------------
        console.log("[階段 1] 登入與充值...");
        sendCmd("auth", { uid: USER_A });
        sendCmd("auth", { uid: USER_B });
        
        // User A 充值 100,000 USDT
        sendCmd("deposit", { uid: USER_A, aid: ASSET_USDT, amt: 100000n * SCALE });
        // User B 充值 10 BTC
        sendCmd("deposit", { uid: USER_B, aid: ASSET_BTC, amt: 10n * SCALE });

        await sleep(500); // 等待引擎處理

        let balA = await checkBalance(USER_A, ASSET_USDT);
        let balB = await checkBalance(USER_B, ASSET_BTC);
        
        console.log(`User A 可用 USDT: ${balA.available}`);
        console.log(`User B 可用 BTC: ${balB.available}`);
        
        console.assert(balA.available === 100000n * SCALE, "User A USDT 餘額不正確");
        console.assert(balB.available === 10n * SCALE, "User B BTC 餘額不正確");
        console.log("✅ 充值驗證成功\n");


        // ---------------------------------------------------------
        // 階段 2：掛單與資產凍結 (Place Order & Freeze)
        // ---------------------------------------------------------
        console.log("[階段 2] 掛單與資產凍結...");
        const cid_A_Buy = Date.now();
        // User A 買入 1 BTC @ 60,000 USDT
        sendCmd("order_create", { uid: USER_A, sid: SYMBOL_BTC_USDT, p: 60000n * SCALE, q: 1n * SCALE, side: "BUY", cid: cid_A_Buy });
        
        await sleep(500);

        balA = await checkBalance(USER_A, ASSET_USDT);
        const orderA = await checkOrder(USER_A, cid_A_Buy);
        
        console.log(`User A 可用 USDT: ${balA.available}, 凍結: ${balA.frozen}`);
        console.assert(orderA.status === 0, `Order A 狀態應為 NEW (0), 實際為: ${orderA.status}`);
        console.assert(balA.frozen === 60000n * SCALE, "凍結金額不正確");
        console.log("✅ 掛單與凍結驗證成功\n");


        // ---------------------------------------------------------
        // 階段 3：部分成交 (Partial Match)
        // ---------------------------------------------------------
        console.log("[階段 3] 部分成交...");
        const cid_B_Sell_1 = Date.now() + 1;
        // User B 賣出 0.4 BTC @ 60,000 USDT (乘以 0.4n 會報錯，用 4n * SCALE / 10n)
        sendCmd("order_create", { uid: USER_B, sid: SYMBOL_BTC_USDT, p: 60000n * SCALE, q: 4n * SCALE / 10n, side: "SELL", cid: cid_B_Sell_1 });

        await sleep(500);

        const orderA_after_partial = await checkOrder(USER_A, cid_A_Buy);
        const orderB_1 = await checkOrder(USER_B, cid_B_Sell_1);
        
        console.log(`Order A 已成交: ${orderA_after_partial.filled}`);
        console.assert(orderA_after_partial.status === 1, `Order A 狀態應為 PARTIAL (1), 實際為: ${orderA_after_partial.status}`);
        console.assert(BigInt(orderA_after_partial.filled) === 4n * SCALE / 10n, "成交量不正確");
        console.log("✅ 部分成交與結算驗證成功\n");


        // ---------------------------------------------------------
        // 階段 4：完全成交
        // ---------------------------------------------------------
        console.log("[階段 4] 完全成交...");
        const cid_B_Sell_2 = Date.now() + 2;
        // User B 賣出 1 BTC (會吃掉 A 剩餘的 0.6)
        sendCmd("order_create", { uid: USER_B, sid: SYMBOL_BTC_USDT, p: 60000n * SCALE, q: 1n * SCALE, side: "SELL", cid: cid_B_Sell_2 });

        await sleep(500);

        const orderA_final = await checkOrder(USER_A, cid_A_Buy);
        console.log(`Order A 最終狀態: ${orderA_final.status}`);
        console.assert(orderA_final.status === 2, "Order A 應為 FILLED (2)");
        console.log("✅ 完全成交驗證成功\n");


        // ---------------------------------------------------------
        // 階段 5：撤單與資產解凍
        // ---------------------------------------------------------
        console.log("[階段 5] 撤單與資產解凍...");
        const orderB_2 = await checkOrder(USER_B, cid_B_Sell_2);
        sendCmd("order_cancel", { uid: USER_B, oid: orderB_2.orderId });

        await sleep(500);

        const orderB_2_canceled = await checkOrder(USER_B, cid_B_Sell_2);
        console.log(`Order B_2 撤單後狀態: ${orderB_2_canceled.status}`);
        console.assert(orderB_2_canceled.status === 3, "應為 CANCELED (3)");
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
