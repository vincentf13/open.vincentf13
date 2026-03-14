import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

// 自定義指標：發送訂單總數
const sentOrders = new Counter('sent_orders_total');

export const options = {
    // 單機建議：低 VU (虛擬用戶)，高頻率發送，減少 TCP 連線開銷
    vus: 50, 
    duration: '1m',
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
};

const url = 'ws://localhost:8080/ws/v1/order'; // 請根據實際 WS 端口修改

export default function () {
    const res = ws.connect(url, {}, function (socket) {
        socket.on('open', () => {
            console.log('Connected to WebSocket');

            // 核心策略：利用 setInterval 在單個連線中進行高頻下單 (每 2ms 一次)
            // 這比建立數萬個連線更能壓榨單機 CPU 效能
            socket.setInterval(() => {
                const order = JSON.stringify({
                    symbol: 'BTCUSDT',
                    side: 'BUY',
                    type: 'LIMIT',
                    price: 60000 + Math.floor(Math.random() * 100), // 隨機價位避免全部重疊
                    qty: 0.1,
                    cid: `cid-${Date.now()}-${Math.random()}`
                });

                socket.send(order);
                sentOrders.add(1);
            }, 2); // 2ms 延遲 = 單個連線 500 TPS，50 個連線理論可達 25,000 TPS
        });

        socket.on('close', () => console.log('Disconnected'));
        socket.on('error', (e) => console.log('WS Error: ', e.error()));

        // 壓力測試持續時間
        socket.setTimeout(() => {
            socket.close();
        }, 60000);
    });

    check(res, { 'status is 101': (r) => r && r.status === 101 });
}
