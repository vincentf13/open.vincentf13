# Spot Exchange Architecture Design (Reliable Benchmark Edition)

## 1. 核心理念 (Core Philosophy)
本系統旨在實現 **極致 TPS 的同時，保證數據的絕對可靠性與最終一致性**。通過結合 Aeron 的高性能傳輸與 Chronicle Queue 的持久化能力，我們構建了一個全鏈路零拷貝的分布式撮合系統。

### 關鍵技術棧：
*   **Aeron (UDP/IPC)**：負責節點間的高可靠、低延遲數據傳輸。
*   **SBE (Simple Binary Encoding)**：極速二進制序列化協議。
*   **Chronicle Queue (WAL)**：提供納秒級的磁碟持久化與重放能力。

## 2. 可靠性保障機制 (Reliability & Consistency)
*   **握手與自愈 (Handshake & Resume)**：發送端與接收端通過控制流維持連接狀態。接收端會定期發送 `RESUME` 信號，告知其當前已處理的最後位點。一旦鏈路中斷或發現跳號，發送端會自動從指定位點執行 WAL 跳轉，補齊數據。
*   **全路徑零拷貝 (Zero-Copy)**：利用 SBE 模型作為堆外內存視圖 (Flyweight)，在數據流轉（WS -> WAL -> Aeron -> Matching）過程中完全避免字節拷貝，最大化 CPU 指令週期利用率。
*   **冪等與連續性檢查**：接收端在落地 WAL 前會對每條消息的 Sequence 進行嚴格校驗，確保不丟、不重、不亂序。

## 3. 消息流轉路徑 (Message Flow)
`WebSocket (JSON)` -> `网關 WAL` -> `Aeron Data Stream` -> `引擎 WAL` -> `Matching Engine (Core)` -> `Execution Log`

## 4. 堆外內存佈局 (Off-Heap Layout)
統一 20 字節固定頭部佈局，實現 Aeron 傳輸與 WAL 存儲格式的完美對齊：
*   `[0-3]`   MsgType (4 bytes)
*   `[4-11]`  Sequence (8 bytes) - 與 WAL Index 同步
*   `[12-19]` SBE Header (8 bytes)
*   `[20-...]` SBE Payload
