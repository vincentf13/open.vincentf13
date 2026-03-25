import ws from 'k6/ws';
import { check } from 'k6';
import http from 'k6/http';

export const options = {
    vus: 500, 
    duration: '1m',
    discardResponseBodies: true,
};

const WS_URL = 'ws://127.0.0.1:8080/ws/spot';
const METRICS_URL = 'http://127.0.0.1:8082/api/test/metrics/saturation';

const MSG_TYPE_AUTH = 103;
const MSG_TYPE_ORDER_CREATE = 100;
const SBE_SCHEMA_ID = 1;
const SBE_VERSION = 0;

/** 預分配 Auth Buffer */
function createAuthBuffer(uid) {
    const buffer = new ArrayBuffer(20 + 16);
    const view = new DataView(buffer);
    view.setInt32(0, MSG_TYPE_AUTH, true); 
    view.setBigInt64(4, BigInt(-1), true); 
    view.setUint16(12, 16, true);          // BlockLength
    view.setUint16(14, 103, true);         // TemplateId
    view.setUint16(16, SBE_SCHEMA_ID, true);
    view.setUint16(18, SBE_VERSION, true);
    view.setBigInt64(20, BigInt(Date.now()), true);
    view.setBigInt64(28, BigInt(uid), true);
    return buffer;
}

/** 預分配 Order Buffer */
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
    const uid = __VU + 1000;
    const authBuf = createAuthBuffer(uid);
    const { buffer, view } = createPreallocatedOrderBuffer();
    let cidCounter = Date.now() * 1000 + (__VU * 1000000);

    const res = ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', function () {
            // 1. 必須發送 Auth
            socket.sendBinary(authBuf);

            // 2. 開始極速下單
            socket.setInterval(function () {
                const ts = BigInt(Date.now());
                for (let i = 0; i < 20; i++) {
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

        socket.on('error', (e) => console.error(`[VU ${__VU}] WS Error: ${e.error()}`));
        socket.setTimeout(function () { socket.close(); }, 58000);
    });

    check(res, { 'status is 101': (r) => r && r.status === 101 });
}

export function teardown() {
    const res = http.get(METRICS_URL);
    if (res.status === 200) {
        const data = JSON.parse(res.body);
        console.log(`================================================`);
        console.log(`[TEARDOWN] Netty Recv: ${data.netty_recv_count}`);
        console.log(`[TEARDOWN] Gateway WAL Write: ${data.gateway_wal_write_count}`);
        console.log(`================================================`);
    }
}
