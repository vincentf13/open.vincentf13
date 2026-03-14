# 現貨交易系統測試計畫 (Spot Exchange Test Plan) - 內存撮合版

## 1. 系統現狀分析 (System State)
- **核心架構**：純內存撮合 (In-memory Matching)，無資料庫落庫過程。
- **通訊協議**：基於 Aeron 的高性能消息傳遞與 WebSocket 下單入口。
- **目前瓶頸**：目前無成交回報 (Execution Report) 與外部可見指標 (Prometheus Metrics)。

---

## 2. 壓力測試計畫 (Stress Test Plan)

### 目的
測出內存撮合引擎 (Matching Engine) 的最大處理極限 (L1/L2 Cache 飽和點)。

### 具體測試方法 (MVP)
由於目前無外部指標，採用 **「內存計數器 + Admin 接口」** 模式：
1. **埋點實施**：
   - 在 `spot-matching` 的 `OrderBook.java` 中添加 `match_count` 原子計數器。
   - 每發生一次 `onMatch`，計數器遞增。
2. **暴露接口**：
   - 暴露一個 HTTP 監控端點，定時回傳累計成交數。
3. **執行測量**：
   - 使用 k6 通過 WS 進行「下單地毯式壓測」。
   - **TPS 計算**：`(T1 時刻的累計成交數 - T0 時刻的累計成交數) / (T1 - T0)`。

### 監控判定
- **CPU 飽和**：撮合引擎通常佔用單核 100%，觀察該核的 CPU Usage。
- **內存延遲**：若 TPS 突然驟降，通常是觸發了 GC (Garbage Collection) 或 CPU Cache Miss 過多。

---

## 3. 可靠性測試計畫 (Reliability Test Plan)

### 目的
驗證內存數據在崩潰重啟後的一致性。

### 具體測試方法 (Snapshot 驗證)
1. **快照一致性**：
   - 觸發 `SnapshotService` 生成內存鏡像。
   - 故障重啟後，比對快照載入後的 `orderIndex` 與 `priceLevelIndex` 數量是否與故障前一致。
2. **消息重放 (Replay)**：
   - 利用 Aeron 的 Archive 功能或上游消息日誌，在引擎重啟後重新載入指令，確認最終內存狀態完全匹配。
