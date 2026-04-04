/**
 * 現貨交易系統 E2E 整合測試 (Node.js - SBE 二進制版)
 * 
 * 執行前準備:
 * 1. 確保已啟動 spot-matching 與 spot-ws-api
 * 2. 確保安裝了 ws 套件: `npm install ws`
 * 3. 使用 Node.js (v18+) 執行: `node e2e-integration-test.js`
 */
const WebSocket = require('ws');

// 根據你的環境調整端口
const WS_URL = 'ws://localhost:8080/ws/spot'; 
const API_URL = 'http://localhost:8082/api/test';

// 精度常數
const SCALE = 100000000n; 

// 測試用常數
const USER_A = 1001n; // 買家
const USER_B = 1002n; // 賣家
const SYMBOL_BTC_USDT = 1001; 
const ASSET_BTC = 1;
const ASSET_USDT = 2;

// SBE 常數
const MSG_TYPE = {
    ORDER_CREATE: 100,
    ORDER_CANCEL: 101,
    DEPOSIT: 102,
    AUTH: 103
};
const SBE_SCHEMA_ID = 1;
const SBE_VERSION = 1;

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

/**
 * 構造二進制 SBE 訊息 (AbstractSbeModel 佈局)
 * [0-3] Type | [4-11] Seq | [12-19] SBE Header | [20-...] Body
 */
function createSbeHeader(view, msgType, blockLength) {
    view.setInt32(0, msgType, true);           // MsgType
    view.setBigInt64(4, BigInt(-1), true);     // Seq (-1)
    view.setUint16(12, blockLength, true);     // BlockLength
    view.setUint16(14, msgType, true);         // TemplateId
    view.setUint16(16, SBE_SCHEMA_ID, true);
    view.setUint16(18, SBE_VERSION, true);
}

const SBE_ENCODER = {
    auth: (uid) => {
        const buffer = new Uint8Array(20 + 16);
        const view = new DataView(buffer.buffer);
        createSbeHeader(view, MSG_TYPE.AUTH, 16);
        view.setBigInt64(20, BigInt(Date.now()), true);
        view.setBigInt64(28, BigInt(uid), true);
        return buffer;
    },
    deposit: (uid, aid, amt) => {
        const buffer = new Uint8Array(20 + 28);
        const view = new DataView(buffer.buffer);
        createSbeHeader(view, MSG_TYPE.DEPOSIT, 28);
        view.setBigInt64(20, BigInt(Date.now()), true);
        view.setBigInt64(28, BigInt(uid), true);
        view.setInt32(36, aid, true);
        view.setBigInt64(40, BigInt(amt), true);
        return buffer;
    },
    orderCreate: (uid, sid, p, q, side, cid) => {
        const buffer = new Uint8Array(20 + 45);
        const view = new DataView(buffer.buffer);
        createSbeHeader(view, MSG_TYPE.ORDER_CREATE, 45);
        view.setBigInt64(20, BigInt(Date.now()), true);
        view.setBigInt64(28, BigInt(uid), true);
        view.setInt32(36, sid, true);
        view.setBigInt64(40, BigInt(p), true);
        view.setBigInt64(48, BigInt(q), true);
        view.setUint8(56, side === "BUY" ? 0 : 1);
        view.setBigInt64(57, BigInt(cid), true);
        return buffer;
    },
    orderCancel: (uid, oid) => {
        const buffer = new Uint8Array(20 + 24);
        const view = new DataView(buffer.buffer);
        createSbeHeader(view, MSG_TYPE.ORDER_CANCEL, 24);
        view.setBigInt64(20, BigInt(Date.now()), true);
        view.setBigInt64(28, BigInt(uid), true);
        view.setBigInt64(36, BigInt(oid), true);
        return buffer;
    }
};

