# 影片開場 PPT 設計 (Single Slide Strategy)

此頁面用於影片開頭，依照影片敘事順序，條列式介紹本集將探討的核心主題。

## Option A: Chinese Version (中文版)

> **標題 (Title):** Open Exchange Core: 架構與 SDK 設計揭秘
> **副標題 (Subtitle):** 打造微秒級高頻交易系統的核心技術
>
> **本集大綱 (Agenda):**
> 1.  **核心架構演進:**
>     *   徹底擺脫傳統金融系統受限於 DB 鎖 (Lock) 與隨機 I/O 的效能枷鎖。
>     *   採用 **LMAX** 純內存無鎖架構 + 全異步事件驅動 + 內存佇列定序 + **WAL** 批次持久化。
> 2.  **CQRS 讀寫分離:**
>     *   **Matching 服務:** 採用順序 I/O 與單執行緒批次處理，極致壓榨硬體性能。
>     *   **Market Data 服務:** 採用多級緩存 (L1/L2) 架構，支撐百萬級高並發讀取。
> 3.  **風控與帳戶體系:**
>     *   **風控:** 實施嚴格**事前風控 (Pre-Trade Check)**，動態計算保證金，風險零容忍。
>     *   **帳戶:** 貫徹**複式記帳 (Double-Entry)** 原則，支援任意時刻資產負債表快照重建，確保資金流可追溯、可審計。
> 4.  **分佈式一致性:**
>     *   **Flip 協議:** 獨創分佈式交易協議，完美解決多節點**資源搶奪 (Anti-Stealing)** 難題。
>     *   **異常處理:** 內建**自動補償 (Compensation)** 機制，強大容錯能力確保系統最終一致性。
> 5.  **彈性擴展策略:**
>     *   **無狀態服務:** 網關與查詢服務支持**無限水平擴容**，流量無上限。
>     *   **有狀態核心:** 透過交易對**精確分片 (Sharding)**，實現核心引擎資源隔離與線性增長。

## Option B: English Version (英文版)

> **Title:** Open Exchange Core: Architecture & SDK Design
> **Subtitle:** Building the Core of a Microsecond-Level HFT System
>
> **Agenda:**
> 1.  **Core Evolution:**
>     *   Breaking free from the performance shackles of traditional DB locks and random I/O.
>     *   Adopting **LMAX** In-memory Lock-free Arch + Async Event-Driven + Ring Buffer + **WAL** Batch Persistence.
> 2.  **CQRS Pattern:**
>     *   **Matching Service:** Adopting sequential I/O & single-threaded batching to maximize hardware performance.
>     *   **Market Data Service:** Utilizing multi-level caching (L1/L2) to support million-level high concurrency.
> 3.  **Risk & Accounts:**
>     *   **Risk:** Strict **Pre-Trade Checks** with dynamic margin calculation for zero risk tolerance.
>     *   **Accounts:** Implementing **Double-Entry Bookkeeping** to enable balance sheet reconstruction at any moment, ensuring traceable and auditable fund flows.
> 4.  **Distributed Consistency:**
>     *   **Flip Protocol:** Proprietary distributed protocol solving multi-node **Resource Contention (Anti-Stealing)**.
>     *   **Error Handling:** Built-in **Auto-Compensation** ensuring eventual consistency with high fault tolerance.
> 5.  **Scalability Strategy:**
>     *   **Stateless Services:** Unlimited **Horizontal Scaling** for gateway & query services.
>     *   **Stateful Core:** Linear scalability via precise **Symbol Sharding**, isolating core engine resources.

---

# Open Exchange Core - 技術展示系列 Ep.1：系統架構與 SDK 設計

**影片長度：** 約 4 - 4.5 分鐘
**核心亮點：** CQRS、LMAX、WAL、Flip 分佈式事務協議、多級緩存

---

## 1. 系統架構總覽 (System Architecture Overview)

**目標：** 深入剖析為何傳統架構無法支撐金融級高頻交易，並展示 Open Exchange Core 如何透過架構創新解決此問題。

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) | 執行建議 |
| :--- | :--- | :--- | :--- |
| 0:00 | **[對比動畫：傳統 vs 現代]**<br>左邊顯示「傳統架構」：大量請求擠向一個 Database 圖示，DB 出現紅色鎖頭 (Lock)。<br>右邊顯示「Open Exchange Core」：數據像流水一樣通過管道 (Kafka)。 | 傳統金融系統往往受限於資料庫的 ACID 鎖機制。當海量訂單湧入時，行級鎖會導致嚴重的資源競爭。Open Exchange Core 徹底摒棄了這種依賴，採用了**全異步的事件驅動架構**。 | |
| 0:25 | **[架構特寫：LMAX 核心思想]**<br>畫面顯示一個類似 CPU 管道的圖示。<br>標註：**Disruptor Pattern / Ring Buffer**。<br>數據單向流動，無鎖競爭。 | 我們借鑒了 **LMAX Disruptor** 的架構思想。在最核心的撮合環節，我們完全移除了隨機磁碟 I/O 與鎖競爭，採用內存佇列進行定序。 | |
| 0:40 | **[深度解析：WAL 與災難復原]**<br>畫面顯示一個 **File** 圖示，數據以 **Batch** 的形式快速寫入。<br>標註：**Sequential Write (順序寫)**。<br>然後演示系統崩潰 (Crash)，接著進度條快速跑動 (Replay)，內存狀態瞬間恢復。<br>右下角浮現未來規劃：**Raft Consensus / RingBuffer Optimization**。 | 為了確保數據絕對安全，我們實現了 **WAL (Write-Ahead Logging)** 機制。所有的撮合事件會先進行**批次順序寫入**——這也是整個撮合過程中**唯一的一次磁碟 I/O**。即使系統瞬間斷電，我們也能透過重放 WAL 日誌，在毫秒級內完全恢復內存狀態。<br>此外，我們規劃了基於 **Raft 協議** 的事件複製與 **RingBuffer** 優化，進一步提升事件層級的高可用性。 | |

