import ws from 'k6/ws';
import { check } from 'k6';
import http from 'k6/http';

export const options = {
    vus: 50, // 模擬 50 個併發用戶
    duration: '1m', // 壓測持續 1 分鐘
};

const WS_URL = 'ws://127.0.0.1:8080/ws/spot'; // 修正路徑為 /ws/spot
const METRICS_URL = 'http://127.0.0.1:8081/metrics/tps';

export default function () {
    const res = ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', function () {
            const uid = Math.floor(Math.random() * 10000) + 1; // 為每個 VU 生成固定 UID
            console.log(`VU ${__VU} connected with UID ${uid}!`); 

            // 1. 先發送 Auth
            socket.send(JSON.stringify({
                op: "auth",
                uid: uid
            }));

            // 2. 地毯式下單 (對沖模式：一買一賣確保平衡)
            socket.setInterval(function () {
                const price = 100;
                const cid = Date.now() * 1000 + Math.floor(Math.random() * 1000);
                
                // 發送買單
                socket.send(JSON.stringify({
                    op: "order_create",
                    uid: uid,
                    sid: 1001,
                    p: price,
                    q: 1,
                    side: "BUY",
                    cid: cid
                }));

                // 立即發送對應的賣單 (確保成交)
                socket.send(JSON.stringify({
                    op: "order_create",
                    uid: uid,
                    sid: 1001,
                    p: price,
                    q: 1,
                    side: "SELL",
                    cid: cid + 1
                }));
            }, 1); 
        });

        socket.on('close', () => console.log('WS connection closed'));
        socket.on('error', (e) => console.error('WS error: ', e.error()));
        
        // 運行一段時間後關閉
        socket.setTimeout(function () {
            socket.close();
        }, 55000);
    });

    check(res, { 'status is 101': (r) => r && r.status === 101 });
}

export function teardown() {
    // 壓測結束後從撮合引擎獲取最終計數
    const res = http.get(METRICS_URL);
    if (res.status === 200) {
        const data = JSON.parse(res.body);
        console.log(`================================================`);
        console.log(`最終累計成交數: ${data.total_matches}`);
        console.log(`預期單機極限壓測完成。`);
        console.log(`================================================`);
    }
}
