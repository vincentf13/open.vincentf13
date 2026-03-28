# Open Exchange Core - 高性能交易系統架構與 SDK 設計

此文檔旨在協助於履歷或作品集中介紹本項目的技術架構與設計理念。

## 1. 項目概述 (Project Overview)

**Open Exchange Core** 是一個基於微服務架構 (Microservices) 的高性能加密貨幣撮合系統。核心撮合引擎採用 **LMAX 架構思想**，透過全內存運算 (In-Memory Computing) 與單執行緒模型 (Single-Threaded Model)，實現了微秒級 (Microsecond-level) 的撮合延遲與極高的吞吐量。系統採用 **事件驅動架構 (Event-Driven Architecture, EDA)**，利用 Kafka 進行服務解耦與流量削峰，確保在高併發場景下的穩定性。

---

## 2. 系統架構亮點 (System Architecture Highlights)

### 2.1 核心撮合引擎 (Core Matching Engine)
*   **LMAX 架構思想 (LMAX-inspired):** 摒棄傳統數據庫鎖 (DB Lock) 機制，採用 **單執行緒 (Single-Threaded)** 處理單一交易對的所有訂單，完全消除 Context Switch 與資源競爭的損耗。
*   **全內存運算 (In-Memory State):** `OrderBook` 與帳戶狀態常駐內存，讀寫速度極快。
*   **WAL-First 持久化 (Write-Ahead Logging):**
    *   所有寫入操作優先寫入內存 WAL，隨後由獨立的 Loader 執行緒異步持久化至 MySQL。
    *   **故障恢復 (Crash Recovery):** 啟動時透過 `Latest Snapshot` + `WAL Replay` 快速重建內存狀態，保證數據不丟失 (RPO ≈ 0)。
*   **分片擴展 (Sharding):** 採用 `InstrumentProcessor` 模式，不同交易對 (Symbol) 分配至不同執行緒/CPU 核心運行，實現線程級別的水平擴展。

### 2.2 事件驅動與微服務 (Event-Driven Microservices)
*   **異步通訊 (Asynchronous Messaging):** 服務間通訊 (如下單 -> 撮合 -> 結算 -> 行情) 全面採用 Kafka，確保核心鏈路的低延遲與高可用。
*   **服務職責分離 (CQRS):**
    *   **Matching Service:** 專注於極致寫入效能 (Command)。
    *   **Market Service:** 專注於高併發查詢 (Query) 與 WebSocket 推送。
*   **技術棧 (Tech Stack):** Java 17/21, Spring Boot 3, Spring Cloud Alibaba (Nacos, Gateway), Kafka, Redis, MySQL, React 19 (Frontend).

---

## 3. SDK 設計與治理 (SDK Design & Governance)

為了降低微服務開發複雜度並統一技術標準，本項目採用了分層的 SDK 設計策略：

### 3.1 基礎設施 SDK (`sdk/` - Infrastructure Layer)
*   **目的：** 封裝複雜的 middleware 配置，實現 "開箱即用" (Convention over Configuration)。
*   **主要模組：**
    *   `sdk-infra-kafka`: 統一封裝 Serializer/Deserializer、Retry 機制、以及異步發送配置。
    *   `sdk-infra-redis`: 提供統一的緩存策略與分佈式鎖 (Distributed Lock) 實作。
    *   `sdk-auth`: 封裝 JWT 驗證與上下文傳遞 (Security Context)。
*   **效益：** 業務開發人員無需關心底層細節，只需引入 Starter 依賴即可獲得經過調優的基礎設施能力。

### 3.2 領域 SDK (`service/exchange/*-sdk` - Domain Layer)
*   **目的：** 定義服務間的通訊契約，打破 "服務孤島" 但保持低耦合。
*   **內容：**
    *   **DTOs (Data Transfer Objects):** 定義 API 請求與回應結構。
    *   **Events (Integration Events):** 定義 Kafka 消息體 (如 `OrderCreatedEvent`, `TradeMatchedEvent`)。
*   **效益：**
    *   **強類型安全 (Type Safety):** 避免手寫 JSON 導致的解析錯誤。
    *   **版本管理:** 透過 SDK 版本控制，平滑過渡接口變更。
    *   **代碼復用:** 消費者 (Consumer) 無需重複定義生產者 (Producer) 的數據結構。

---

## 4. 履歷/面試話術建議 (Interview Talking Points)

*   **關於效能：** "我參與設計了基於 LMAX 思想的撮合引擎，透過將核心邏輯移至內存並採用 WAL-First 策略，成功解決了傳統數據庫架構在高頻交易下的鎖競爭瓶頸。"
*   **關於架構：** "利用 Event-Driven 架構與 Kafka 解耦系統，使得撮合引擎能專注於定序與匹配，同時下游的風控與行情服務能異步處理，提升了系統整體的吞吐量與擴展性。"
*   **關於 SDK：** "為了提升團隊開發效率，我主導/參與了 SDK 的分層設計，將基礎設施配置與業務契約分離，這不僅統一了代碼規範，也大幅降低了新服務的接入成本。"
