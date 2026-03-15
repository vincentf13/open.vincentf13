# 現貨交易系統 E2E 整合測試指南

## 1. 測試目的與範圍
本測試旨在驗證現貨交易系統的 **「端到端 (End-to-End) 業務邏輯」**。
測試涵蓋了從 WebSocket 網關接入，經過 Aeron 傳輸，到內存撮合引擎處理，最後透過 HTTP 接口驗證狀態的完整生命週期。

### 驗證的五大核心場景：
1. **認證與充值 (Auth & Deposit)**：登入多個帳戶並進行初始資金注入。
2. **掛單與凍結 (Maker Order)**：驗證下單後，系統是否正確凍結了對應的資產（如買單凍結 Quote Asset，賣單凍結 Base Asset）。
3. **部分成交 (Partial Match)**：驗證當 Taker 訂單小於 Maker 訂單時，是否能正確觸發部分成交，並精確結算雙方資產。
4. **完全成交 (Full Match)**：驗證當 Taker 訂單大於 Maker 剩餘數量時，Maker 訂單是否轉為 `FILLED`，且 Taker 產生對應的殘餘掛單。
5. **撤單與解凍 (Cancel Order)**：驗證撤單指令是否能正確將訂單狀態改為 `CANCELED`，並將剩餘未成交的凍結資產退回為可用餘額。

---

## 2. 測試環境要求
*   **Java 服務**：必須同時啟動 `spot-matching` 與 `spot-ws-api` 兩個 Spring Boot 應用程式。
    *   `spot-ws-api` 提供 WebSocket 與 HTTP 監控端口：`8081`
    *   `spot-matching` 專注於高效能撮合（無 Web 端口）
*   **Node.js 環境**：需要安裝 Node.js (建議 v18+) 用於執行測試腳本。

---

## 3. 執行測試步驟

### 步驟 1：啟動後端服務
請在 IDE 中或透過命令列啟動以下兩個服務，並等待它們完全啟動。
- 啟動 `MatchingApp` (spot-matching)
- 啟動 `WsApiApp` (spot-ws-api)

### 步驟 2：準備 Node.js 環境
開啟命令列終端機，切換到存放測試腳本的目錄：
```bash
cd service/spot-exchange/doc/test
```

安裝腳本所需的 `ws` (WebSocket) 依賴：
```bash
npm install ws
```

### 步驟 3：執行測試腳本
運行 E2E 測試腳本：
```bash
node e2e-integration-test.js
```

### 4. 預期輸出結果
如果系統所有邏輯正常，終端機將會依序輸出每個階段的驗證結果：

```text
=== 開始執行現貨系統 E2E 整合測試 ===

[階段 1] 登入與充值...
✅ 充值驗證成功

[階段 2] 掛單與資產凍結...
✅ 掛單與凍結驗證成功

[階段 3] 部分成交...
✅ 部分成交與結算驗證成功

[階段 4] 完全成交...
✅ 完全成交驗證成功

[階段 5] 撤單與資產解凍...
✅ 撤單與解凍驗證成功

🎉 所有 E2E 整合測試情境通過！
```
若有任何階段的斷言 (Assert) 失敗，腳本會拋出錯誤並中斷，提示你具體是哪個資產數字或訂單狀態不符合預期。
