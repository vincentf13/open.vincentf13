# Open Exchange Core - 技術展示系列 Ep.1：系統架構與 SDK 設計

**影片長度：** 約 2 分鐘
**核心亮點：** 事件驅動架構 (EDA)、內存優先 (Memory-First)、單執行緒模型、SDK 治理

---

## 1. 系統架構總覽 (System Architecture Overview)

**目標：** 展示系統如何透過「非同步事件驅動」與「內存運算」來突破傳統關聯式資料庫的效能瓶頸。

| 時間   | 畫面 (Visual)                                                                                                                                                                                             | 旁白腳本 (Audio)                                                                                                 | 執行建議                            |
| :--- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | :----------------------------------------------------------------------------------------------------------- | :------------------------------ |
| 0:00 | **[動態全景架構圖]**<br>核心不是 DB，而是 **Kafka** (Message Bus)。<br>服務圍繞在 Kafka 周圍：<br>- Upstream: Gateway, Order<br>- Core: Matching (Cluster), Risk<br>- Downstream: Asset, Market, Position<br>線條顯示數據流動是單向且非同步的。 | 傳統金融系統往往受限於資料庫的鎖機制 (Locking)。Open Exchange Core 採用了徹底的**事件驅動架構 (Event-Driven Architecture)**。                | 強調 Kafka 作為系統的中樞神經。             |
| 0:20 | **[特寫：熱數據與冷數據分離]**<br>畫面分割為二：<br>🔴 **Hot Path (交易鏈路):** Order -> Matching -> Risk。標註 "In-Memory" & "WAL"。<br>🔵 **Cold Path (查詢/報表):** 數據非同步流入 MySQL/Redis 供查詢。                                        | 我們將「交易」與「查詢」完全分離。核心交易鏈路不觸碰資料庫，而是全內存運算。這使得下單延遲從毫秒級降低到了微秒級。                                                    | 建立 "Memory-First" 的概念。          |
| 0:35 | **[微觀架構：撮合引擎設計]**<br>透視 `Matching Service` 內部。<br>顯示多個 **Partition (Instrument)**，每個 Partition 只有**一條執行緒 (Single Thread)** 負責處理。<br>沒有 Lock，只有 Queue。                                                   | 為了解決併發競爭，撮合引擎採用**單執行緒模型 (Single-Threaded Model)** 針對每個交易對進行分片。沒有 Context Switch，也沒有鎖競爭，這是系統能支撐單機十萬級 TPS 的秘密。 | 這裡對應 `InstrumentProcessor` 的設計。 |
|      |                                                                                                                                                                                                         |                                                                                                              |                                 |

---

## 2. SDK 設計與架構治理 (SDK Design & Governance)

**目標：** 解釋在這樣複雜的分佈式環境下，如何維持程式碼的整潔與一致性。

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) | 執行建議 |
| :--- | :--- | :--- | :--- |
| 0:50 | **[IDE 專案結構特寫]**<br>展開 `sdk` 目錄，快速掃過：<br>- `sdk-infra-kafka` (處理事件通訊)<br>- `sdk-core` (統一異常與工具)<br>- `sdk-auth` (統一安全)<br>背景淡出架構圖，聚焦代碼。 | 在微服務架構中，基礎設施的複雜度極高。為了讓業務團隊專注於核心邏輯，我開發了一套標準化的 SDK。 | 實際錄製 IntelliJ IDEA 畫面。 |
| 1:05 | **[程式碼展示：Spring Boot Starter]**<br>左側：`MatchingApplication` 啟動類，乾乾淨淨。<br>右側：SDK 內的 `KafkaAutoConfiguration`，顯示複雜的序列化與重試邏輯被封裝起來。 | 我們利用 **Spring Boot Starter** 機制，將 Kafka 的序列化、Redis 的連接池管理、以及分佈式追蹤 (Tracing) 埋點全部封裝。 | 展示 "Convention over Configuration"。 |
| 1:20 | **[視覺化效益：Trace ID]**<br>畫面顯示一條 Log：`[TraceId: a1b2c3d4] Processing Order...`<br>接著畫面跳轉到 Grafana/Zipkin，輸入該 ID，顯示完整的跨服務調用鏈路。 | 這不僅消除了重複代碼，更重要的是實現了全鏈路的**可觀測性 (Observability)**。從 Gateway 到撮合再到資產結算，每一個請求都有唯一的 Trace ID 貫穿其中。 | 證明架構的可維護性。 |

---

## 3. 總結 (Wrap-up)

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) | 執行建議 |
| :--- | :--- | :--- | :--- |
| 1:40 | **[回到全景架構圖 + 技術堆疊]**<br>下方出現 icon：Java 21, Spring Cloud, Kafka, Docker, K8s。<br>字幕：**Extreme Performance, Rock-Solid Consistency**。 | Open Exchange Core 透過事件驅動與內存計算極大化了效能，並透過 SDK 治理確保了系統的穩健。這是一個為規模化而生的金融交易架構。 | |

---

## 準備工作清單 (Action Items)

1.  **架構圖繪製 (Draw.io/Keynote)**：
    *   **圖1 (Macro)**: 中心是 Kafka，周圍是 Service。
    *   **圖2 (Micro)**: 畫出 `Matching Service` 內部的方塊：`InstrumentProcessor` (Map<Long, Processor>) -> `Single Thread` -> `OrderBook` -> `WAL`。這張圖能展現對源碼的深刻理解。
2.  **IDE 錄製**：
    *   打開 `MatchingEngine.java` 展示 `ConcurrentHashMap<Long, InstrumentProcessor>`。
    *   打開 `InstrumentProcessor.java` 展示 `Executors.newFixedThreadPool(1)` (證明單執行緒)。
    *   打開 SDK 的 `AutoConfiguration` 類別。
3.  **Log 準備**：
    *   啟動服務，發送一筆交易，攔截帶有 TraceId 的 Log 截圖。
