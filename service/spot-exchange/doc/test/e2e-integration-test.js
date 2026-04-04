/**
 * 現貨交易系統 E2E 整合測試 (Node.js - SBE 二進制版)
 *
 * 測試完整訂單生命週期：Auth → Deposit → Place Order → Partial Fill → Full Fill → Cancel
 *
 * 執行前準備:
 * 1. 清除舊數據: Remove-Item C:\iProject\open.vincentf13\data\spot-exchange -Recurse -Force
 * 2. 啟動 spot-matching 與 spot-ws-api
 * 3. npm install ws
 * 4. node e2e-integration-test.js
 */
const WebSocket = require('ws');

const WS_URL = 'ws://localhost:8080/ws/spot';
const API_URL = 'http://localhost:8082/api/test';

// ========== 系統常數 ==========
const SCALE = 100000000n;                     // 10^8 精度 (1 BTC = 1_00000000)
const USER_A = 1001n;                         // 買家
const USER_B = 1002n;                         // 賣家
const SYMBOL_BTC_USDT = 1001;
const ASSET_BTC = 1;
const ASSET_USDT = 2;

// 訂單狀態碼
const STATUS = { NEW: 0, PARTIALLY_FILLED: 1, FILLED: 2, CANCELED: 3 };

// SBE 協定常數
const MSG_TYPE = { ORDER_CREATE: 100, ORDER_CANCEL: 101, DEPOSIT: 102, AUTH: 103 };
const SBE_SCHEMA_ID = 1;
const SBE_VERSION = 1;

// ========== 工具函數 ==========
const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));
let passed = 0, failed = 0;

function assert(condition, expected, actual, description) {
    if (condition) {
        passed++;
    } else {
        failed++;
        console.error(`  FAIL: ${description} (expected: ${expected}, actual: ${actual})`);
    }
}

async function queryBalance(userId, assetId) {
    const res = await fetch(`${API_URL}/balance?userId=${userId}&assetId=${assetId}`);
    if (!res.ok) throw new Error(`查詢餘額失敗 (${res.status})`);
    const data = await res.json();
    return { available: BigInt(data.available), frozen: BigInt(data.frozen) };
}

async function queryOrder(userId, cid) {
    const res = await fetch(`${API_URL}/order_by_cid?userId=${userId}&cid=${cid}`);
    if (!res.ok) throw new Error(`查詢訂單失敗 (${res.status})`);
    const text = await res.text();
    return text ? JSON.parse(text) : null;
}

// ========== SBE 二進制編碼器 ==========
// 客戶端幀佈局: [0-3] MsgType | [4-11] Seq(unused) | [12-19] SBE Header | [20+] SBE Body
function sbeHeader(view, msgType, blockLength) {
    view.setInt32(0, msgType, true);
    view.setBigInt64(4, -1n, true);
    view.setUint16(12, blockLength, true);
    view.setUint16(14, msgType, true);
    view.setUint16(16, SBE_SCHEMA_ID, true);
    view.setUint16(18, SBE_VERSION, true);
}

const encode = {
    auth: (uid) => {
        const buf = new Uint8Array(36); const v = new DataView(buf.buffer);
        sbeHeader(v, MSG_TYPE.AUTH, 16);
        v.setBigInt64(20, BigInt(Date.now()), true);   // timestamp
        v.setBigInt64(28, BigInt(uid), true);           // userId
        return buf;
    },
    deposit: (uid, assetId, amount) => {
        const buf = new Uint8Array(48); const v = new DataView(buf.buffer);
        sbeHeader(v, MSG_TYPE.DEPOSIT, 28);
        v.setBigInt64(20, BigInt(Date.now()), true);   // timestamp
        v.setBigInt64(28, BigInt(uid), true);           // userId
        v.setInt32(36, assetId, true);                  // assetId
        v.setBigInt64(40, BigInt(amount), true);        // amount
        return buf;
    },
    orderCreate: (uid, symbolId, price, qty, side, clientOrderId) => {
        const buf = new Uint8Array(65); const v = new DataView(buf.buffer);
        sbeHeader(v, MSG_TYPE.ORDER_CREATE, 45);
        v.setBigInt64(20, BigInt(Date.now()), true);   // timestamp
        v.setBigInt64(28, BigInt(uid), true);           // userId
        v.setInt32(36, symbolId, true);                 // symbolId
        v.setBigInt64(40, BigInt(price), true);         // price
        v.setBigInt64(48, BigInt(qty), true);           // qty
        v.setUint8(56, side === 'BUY' ? 0 : 1);        // side (BUY=0, SELL=1)
        v.setBigInt64(57, BigInt(clientOrderId), true); // clientOrderId
        return buf;
    },
    orderCancel: (uid, orderId) => {
        const buf = new Uint8Array(44); const v = new DataView(buf.buffer);
        sbeHeader(v, MSG_TYPE.ORDER_CANCEL, 24);
        v.setBigInt64(20, BigInt(Date.now()), true);   // timestamp
        v.setBigInt64(28, BigInt(uid), true);           // userId
        v.setBigInt64(36, BigInt(orderId), true);       // orderId
        return buf;
    }
};

