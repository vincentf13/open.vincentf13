
const API_URL = 'http://localhost:8082/api/test';

// 精度常數
const SCALE = 100000000n; 

// 測試用常數
const USER_A = 1001n; // 買家
const USER_B = 1002n; // 賣家
const ASSET_BTC = 1;
const ASSET_USDT = 2;

// 輔助函數：透過 HTTP 查詢引擎狀態
async function checkBalance(userId, assetId) {
    const url = `${API_URL}/balance?userId=${userId}&assetId=${assetId}`;
    try {
        const res = await fetch(url);
        if (!res.ok) {
            const text = await res.text();
            console.error(`查詢餘額失敗 (User: ${userId}, Asset: ${assetId}) - Status ${res.status}: ${text}`);
            return null;
        }
        const data = await res.json();
        return {
            available: BigInt(data.available),
            frozen: BigInt(data.frozen)
        };
    } catch (e) {
        console.error(`連線失敗: ${e.message}`);
        return null;
    }
}

async function runVerify() {
    console.log("=== 開始執行重啟後資產恢復驗證 ===\n");

    const balanceA = await checkBalance(USER_A, ASSET_USDT);
    const balanceB = await checkBalance(USER_B, ASSET_BTC);

    if (balanceA) {
        console.log(`User A (USDT) -> 可用: ${balanceA.available}, 凍結: ${balanceA.frozen}`);
        // 預期：根據 E2E 流程，最後 User A 應該成交了一些 BTC，剩下 USDT
        // 或者是如果整個流程跑完，A 買了 BTC，B 賣了 BTC
    } else {
        console.log("❌ 無法取得 User A 餘額");
    }

    if (balanceB) {
        console.log(`User B (BTC)  -> 可用: ${balanceB.available}, 凍結: ${balanceB.frozen}`);
    } else {
        console.log("❌ 無法取得 User B 餘額");
    }

    console.log("\n驗證完成。如果數值不為 0 且與測試後一致，代表 Chronicle 恢復正常。");
}

runVerify();
