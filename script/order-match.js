import http from 'k6/http';
import { check, sleep } from 'k6';

// k6 配置：10 個虛擬用戶 (VUs)，持續運行 30 秒
export const options = {
    vus: 10,
    duration: '30s',
};

// 買家與賣家的 Token
const BUYER_TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjLnAua2V2aW5mMTNAZ21haWwuY29tIiwidWlkIjo3NjEzNjUzMTcyMTg0MzcsImlzcyI6Im9wZW4udmluY2VudGYxMyIsImV4cCI6MTc3MTY3OTE1OSwidG9rZW5fdHlwZSI6IkFDQ0VTUyIsImlhdCI6MTc2OTA4NzE1OSwiYXV0aG9yaXRpZXMiOlsiUk9MRV9VU0VSIl0sImVtYWlsIjoiYy5wLmtldmluZjEzQGdtYWlsLmNvbSIsInNpZCI6IjgxYWUwNWI2LTdmMWItNDQzZi05NjAyLTVkZDZjZDEwNmMyZiJ9.W61yRT_PvMtvborNWb2g3rEwmBLg0Sf9l4iD_Po8du4';
const SELLER_TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjLnAua2V2aW5mMTMtMkBnbWFpbC5jb20iLCJ1aWQiOjc2MTM2NTM3MDMxNDg4NSwiaXNzIjoib3Blbi52aW5jZW50ZjEzIiwiZXhwIjoxNzcxNjgwNzg4LCJ0b2tlbl90eXBlIjoiQUNDRVNTIiwiaWF0IjoxNzY5MDg4Nzg4LCJhdXRob3JpdGllcyI6WyJST0xFX1VTRVIiXSwiZW1haWwiOiJjLnAua2V2aW5mMTMtMkBnbWFpbC5jb20iLCJzaWQiOiI2YzlkNTE4Ny0xNzExLTRmM2EtYTBlYi0zMzA1NjAyMDEyNmUifQ.3m_K4a7CZImUhPxa2oqDViuBCV3wrDKQr0cfaL47REo';

// 指令: kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 8080:80
const URL = 'http://localhost:8080/exchange/order/api/orders';

export default function () {
    const commonHeaders = {
        'Content-Type': 'application/json',
        'Accept': 'application/json, text/plain, */*',
    };

    const buyerHeaders = Object.assign({}, commonHeaders, { 'Authorization': `Bearer ${BUYER_TOKEN}` });
    const sellerHeaders = Object.assign({}, commonHeaders, { 'Authorization': `Bearer ${SELLER_TOKEN}` });

    const buyBody = JSON.stringify({
        instrumentId: 10001,
        side: 'BUY',
        type: 'LIMIT',
        quantity: 1000,
        price: 10
    });

    const sellBody = JSON.stringify({
        instrumentId: 10001,
        side: 'SELL',
        type: 'LIMIT',
        quantity: 1000,
        price: 10
    });

    // 使用 http.batch 同時發送買單與賣單，模擬併發
    let responses = http.batch([
        ['POST', URL, buyBody, { headers: buyerHeaders }],
        ['POST', URL, sellBody, { headers: sellerHeaders }]
    ]);

    // 檢查回應狀態
    check(responses[0], { 'BUY order status 200': (r) => r.status === 200 });
    check(responses[1], { 'SELL order status 200': (r) => r.status === 200 });

    // 每次迭代後休息 1 秒
    sleep(1);
}
