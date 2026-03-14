# Spot Exchange Architecture Design (TPS Benchmark Edition)

## 1. 核心理念 (Core Philosophy)
本系統目前處於 **高性能 TPS 極限測試原型階段**。為了驗證 Aeron + SBE + Chronicle 在現貨撮合場景下的理論性能上限，我們對系統進行了大幅簡化，移除了所有非核心的 I/O 與網絡瓶頸。

### 關鍵優化策略：
*   **全路徑零拷貝 (Zero-Copy)**：利用 SBE 模型作為堆外內存視圖 (Flyweight)，從指令解析到核心處理實現 100% 內存映射，無字節拷貝。
*   **回報鏈路裁剪**：移除了從「撮合引擎」到「網關」再到「前端」的回報傳輸鏈路。撮合結果僅通過日誌 (ExecutionReporter) 輸出，以排除網絡 I/O 帶寬對 TPS 的干擾。
*   **去 ThreadLocal 化**：模型層完全封裝自己的 Buffer 與工具，消除了執行緒局部查找開銷。

## 2. 消息流轉路徑 (Message Flow)
目前的測試路徑如下：
`WebSocket (JSON)` -> `网關 WAL (Chronicle Queue)` -> `Aeron (UDP/IPC)` -> `引擎 WAL (Chronicle Queue)` -> `Matching Engine (Core)` -> `Execution Log (Slf4j)`

## 3. 堆外內存佈局 (Off-Heap Layout)
所有消息統一採用以下 20 字節固定頭部佈局：
*   `[0-3]`   MsgType (4 bytes)
*   `[4-11]`  Sequence (8 bytes)
*   `[12-19]` SBE Header (8 bytes)
*   `[20-...]` SBE Payload

## 4. 測試目標 (Benchmark Goals)
*   驗證在 **單機單線程** 撮合下，系統能否突破 **1,000,000 TPS**。
*   觀測在極高壓力下，Aeron 的背壓 (Back-pressure) 行為與 Chronicle Queue 的磁碟寫入延遲。
