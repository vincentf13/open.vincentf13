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
const MSG_TYPE_DEPOSIT = 102;
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

/** 預分配 Deposit Buffer */
function createDepositBuffer(uid, aid, amt) {
    const buffer = new ArrayBuffer(20 + 20);
    const view = new DataView(buffer);
    view.setInt32(0, MSG_TYPE_DEPOSIT, true); 
    view.setBigInt64(4, BigInt(-1), true); 
    view.setUint16(12, 20, true);
    view.setUint16(14, 102, true);
    view.setUint16(16, SBE_SCHEMA_ID, true);
    view.setUint16(18, SBE_VERSION, true);
    view.setBigInt64(20, BigInt(uid), true);
    view.setInt32(28, aid, true);
    view.setBigInt64(32, BigInt(amt), true);
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
    view.setBigInt64(48, BigInt(100), true);   // Qty (增加一點數量，讓撮合更穩定)
    return { buffer, view };
}

export default function () {
    const uid = __VU + 1000;
    const authBuf = createAuthBuffer(uid);
    // 充值 1 億單位 (8 位精度)
    const BIG_AMT = BigInt("10000000000000000"); 
    const btcDep  = createDepositBuffer(uid, 1, BIG_AMT); 
    const usdtDep = createDepositBuffer(uid, 2, BIG_AMT); 
    const { buffer, view } = createPreallocatedOrderBuffer();
    let cidCounter = Date.now() * 1000 + (__VU * 1000000);
    let batchCount = 0;

    const res = ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', function () {
            // 1. 認證
            socket.sendBinary(authBuf);
            // 2. 初始大額充值
            socket.sendBinary(btcDep);
            socket.sendBinary(usdtDep);

            // 3. 開始極速下單
            socket.setInterval(function () {
                const ts = BigInt(Date.now());
                
                // 每 100 輪補票一次，確保資產流通不斷裂
                if (++batchCount % 100 === 0) {
                    socket.sendBinary(btcDep);
                    socket.sendBinary(usdtDep);
                }

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
