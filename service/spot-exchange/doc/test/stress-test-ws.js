import ws from 'k6/ws';
import { check } from 'k6';
import http from 'k6/http';

export const options = {
    vus: 200, // 200 個並發連接
    duration: '5m', // 執行 5 分鐘
    discardResponseBodies: true,
};

const WS_URL = 'ws://127.0.0.1:8080/ws/spot';
const METRICS_URL = 'http://127.0.0.1:8082/api/test/metrics/saturation';

const MSG_TYPE_AUTH = 103;
const MSG_TYPE_DEPOSIT = 102;
const MSG_TYPE_ORDER_CREATE = 100;

const ASSET_BTC = 1;
const ASSET_USDT = 2;
const SCALE = 100000000n;
const ORDER_PRICE = 60000n * SCALE;
const ORDER_QTY = 1000n; // 0.00001 BTC，降低單筆資產消耗，避免長時間壓測打穿餘額
const ORDERS_PER_TICK = 250;

function createAuthBuffer(uid) {
    const buffer = new ArrayBuffer(36);
    const view = new DataView(buffer);
    view.setInt32(0, MSG_TYPE_AUTH, true); 
    view.setBigInt64(4, BigInt(-1), true); 
    view.setUint16(12, 16, true);
    view.setUint16(14, 103, true);
    view.setUint16(16, 1, true);
    view.setUint16(18, 1, true);
    view.setBigInt64(20, BigInt(Date.now()), true);
    view.setBigInt64(28, BigInt(uid), true);
    return buffer;
}

function createDepositBuffer(uid, assetId, amount) {
    const buffer = new ArrayBuffer(20 + 28);
    const view = new DataView(buffer);
    view.setInt32(0, MSG_TYPE_DEPOSIT, true);
    view.setBigInt64(4, BigInt(-1), true);
    view.setUint16(12, 28, true);
    view.setUint16(14, 102, true);
    view.setUint16(16, 1, true);
    view.setUint16(18, 1, true);
    view.setBigInt64(20, BigInt(Date.now()), true);
    view.setBigInt64(28, BigInt(uid), true);
    view.setInt32(36, assetId, true);
    view.setBigInt64(40, BigInt(amount), true);
    return buffer;
}

function createPreallocatedOrderBuffer() {
    const buffer = new ArrayBuffer(65);
    const view = new DataView(buffer);
    view.setInt32(0, MSG_TYPE_ORDER_CREATE, true); 
    view.setBigInt64(4, BigInt(-1), true); 
    view.setUint16(12, 45, true);
    view.setUint16(14, 100, true);
    view.setUint16(16, 1, true);
    view.setUint16(18, 1, true);
    view.setInt32(36, 1001, true); 
    view.setBigInt64(40, ORDER_PRICE, true);
    view.setBigInt64(48, ORDER_QTY, true);
    return { buffer, view };
}

export default function () {
    const uid = __VU + 1000;
    const isBuyer = (__VU % 2) === 0;
    const authBuf = createAuthBuffer(uid);
    const depositBtcBuf = createDepositBuffer(uid, ASSET_BTC, 1000n * SCALE);
    const depositUsdtBuf = createDepositBuffer(uid, ASSET_USDT, 1000000000n * SCALE);
    
    const { buffer, view } = createPreallocatedOrderBuffer();
    let cidCounter = Date.now() * 1000 + (__VU * 10000000);

    const res = ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', function () {
            // 1. 認證
            socket.sendBinary(authBuf);

            // 2. 初始充值
            socket.sendBinary(depositBtcBuf);
            socket.sendBinary(depositUsdtBuf);

            // 3. 稍等 500ms 讓充值先入帳後開始下單
            socket.setTimeout(function () {
                socket.setInterval(function () {
                    const ts = BigInt(Date.now());
                    for (let i = 0; i < ORDERS_PER_TICK; i++) { 
                        view.setBigInt64(20, ts, true);
                        view.setBigInt64(28, BigInt(uid), true);
                        view.setUint8(56, isBuyer ? 0 : 1);
                        view.setBigInt64(57, BigInt(++cidCounter), true);
                        socket.sendBinary(buffer);
                    }
                }, 1); // 每 1ms 觸發一次
            }, 500);
            
            // 讓這個連接保持存活 295 秒
            socket.setTimeout(function () {
                socket.close();
            }, 295000);
        });

        socket.on('error', (e) => console.error(`[VU ${__VU}] Error: ${e.error()}`));
        socket.on('close', () => console.log(`[VU ${__VU}] Connection closed`));
    });

    check(res, { 'status is 101': (r) => r && r.status === 101 });
}

export function teardown() {
    const res = http.get(METRICS_URL);
    if (res.status === 200 && res.body) {
        try {
            const data = JSON.parse(res.body);
            const metrics = data.data || data;
            console.log(`================================================`);
            console.log(`[FINAL] 累計 Netty 接收: ${metrics.netty_recv || 0}`);
            console.log(`[FINAL] 累計 Gateway WAL 寫入: ${metrics.wal_write || 0}`);
            console.log(`================================================`);
        } catch (e) {
            console.error(`解析監測數據失敗: ${e.message}`);
        }
    } else {
        console.error(`無法獲取監測數據，HTTP 狀態碼: ${res.status}`);
    }
}