// ========== 測試主流程 ==========
console.log(`連線至 ${WS_URL} ...`);
const ws = new WebSocket(WS_URL);

const timeout = setTimeout(() => {
    if (ws.readyState !== WebSocket.OPEN) {
        console.error('連線超時，請檢查 spot-ws-api 是否啟動於 8080');
        process.exit(1);
    }
}, 5000);

ws.on('open', async () => {
    clearTimeout(timeout);
    console.log('WebSocket 連線成功\n=== E2E 整合測試開始 ===\n');

    try {
        // =============================================================
        // 階段 1: 認證與充值
        // 操作: User A 與 User B 各自認證，A 充值 100,000 USDT，B 充值 10 BTC
        // 預期: A 的 USDT 可用餘額 = 100,000 USDT；B 的 BTC 可用餘額 = 10 BTC
        // =============================================================
        console.log('[階段 1] 認證與充值');
        console.log('  操作: User A 認證 + 充值 100,000 USDT；User B 認證 + 充值 10 BTC');
        ws.send(encode.auth(USER_A));
        ws.send(encode.auth(USER_B));
        ws.send(encode.deposit(USER_A, ASSET_USDT, 100000n * SCALE));
        ws.send(encode.deposit(USER_B, ASSET_BTC, 10n * SCALE));
        await sleep(2000);

        const bal1A = await queryBalance(USER_A, ASSET_USDT);
        const bal1B = await queryBalance(USER_B, ASSET_BTC);
        console.log(`  結果: A USDT available=${bal1A.available / SCALE} | B BTC available=${bal1B.available / SCALE}`);
        assert(bal1A.available === 100000n * SCALE, '100000', String(bal1A.available / SCALE), 'A USDT 餘額');
        assert(bal1B.available === 10n * SCALE, '10', String(bal1B.available / SCALE), 'B BTC 餘額');
        console.log('  PASS\n');

        // =============================================================
        // 階段 2: 掛單與資產凍結
        // 操作: User A 限價買入 1 BTC @ 60,000 USDT (無對手盤，掛單)
        // 預期: 訂單狀態 = NEW(0)，A 凍結 60,000 USDT，可用減少 60,000
        // 意義: 買單需凍結 price × qty 的報價資產，防止超額下單
        // =============================================================
        console.log('[階段 2] 掛單與資產凍結');
        const cidBuy = BigInt(Date.now());
        console.log(`  操作: A 買入 1 BTC @ 60,000 USDT (clientOrderId=${cidBuy})`);
        ws.send(encode.orderCreate(USER_A, SYMBOL_BTC_USDT, 60000n * SCALE, 1n * SCALE, 'BUY', cidBuy));
        await sleep(2000);

        const bal2A = await queryBalance(USER_A, ASSET_USDT);
        const order2A = await queryOrder(USER_A, cidBuy);
        console.log(`  結果: A USDT available=${bal2A.available / SCALE}, frozen=${bal2A.frozen / SCALE} | Order status=${order2A?.status} (${order2A?.status === STATUS.NEW ? 'NEW' : 'unexpected'})`);
        assert(order2A?.status === STATUS.NEW, 'NEW(0)', order2A?.status, 'Order A 狀態');
        assert(bal2A.frozen === 60000n * SCALE, '60000', String(bal2A.frozen / SCALE), 'A USDT 凍結');
        assert(bal2A.available === 40000n * SCALE, '40000', String(bal2A.available / SCALE), 'A USDT 可用');
        console.log('  PASS\n');

        // =============================================================
        // 階段 3: 部分成交
        // 操作: User B 限價賣出 0.4 BTC @ 60,000 USDT (與 A 的買單撮合)
        // 預期: A 的買單部分成交 0.4 BTC，狀態 = PARTIALLY_FILLED(1)
        // 意義: 價格優先-時間優先撮合，A 獲得 0.4 BTC，B 獲得 24,000 USDT
        // =============================================================
        console.log('[階段 3] 部分成交');
        const cidSell1 = BigInt(Date.now()) + 1n;
        console.log(`  操作: B 賣出 0.4 BTC @ 60,000 USDT (clientOrderId=${cidSell1})`);
        ws.send(encode.orderCreate(USER_B, SYMBOL_BTC_USDT, 60000n * SCALE, 4n * SCALE / 10n, 'SELL', cidSell1));
        await sleep(2000);

        const order3A = await queryOrder(USER_A, cidBuy);
        const filledQty = BigInt(order3A?.filled || 0);
        console.log(`  結果: Order A filled=${filledQty / SCALE} BTC, status=${order3A?.status} (${order3A?.status === STATUS.PARTIALLY_FILLED ? 'PARTIALLY_FILLED' : 'unexpected'})`);
        assert(order3A?.status === STATUS.PARTIALLY_FILLED, 'PARTIALLY_FILLED(1)', order3A?.status, 'Order A 狀態');
        assert(filledQty === 4n * SCALE / 10n, '0.4', String(filledQty / SCALE), 'Order A 成交量');
        console.log('  PASS\n');

        // =============================================================
        // 階段 4: 完全成交
        // 操作: User B 限價賣出 1 BTC @ 60,000 USDT
        //       其中 0.6 BTC 與 A 的剩餘買單撮合，0.4 BTC 掛到賣簿
        // 預期: A 的買單完全成交，狀態 = FILLED(2)
        // 意義: 買單全部成交後自動移出訂單簿，凍結的 USDT 按成交價結算
        // =============================================================
        console.log('[階段 4] 完全成交');
        const cidSell2 = BigInt(Date.now()) + 2n;
        console.log(`  操作: B 賣出 1 BTC @ 60,000 USDT (0.6 撮合 A 剩餘，0.4 掛賣簿)`);
        ws.send(encode.orderCreate(USER_B, SYMBOL_BTC_USDT, 60000n * SCALE, 1n * SCALE, 'SELL', cidSell2));
        await sleep(2000);

        const order4A = await queryOrder(USER_A, cidBuy);
        console.log(`  結果: Order A status=${order4A?.status} (${order4A?.status === STATUS.FILLED ? 'FILLED' : 'unexpected'})`);
        assert(order4A?.status === STATUS.FILLED, 'FILLED(2)', order4A?.status, 'Order A 最終狀態');
        console.log('  PASS\n');

        // =============================================================
        // 階段 5: 撤單與資產解凍
        // 操作: User B 撤銷階段 4 掛在賣簿上剩餘的 0.4 BTC 賣單
        // 預期: 訂單狀態 = CANCELED(3)，凍結的 0.4 BTC 歸還 B 的可用餘額
        // 意義: 撤單必須解凍所有剩餘凍結資產，防止資產永久鎖死
        // =============================================================
        console.log('[階段 5] 撤單與資產解凍');
        const orderB2 = await queryOrder(USER_B, cidSell2);
        if (!orderB2?.orderId) throw new Error('找不到 B 的掛單 (可能已全部成交)');
        console.log(`  操作: B 撤銷賣單 orderId=${orderB2.orderId} (剩餘 0.4 BTC)`);
        ws.send(encode.orderCancel(USER_B, orderB2.orderId));
        await sleep(2000);

        const orderB2Canceled = await queryOrder(USER_B, cidSell2);
        console.log(`  結果: Order B status=${orderB2Canceled?.status} (${orderB2Canceled?.status === STATUS.CANCELED ? 'CANCELED' : 'unexpected'})`);
        assert(orderB2Canceled?.status === STATUS.CANCELED, 'CANCELED(3)', orderB2Canceled?.status, 'B 撤單狀態');
        console.log('  PASS\n');

        // =============================================================
        // 最終結算驗證
        // 成交明細: A 買 1 BTC @ 60,000 = 支出 60,000 USDT，獲得 1 BTC
        //           B 賣 1 BTC @ 60,000 = 獲得 60,000 USDT (扣微量手續費)，支出 1 BTC
        // =============================================================
        console.log('[最終結算] 驗證資產餘額一致性');
        const finalA_USDT = await queryBalance(USER_A, ASSET_USDT);
        const finalA_BTC  = await queryBalance(USER_A, ASSET_BTC);
        const finalB_USDT = await queryBalance(USER_B, ASSET_USDT);
        const finalB_BTC  = await queryBalance(USER_B, ASSET_BTC);
        console.log(`  A: ${finalA_USDT.available / SCALE} USDT + ${finalA_BTC.available / SCALE} BTC`);
        console.log(`  B: ${finalB_USDT.available / SCALE} USDT + ${finalB_BTC.available / SCALE} BTC`);
        assert(finalA_BTC.available === 1n * SCALE, '1', String(finalA_BTC.available / SCALE), 'A 最終 BTC');
        assert(finalA_USDT.available === 40000n * SCALE, '40000', String(finalA_USDT.available / SCALE), 'A 最終 USDT');
        assert(finalA_USDT.frozen === 0n, '0', String(finalA_USDT.frozen / SCALE), 'A USDT 凍結歸零');
        assert(finalB_BTC.frozen === 0n, '0', String(finalB_BTC.frozen / SCALE), 'B BTC 凍結歸零');
        console.log('  PASS\n');

        // 總結
        console.log(`=== 測試完成: ${passed} passed, ${failed} failed ===`);
        if (failed === 0) console.log('所有測試通過');
        else process.exitCode = 1;

    } catch (err) {
        console.error('測試異常:', err);
        process.exitCode = 1;
    } finally {
        ws.close();
    }
});

ws.on('error', (err) => console.error('WS 錯誤:', err));
