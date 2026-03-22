import ws from 'k6/ws';
import { check, sleep } from 'k6';
import http from 'k6/http';

export const options = {
    vus: 500, // 提升至 500 並發
    duration: '1m',
    discardResponseBodies: true, // 減少內存壓力
};

const WS_URL = 'ws://127.0.0.1:8080/ws/spot';
const METRICS_URL = 'http://127.0.0.1:8082/api/test/metrics/saturation';

// SBE 常數與預分配
const MSG_TYPE_AUTH = 103;
const MSG_TYPE_ORDER_CREATE = 100;
const SBE_SCHEMA_ID = 1;
const SBE_VERSION = 0;

/** 構造預分配的 Buffer 以減少 GC */
function createPreallocatedOrderBuffer() {
    const buffer = new ArrayBuffer(20 + 45);
    const view = new DataView(buffer);
    view.setInt32(0, MSG_TYPE_ORDER_CREATE, true); 
    view.setBigInt64(4, BigInt(-1), true); 
    view.setUint16(12, 45, true);
    view.setUint16(14, 100, true);
    view.setUint16(16, SBE_SCHEMA_ID, true);
    view.setUint16(18, SBE_VERSION, true);
    view.setInt32(36, 1001, true); // SymbolId
    view.setBigInt64(40, BigInt(100), true); // Price
    view.setBigInt64(48, BigInt(1), true);   // Qty
    return { buffer, view };
}

export default function () {
    const { buffer, view } = createPreallocatedOrderBuffer();
    const uid = __VU; // 每個 VU 使用獨立 UID 減少撮合端熱點
    let cidCounter = Date.now() * 1000;

    const res = ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', function () {
            // 1. 發送 Auth (省略，直接進入下單)
            
            // 2. 極速連發邏輯
            socket.setInterval(function () {
                // 每 1ms 爆發發送 50 筆，達成單 VU 5萬 TPS 的理論潛力
                for (let i = 0; i < 25; i++) {
                    const ts = BigInt(Date.now());
                    
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

        socket.setTimeout(function () { socket.close(); }, 58000);
    });

    check(res, { 'status is 101': (r) => r && r.status === 101 });
}

export function teardown() {
    const res = http.get(METRICS_URL);
    if (res.status === 200) {
        const data = JSON.parse(res.body);
        console.log(`================================================`);
        console.log(`最終平均 TPS: ${data.average_tps}`);
        console.log(`引擎飽和度: ${data.engine_saturation}`);
        console.log(`================================================`);
    }
}