---

## 2. 領域服務架構特寫 (Domain Service Architecture)

**目標：** 針對不同服務屬性 (寫入密集、查詢密集、計算密集) 展示差異化的架構設計。

| 時間   | 畫面 (Visual)                                                                                                                                                                                | 旁白腳本 (Audio)                                                                                                                                                                                                  | 執行建議                     |
| :--- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | :----------------------- |
| 1:15 | **[CQRS 宏觀視角]**<br>畫面將服務分為兩群：<br>⬅️ **Command Side (寫):** Order, Matching (紅色)<br>➡️ **Query Side (讀):** Market, History (藍色)                                                              | 整個系統遵循 **CQRS (命令查詢職責分離)** 原則。針對不同的業務型態，我們採用了截然不同的優化策略。                                                                                                                                                       | 引入 CQRS 概念。              |
| 1:25 | **[Matching & Order (寫入極致)]**<br>特寫撮合引擎。<br>關鍵字：**Batching**, **Single-Thread**, **No-Lock**。                                                                                              | 對於寫入密集的 **Matching Service**，我們追求極致的低延遲。透過批次處理與單執行緒無鎖設計，我們將硬體性能壓榨到了極限，確保每一筆訂單都能在微秒級完成定序與撮合。                                                                                                                   | 總結寫入端特點。                 |
| 1:40 | **[Market Data (查詢與緩存)]**<br>特寫行情服務。<br>畫面顯示：Request -> **L1 Cache (Local)** -> **L2 Cache (Redis)** -> DB。<br>顯示 L1 命中率極高 (99%)。<br>右下角浮現未來規劃：**Netty WebSocket Push**。                   | 對於查詢密集的 **Market Data Service**，挑戰在於熱點數據的頻繁讀取。我們實現了**多級緩存架構 (Multi-Level Caching)**，利用 L1 本地快取攔截 99% 的熱點請求，極大降低了對 Redis 與資料庫的壓力。<br>未來，我們更規劃引入 **Netty WebSocket**，從「客戶端輪詢」升級為「伺服器主動推播」，進一步提升即時性。             |                          |
| 2:00 | **[Risk & Asset (風控與結算)]**<br>特寫風控與資產模組。<br>Risk 顯示：**Pre-Trade Check (事前風控)**。<br>Asset 顯示：**Double-Entry (複式記帳)**。<br>兩者中間有強一致性的連線。                                                      | 而對於最敏感的 **Risk (風控)** 與 **Asset (結算)**，架構的核心是「正確性」。風控採用事前檢查模型 (Pre-Trade Check) 實時計算保證金；結算層則嚴格遵循**複式記帳法 (Double-Entry Bookkeeping)**，確保每一分錢的流動都有借貸平衡與流向紀錄，能重建任何時刻的用戶與平台資產負債表快照，透過這項工具將人工對帳與稽核成本大幅的縮減。實現資金零誤差。 |                          |
| 2:20 | **[分佈式事務：Flip 協議]**<br>畫面顯示兩個節點同時嘗試扣款。<br>出現 **Flip Protocol** 的動畫：<br>1. **Try Flip**: 嘗試翻轉狀態。<br>2. **Confirm/Cancel**: 成功則提交，失敗則回滾。<br>關鍵字：**Anti-Stealing**, **Eventual Consistency**。 | 但在分佈式環境下，如何保證撮合成功後資產一定能正確扣款？為此，我設計並實作了 **Flip 分佈式事務協議**。它專門解決多節點間的**資源搶奪 (Stealing)** 與併發衝突問題。此外，當交易失敗或發生異常時，系統會自動觸發**補償機制 (Compensation)**，回滾所有已執行的操作，確保最終一致性。                                               | **(新增重點)** Flip 協議與補償機制。 |
| 2:50 | **[彈性擴展總結]**<br>畫面縮小回全景。<br>Gateway, Market, Asset 像細胞分裂一樣快速複製 (Stateless/DB-Backed)。<br>Matching 則依據幣種分片 (Stateful In-Memory)。                                                            | 正因如此，我們的擴展策略也是分層的：Gateway、Market 甚至 Asset Service 等服務，因為不持有內存狀態，可以無限水平擴展以應對流量；而唯獨擁有核心內存狀態的 Matching Engine，透過**交易對分片 (Sharding)** 來實現資源隔離與線性擴容。                                                               |                          |

---

## 4. 總結 (Wrap-up)

| 時間   | 畫面 (Visual)                                                                                                | 旁白腳本 (Audio)                                                                                                                                         | 執行建議 |
| :--- | :--------------------------------------------------------------------------------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------- | :--- |
| 4:05 | **[回到全景架構圖 + 關鍵字]**<br>文字依序浮現：<br>1. **CQRS & LMAX**<br>2. **Flip Protocol**<br>3. **Multi-Level Caching** | Open Exchange Core 的架構哲學很簡單：透過 CQRS 分離讀寫，透過LMAX架構處理寫入密集型任務，透過 Flip 協議與補償確保一致性，透過資產帳戶體系設計掌握每一分金流與稽核，透過Risk模塊保障用戶與平台權益，透過多級緩存極大化吞吐。這是一個為速度與準確性而生的金融核心。 |      |
