import ws from 'k6/ws';
import { check, sleep } from 'k6';
import http from 'k6/http';

export const options = {
    vus: 100, // 100 個並發連接
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

function createAuthBuffer(uid) {
    const buffer = new ArrayBuffer(36);
    const view = new DataView(buffer);
    view.setInt32(0, MSG_TYPE_AUTH, true); 
    view.setBigInt64(4, BigInt(-1), true); 
    view.setUint16(12, 16, true);
    view.setUint16(14, 103, true);
    view.setUint16(16, 1, true);
    view.setUint16(18, 0, true);
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
    view.setUint16(18, 0, true);
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
    view.setUint16(18, 0, true);
    view.setInt32(36, 1001, true); 
    view.setBigInt64(40, BigInt(60000) * SCALE, true); // 設定合理價格 60000
    view.setBigInt64(48, SCALE, true);   // 設定 1 BTC
    return { buffer, view };
}

export default function () {
    const uid = __VU + 1000;
    const authBuf = createAuthBuffer(uid);
    const depositBtcBuf = createDepositBuffer(uid, ASSET_BTC, 1000n * SCALE); // 充值 1000 BTC
    const depositUsdtBuf = createDepositBuffer(uid, ASSET_USDT, 1000000000n * SCALE); // 充值 10 億 USDT       
    const { buffer, view } = createPreallocatedOrderBuffer();
    let cidCounter = Date.now() * 1000 + (__VU * 10000000);

    const res = ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', function () {
            // 1. 認證
            socket.sendBinary(authBuf);

            // 2. 初始充值 (BTC & USDT)
            socket.sendBinary(depositBtcBuf);
            socket.sendBinary(depositUsdtBuf);

            // 3. 稍等 500ms 讓充值先入帳後開始下單
            socket.setTimeout(function () {
                socket.setInterval(function () {
                    const ts = BigInt(Date.now());
                    for (let i = 0; i < 125; i++) {
                        // BUY
                        view.setBigInt64(20, ts, true);
                        view.setBigInt64(28, BigInt(uid), true);
                        view.setUint8(56, 0); 
                        view.setBigInt64(57, BigInt(++cidCounter), true);
                        socket.sendBinary(buffer);

                        // SELL
                        view.setBigInt64(20, ts, true);
                        view.setBigInt64(28, BigInt(uid), true);
                        view.setUint8(56, 1);
                        view.setBigInt64(57, BigInt(++cidCounter), true);
                        socket.sendBinary(buffer);
                    }
                }, 5); 
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
            console.log(`[FINAL] 累計 Netty 接收: ${metrics.netty_recv_count || 0}`);
            console.log(`[FINAL] 累計引擎處理: ${metrics.engine_work_count || 0}`);
            console.log(`[FINAL] 引擎平均飽和度: ${metrics.engine_saturation || 'N/A'}`);
            console.log(`================================================`);
        } catch (e) {
            console.error(`解析監測數據失敗: ${e.message}`);
        }
    } else {
        console.error(`無法獲取監測數據，HTTP 狀態碼: ${res.status}`);
    }
}
