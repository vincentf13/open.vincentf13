# 現貨交易系統測試計畫 (Spot Exchange Test Plan) - 內存撮合版

## 1. 系統現狀分析 (System State)
- **核心架構**：純內存撮合 (In-memory Matching)，無資料庫落庫過程。
- **通訊協議**：基於 Aeron 的高性能消息傳遞與 WebSocket 下單入口。
- **目前瓶頸**：目前無成交回報 (Execution Report) 與外部可見指標 (Prometheus Metrics)。

---

## 2. 壓力測試計畫 (Stress Test Plan)

### 本機設備基準 (Acer Swift Go 16 SFG16-73)
*   **CPU**: Intel Core Ultra 9 285H (16 Cores / 16 Threads, Arrow Lake)
*   **RAM**: 32GB LPDDR5X (High-Speed Memory)
*   **SSD**: PCIe Gen4 NVMe (Ultra-fast Persistence)
*   **壓測目標 (單機理論極限)**:
    - **P-core 單核 TPS**: > 800,000 / sec (受惠於 Arrow Lake 無超執行緒設計，L2 快取專屬化)。
    - **端對端 WS 延遲**: < 5ms (P-core 間數據交換，跳過 E-core)。

### 目的
測出內存撮合引擎 (Matching Engine) 的最大處理極限 (L1/L2 Cache 飽和點)。

### 具體測試方法 (MVP)
由於目前無外部指標，採用 **「內存計數器 + Admin 接口」** 模式：
1. **核心分離策略 (Intel Arrow Lake)**：
   - **P-core 鎖定**: 透過 PowerShell Affinity 將 `spot-matching` 與 `spot-ws-api` 鎖定在不同的物理 P-cores (0-5)，徹底消除線程競爭。
   - **E-core 卸載**: 將 k6 與系統負擔卸載至 E-cores (6-15)。
2. **埋點實施**：
   - 在 `spot-matching` 的 `OrderBook.java` 中添加 `match_count` 原子計數器。
   - 每發生一次 `onMatch`，計數器遞增。
3. **暴露接口**：
   - 暴露一個 HTTP 監控端點 `/metrics/tps`，回傳累積成交數與時間戳。
4. **執行測量**：
   - 使用 k6 通過 WS 進行「地毯式壓測」。
   - **TPS 計算**：`(T1 時刻的累計成交數 - T0 時刻的累計成交數) / (T1 - T0)`。

### 監控判定
- **CPU 飽和**：撮合引擎通常佔用單核 100%，觀察該核的 CPU Usage。
- **內存延遲**：若 TPS 突然驟降，通常是觸發了 GC (Garbage Collection) 或 CPU Cache Miss 過多。

---

## 3. 可靠性測試計畫 (Reliability Test Plan)

### 目的
驗證內存數據與持久化進度在重啟後的一致性。

### 具體測試方法
1. **進度恢復 (Resume)**：
   - 殺掉 Matching Engine 並重啟。
   - 驗證 Engine 能從 `WalProgress` 加載最後處理的 Seq，並同步給 `AeronReceiver`。
   - 驗證 Aeron 鏈路能自動對齊並從斷點續傳。
