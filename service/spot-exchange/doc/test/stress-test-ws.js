import ws from 'k6/ws';
import { check } from 'k6';
import http from 'k6/http';

export const options = {
    vus: 200, 
    duration: '1m', 
};

const WS_URL = 'ws://127.0.0.1:8080/ws/spot';
const METRICS_URL = 'http://127.0.0.1:8082/api/test/metrics/saturation';

// SBE 常數
const MSG_TYPE_AUTH = 103;
const MSG_TYPE_ORDER_CREATE = 100;
const SBE_SCHEMA_ID = 1;
const SBE_VERSION = 0;

/**
 * 構造二進制 SBE 訊息 (AbstractSbeModel 佈局)
 * [0-3] Type | [4-11] Seq | [12-19] SBE Header | [20-...] Body
 */
function createSbeAuth(uid) {
    const buffer = new ArrayBuffer(20 + 16); // Header(20) + Body(16)
    const view = new DataView(buffer);
    
    // AbstractSbeModel Header
    view.setInt32(0, MSG_TYPE_AUTH, true); // MsgType
    view.setBigInt64(4, BigInt(-1), true); // Seq (-1)
    
    // SBE MessageHeader
    view.setUint16(12, 16, true);          // BlockLength
    view.setUint16(14, 103, true);         // TemplateId
    view.setUint16(16, SBE_SCHEMA_ID, true);
    view.setUint16(18, SBE_VERSION, true);
    
    // Auth Body
    view.setBigInt64(20, BigInt(Date.now()), true); // Timestamp
    view.setBigInt64(28, BigInt(uid), true);       // UserId
    
    return buffer;
}

function createSbeOrderCreate(uid, side, cid) {
    const buffer = new ArrayBuffer(20 + 45); // Header(20) + Body(45)
    const view = new DataView(buffer);
    
    // AbstractSbeModel Header
    view.setInt32(0, MSG_TYPE_ORDER_CREATE, true); 
    view.setBigInt64(4, BigInt(-1), true); 
    
    // SBE MessageHeader
    view.setUint16(12, 45, true);          // BlockLength
    view.setUint16(14, 100, true);         // TemplateId
    view.setUint16(16, SBE_SCHEMA_ID, true);
    view.setUint16(18, SBE_VERSION, true);
    
    // OrderCreate Body
    view.setBigInt64(20, BigInt(Date.now()), true); // Timestamp
    view.setBigInt64(28, BigInt(uid), true);       // UserId
    view.setInt32(36, 1001, true);                 // SymbolId (BTCUSDT)
    view.setBigInt64(40, BigInt(100), true);       // Price
    view.setBigInt64(48, BigInt(1), true);         // Qty
    view.setUint8(56, side);                       // Side (0=BUY, 1=SELL)
    view.setBigInt64(57, BigInt(cid), true);       // ClientOrderId
    
    return buffer;
}

export default function () {
    const res = ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', function () {
            const uid = Math.floor(Math.random() * 10000) + 1;
            
            // 1. 發送二進制 Auth
            socket.sendBinary(createSbeAuth(uid));

            // 2. 地毯式下單 (高壓版)
            socket.setInterval(function () {
                const baseCid = Date.now() * 1000 + Math.floor(Math.random() * 1000);
                
                // 批處理：單次循環發送 10 筆 (5買 5賣)
                for (let i = 0; i < 5; i++) {
                    socket.sendBinary(createSbeOrderCreate(uid, 0, baseCid + i*2));
                    socket.sendBinary(createSbeOrderCreate(uid, 1, baseCid + i*2 + 1));
                }
            }, 1); 
        });

        socket.on('error', (e) => console.error('WS error: ', e.error()));
        
        socket.setTimeout(function () {
            socket.close();
        }, 55000);
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
        console.log(`Netty 接收數: ${data.netty_recv_count}`);
        console.log(`================================================`);
    }
}
