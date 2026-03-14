import ws from 'k6/ws';
import { check } from 'k6';
import http from 'k6/http';

export const options = {
    vus: 50, // 模擬 50 個併發用戶
    duration: '1m', // 壓測持續 1 分鐘
};

const WS_URL = 'ws://localhost:8080/ws'; // 根據實際網關端口調整
const METRICS_URL = 'http://localhost:8081/metrics/tps'; // 根據撮合引擎端口調整

export default function () {
    const res = ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', function () {
            // 1. 先發送 Auth (目前系統要求)
            socket.send(JSON.stringify({
                op: "auth",
                uid: Math.floor(Math.random() * 10000) + 1
            }));

            // 2. 地毯式下單 (無 sleep 模式)
            socket.setInterval(function () {
                const side = Math.random() > 0.5 ? "BUY" : "SELL";
                const price = side === "BUY" ? 100 : 101; // 刻意製造大量成交
                socket.send(JSON.stringify({
                    op: "order_create",
                    sid: 1,
                    p: price,
                    q: 1,
                    side: side,
                    cid: Date.now() + Math.floor(Math.random() * 1000)
                }));
            }, 1); // 每 1ms 發送一筆，單個 VU 約 1000 TPS
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
