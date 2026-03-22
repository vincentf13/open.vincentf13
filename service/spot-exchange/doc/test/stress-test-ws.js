import ws from 'k6/ws';
import { check } from 'k6';
import http from 'k6/http';

export const options = {
    vus: 40, // 大幅減少 VU，降低 OS 調度開銷
    duration: '1m',
    discardResponseBodies: true,
};

const WS_URL = 'ws://127.0.0.1:8080/ws/spot';
const METRICS_URL = 'http://127.0.0.1:8082/api/test/metrics/saturation';

const MSG_TYPE_AUTH = 103;
const MSG_TYPE_ORDER_CREATE = 100;

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
    view.setBigInt64(40, BigInt(100), true); 
    view.setBigInt64(48, BigInt(1), true);   
    return { buffer, view };
}

export default function () {
    const uid = __VU + 1000;
    const authBuf = createAuthBuffer(uid);
    const { buffer, view } = createPreallocatedOrderBuffer();
    let cidCounter = Date.now() * 1000 + (__VU * 10000000);

    const res = ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', function () {
            socket.sendBinary(authBuf);

            // --- 極限優化：飽和攻擊 (修正版) ---
            socket.setInterval(function () {
                const ts = BigInt(Date.now());
                // 調低每批次發送量，避免單機網路棧直接崩潰
                for (let i = 0; i < 10; i++) {
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
            }, 1); 
        });

        socket.on('error', (e) => console.error(`[VU ${__VU}] Error: ${e.error()}`));
        socket.setTimeout(function () { socket.close(); }, 58000);
    });

    check(res, { 'status is 101': (r) => r && r.status === 101 });
}

export function teardown() {
    const res = http.get(METRICS_URL);
    if (res.status === 200 && res.body) {
        try {
            const data = JSON.parse(res.body);
            // 處理可能存在的 data 封裝層
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