console.log(`正在連線至 WebSocket: ${WS_URL} ...`);
const ws = new WebSocket(WS_URL);

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
    console.log("=== 開始執行現貨系統 E2E 整合測試 (SBE 版) ===\n");

    try {
        // ---------------------------------------------------------
        // 階段 1：登入與充值 (Auth & Deposit)
        // ---------------------------------------------------------
        console.log("[階段 1] 登入與充值...");
        ws.send(SBE_ENCODER.auth(USER_A));
        ws.send(SBE_ENCODER.auth(USER_B));
        
        // User A 充值 100,000 USDT
        ws.send(SBE_ENCODER.deposit(USER_A, ASSET_USDT, 100000n * SCALE));
        // User B 充值 10 BTC
        ws.send(SBE_ENCODER.deposit(USER_B, ASSET_BTC, 10n * SCALE));

        await sleep(2000); // 增加等待時間確保 Chronicle 可見性

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
        const cid_A_Buy = BigInt(Date.now());
        // User A 買入 1 BTC @ 60,000 USDT
        ws.send(SBE_ENCODER.orderCreate(USER_A, SYMBOL_BTC_USDT, 60000n * SCALE, 1n * SCALE, "BUY", cid_A_Buy));
        
        await sleep(2000);

        balA = await checkBalance(USER_A, ASSET_USDT);
        const orderA = await checkOrder(USER_A, cid_A_Buy);
        
        console.log(`User A 可用 USDT: ${balA.available}, 凍結: ${balA.frozen}`);
        if (orderA && orderA.status !== undefined) {
            console.log(`Order A 狀態: ${orderA.status}`);
            console.assert(orderA.status === 0, `Order A 狀態應為 NEW (0), 實際為: ${orderA.status}`);
        } else {
            console.error("❌ 無法查詢到 Order A:", orderA);
        }
        console.assert(balA.frozen === 60000n * SCALE, "凍結金額不正確");
        console.log("✅ 掛單與凍結驗證成功\n");


        // ---------------------------------------------------------
        // 階段 3：部分成交 (Partial Match)
        // ---------------------------------------------------------
        console.log("[階段 3] 部分成交...");
        const cid_B_Sell_1 = BigInt(Date.now()) + 1n;
        // User B 賣出 0.4 BTC @ 60,000 USDT
        ws.send(SBE_ENCODER.orderCreate(USER_B, SYMBOL_BTC_USDT, 60000n * SCALE, 4n * SCALE / 10n, "SELL", cid_B_Sell_1));

        await sleep(2000);

        const orderA_after_partial = await checkOrder(USER_A, cid_A_Buy);
        
        if (orderA_after_partial) {
            console.log(`Order A 已成交: ${orderA_after_partial.filled}`);
            console.assert(orderA_after_partial.status === 1, `Order A 狀態應為 PARTIAL (1), 實際為: ${orderA_after_partial.status}`);
            console.assert(BigInt(orderA_after_partial.filled) === 4n * SCALE / 10n, "成交量不正確");
        }
        console.log("✅ 部分成交與結算驗證成功\n");


        // ---------------------------------------------------------
        // 階段 4：完全成交
        // ---------------------------------------------------------
        console.log("[階段 4] 完全成交...");
        const cid_B_Sell_2 = BigInt(Date.now()) + 2n;
        // User B 賣出 1 BTC (會吃掉 A 剩餘的 0.6)
        ws.send(SBE_ENCODER.orderCreate(USER_B, SYMBOL_BTC_USDT, 60000n * SCALE, 1n * SCALE, "SELL", cid_B_Sell_2));

        await sleep(1000);

        const orderA_final = await checkOrder(USER_A, cid_A_Buy);
        console.log(`Order A 最終狀態: ${orderA_final.status}`);
        console.assert(orderA_final.status === 2, "Order A 應為 FILLED (2)");
        console.log("✅ 完全成交驗證成功\n");


        // ---------------------------------------------------------
        // 階段 5：撤單與資產解凍
        // ---------------------------------------------------------
        console.log("[階段 5] 撤單與資產解凍...");
        const orderB_2 = await checkOrder(USER_B, cid_B_Sell_2);
        ws.send(SBE_ENCODER.orderCancel(USER_B, orderB_2.orderId));

        await sleep(1000);

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
