# 影片開場 PPT 設計 (Single Slide Strategy)

此頁面用於影片開頭，依照影片敘事順序，條列式介紹本集將探討的核心主題。

## Option A: Chinese Version (中文版)

> **標題 (Title):** Open Exchange Core: SDK 設計與架構治理深度解析
> **副標題 (Subtitle):** 透過模組化 SDK 實現「依賴即治理」
>
> **本集大綱 (Agenda):**
> 1.  **SDK Core 基石 (Foundation):**
>     *   **可觀測性與監控 (Observability):**
>         *   **Log4j2 深度調優:** 啟用全異步日誌 (Async Logger)，極致優化吞吐量與低延遲寫入；簡化 Logger 宣告並統一格式。
>         *   **上下文自動化:** 透過 MDC 自動注入 **TraceId** 與 **ReqId**，確保全鏈路追蹤資訊無斷點。
>         *   **指標治理 (Metrics):** 設計標準化 Metrics SDK，提供高效能指標封裝與 Tag 管理；深度整合 **ThreadPool 埋點**，即時監控核心資源水位。
>     *   **系統治理 (Governance):**
>         *   **統一生命週期:** 標準化 **Bootstrap** 啟動流程與環境檢查，確保服務行為一致且可預測。
>         *   **異常規範:** 建立 **Global Exception** 轉換機制，異常拋出時自動攜帶 MDC 上下文，提升排錯效率。
>     *   **數據一致性與安全 (Data & Security):**
>         *   **序列化防禦:** 嚴格訂製 **Jackson** 規則 (禁用科學記號、禁止 Null 隱式轉型、RFC 3339 時間標準)，防禦 JSON 混淆攻擊並確保金額精度。
>         *   **審計與校驗:** 獨創 **OpenObjectDiff** 生成物件差異 (Delta JSON) 賦能操作審計；統一 **OpenValidator** 實現從 API 到 DB 的多層次防禦體系。
>         *   **分佈式 ID:** 整合 **Snowflake** 算法，確保高併發下的 ID 全域唯一性。
>     *   **容器化整合測試 (Containerized Testing):**
>         *   **真實環境模擬:** 測試自動拉起容器化 MySQL、Redis 與 Kafka，消除 Mock 差異，確保單元測試涵蓋真實版本影響。
> 2.  **基礎設施封裝 (Infrastructure Abstraction):**
>     *   **MySQL 治理模式 (MySQL Governance):**
>         *   **透明化管理:** 整合 MyBatis Plus 與動態多資料源，透過攔截器實現對業務透明的讀寫分離與資料源切換。
>         *   **效能與安全:** 內建高效能 **BatchExecutor** 突破傳統批次寫入瓶頸；預設啟用 **Block Attack** 攔截全表危險操作。
>         *   **分佈式事務方案:** 標準化 **Transactional Outbox** 與 **Retry Task** 模式，使複雜的分佈式事務開發變得極其簡便且高可靠。
>     *   **Redis 雙引擎封裝 (Redis Dual-Engine):**
>         *   **開發模板化:** 深度封裝 Lettuce 與 Redisson，封裝 Cache-Aside 讀取模板、異步寫入、Cluster pipeline 批次讀寫，簡便緩存操作與優化性能。
>         *   **安全與防護:** 統一安全防護與強制 TTL 抖動機制，從架構層面杜絕緩存穿透、擊穿、雪崩。
>     *   **Kafka 契約式治理 (Kafka Messaging):**
>         *   **Contract-First 治理:** 強推以 Client SDK 定義 Topic 與 Event 契約，讓消費方明確對接規範，極大化降低跨服務溝通成本。
>         *   **智慧生產者與消費者:** 內建 Bean Validation 攔截非法數據，自動注入 MDC 事件上下文；支援自動化**指數退避重試**與 **DLQ 路由**。
>         *   **效能與配置優化:** 深度優化傳輸可靠性與壓縮配置，統一開發日誌；調整 Batch 參數、異步 Ack 與 CooperativeStickyAssignor 策略提升吞吐。
> 3.  **金融級安全架構 (Auth):**
>     *   獨創 **JWT + Redis 混合驗證模式**，兼具無狀態效能與**即時撤銷 (Revocation)** 能力。
>     *   提供 **@Public/Private/Jwt** 多重策略註解，實現無感接入。
> 4.  **微服務治理與韌性 (Governance & Resilience):**
>     *   **Spring MVC & OpenFeign:** 標準化 Web 請求處理與全鏈路 TraceId/UserContext/Language 無感透傳。
>     *   **Resilience4j:** 預設註解式 **Circuit Breaker** 與 **Rate Limiter**，強化系統自我保護能力。

## Option B: English Version (英文版)

> **Title:** Open Exchange Core: SDK Design & Architecture Governance
> **Subtitle:** Achieving "Governance by Dependency" via Modular SDKs
>
> **Agenda:**
> 1.  **SDK Core Foundation:**
>     *   **Observability & Monitoring:**
>         *   **Log4j2 Deep Tuning:** Full Async Logger for maximized throughput and low-latency; simplified logger declaration with unified formatting.
>         *   **Context Automation:** Auto-injection of **TraceId** & **ReqId** via MDC for seamless end-to-end tracing.
>         *   **Metrics Governance:** Standardized Metrics SDK with high-perf encapsulation and tag management; deep **ThreadPool instrumentation** for real-time resource monitoring.
>     *   **System Governance:**
>         *   **Unified Lifecycle:** Standardized **Bootstrap** process and environment checks for predictable service behavior.
>         *   **Exception Standardization:** **Global Exception** translation with auto-attached MDC context for faster troubleshooting.
>     *   **Data Consistency & Security:**
>         *   **Serialization Defense:** Strict **Jackson** customization (no scientific notation, no null-to-zero coercion, RFC 3339 standard) to prevent JSON confusion attacks and ensure precision.
>         *   **Audit & Validation:** Proprietary **OpenObjectDiff** for Delta JSON generation to empower audits; unified **OpenValidator** for multi-layer defense from API to DB.
>         *   **Distributed ID:** Integrated **Snowflake** algorithm for globally unique IDs in high-concurrency environments.
>     *   **Containerized Integration Testing:**
>         *   **Real-Environment Simulation:** Automatically spinning up containerized MySQL, Redis, and Kafka for local tests, eliminating Mock gaps and ensuring version compatibility.
> 2.  **Infrastructure Abstraction:**
>     *   **MySQL Governance:**
>         *   **Transparent Management:** Unifying MyBatis Plus and Dynamic Datasource via interceptors for seamless R/W splitting and datasource switching.
>         *   **Performance & Security:** Built-in high-performance **BatchExecutor**; default **Block Attack** protection against full-table operations.
>         *   **Distributed Transaction Patterns:** Standardized **Transactional Outbox** and **Retry Task** patterns, making complex distributed transactions simple and highly reliable.
>     *   **Redis Dual-Engine:**
>         *   **Template-Based Development:** Deeply encapsulated Lettuce and Redisson, providing Cache-Aside read templates, asynchronous writes, and Cluster pipeline batch R/W, simplifying cache operations and performance.
>         *   **Security & Protection:** Unified security protection and mandatory TTL jitter mechanism, eliminating cache penetration, breakdown, and avalanche at the architectural level.
>     *   **Kafka Messaging:**
>         *   **Contract-First Governance:** Enforced Topic and Event contracts via Client SDKs, providing clear integration specs for consumers and minimizing cross-service communication overhead.
>         *   **Smart Producer & Consumer:** Built-in Bean Validation to block invalid data; automatic MDC context injection; and automated **Exponential Backoff Retry** with **DLQ routing**.
>         *   **Performance & Config Tuning:** Optimized transmission reliability and compression, with unified development logging; tuned Batch params, Async Ack, and CooperativeStickyAssignor.
> 3.  **Financial-Grade Security:**
>     *   Proprietary **JWT + Redis Hybrid Validation**, combining stateless speed with **Real-time Revocation**.
>     *   Multi-strategy annotations (**@Public/Private/Jwt**) for seamless integration.
> 4.  **Microservice Governance & Resilience:**
>     *   **Spring MVC & OpenFeign:** Standardized request handling and seamless full-link propagation of TraceId/UserContext/Language.
>     *   **Resilience4j:** Annotation-based **Circuit Breaker** and **Rate Limiter** defaults for robust self-protection.

---

# Open Exchange Core - 技術展示系列 Ep.2：SDK 設計與架構治理深度解析

**總時長預估：** 6 - 8 分鐘
**核心目標：** 展示如何透過模組化 SDK 實現「依賴即治理」，解決微服務架構中的碎片化與一致性問題。

---

## 1. SDK Core: 全方位系統基石 (The Comprehensive Foundation)

**模組：** `sdk-core`
**核心價值：** 提供可觀測性、生命週期治理與數據一致性的統一標準，是所有微服務的共同基因。

### 1.1 可觀測性：日誌與監控 (Observability: Log & Metrics)

**設計哲學：** 標準化埋點與自動上下文注入 (Context Injection)，消除業務代碼中的重複勞動。

| 時間   | 畫面 (Visual)                                                                                                                                                                        | 旁白腳本 (Audio)                                                                                                                                                                                                                           | 執行建議 |
| :--- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :--- |
| 0:00 | **[Log 配置與效能優化]**<br>顯示 `log4j2-spring.xml` 的 AsyncLogger 配置。<br>顯示 `OpenLog.info` 代碼中的 `isInfoEnabled` 檢查與 `StackWalker` 調用。<br>Log 輸出：`[TraceId: 123][ReqId: abc] Order Created` | 在高併發系統中，日誌往往是效能殺手。我們基於 **Log4j2 異步日誌** 進行了深度調優，並利用 **StackWalker** 自動定位呼叫類別，讓開發者無需再每個Class手動宣告 Logger 物件。同時，OpenLog 內部封裝了 `isInfoEnabled` 等級檢查，避免無效的字串拼接損耗。更關鍵的是，MDC 自動注入了 Trace ID 與 Request ID，確保每一行日誌都能精確關聯到具體的請求上下文，讓分佈式排錯不再是大海撈針。 |      |
| 0:25 | **[監控指標封裝]**<br>顯示 `MCounter` 和 `MTimer` 的源碼。<br>右側顯示 Prometheus 中漂亮的 Metrics 曲線。                                                                                                  | 對於監控，我們封裝了 Micrometer 提供了五種常見指標的靜態工具。開發者無需手動注入 Registry，只需一行代碼即可完成標準化的業務指標埋點。這些指標經過標籤化 <br>(Tagging) 設計，讓開發者使用統一的指標命名與label進行埋點，讓Grafanfa的聚合查詢，不再會遇到雜亂無章的指標數據。並且內部也對Micrometer   內部的指標查詢與註冊性能進行優化，這在每秒萬級TPS的交易系統裡至關重要。                 |      |

### 1.2 系統治理：啟動與異常 (Governance: Bootstrap & Exception)

**設計哲學：** 統一生命週期與錯誤上下文，讓系統行為可預測、可追蹤。

| 時間   | 畫面 (Visual)                                                                                                                                           | 旁白腳本 (Audio)                                                                                                                                                                                                                                                                         | 執行建議           |
| :--- | :---------------------------------------------------------------------------------------------------------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------- |
| 0:45 | **[啟動與熱加載]**<br>顯示 `Bootstrap` 包下的 `StartupListener`。<br>Console 打印出標準的 Banner 和環境檢查資訊。                                                               | 透過 `Bootstrap` 模組，我們統一了所有微服務的啟動流程，包括環境變數檢查、緩存預熱以及熱加載程序的初始化。這確保了無論是哪個服務，其啟動行為都是標準且可預測的。                                                                                                                                                                                               |                |
| 1:00 | **[異常上下文注入]**<br>顯示 `OpenException` 拋出時自動捕捉 TraceId。<br>顯示一個定義了 100+ 個錯誤碼的 `GlobalErrorCode` Enum。<br>顯示 Controller 層的 `GlobalExceptionHandler` 捕獲邏輯。 | 錯誤處理方面，我們設計了統一的 `OpenException` 基類與 `OpenErrorCode` 介面。當異常發生時，SDK 會自動將 Trace ID 與 Request ID   注入到錯誤上下文中，並支持附加 Meta 資訊。這讓運維人員看到錯誤日誌時，能立刻獲得完整的除錯線索，而無需再回頭翻查請求參數。<br><br>當異常發生時，SDK 會在各個層面（如 Controller、MQ Listener）自動捕獲並處理 `OpenException`。<br><br>這不僅實現了錯誤訊息的標準化，更讓運維人員能立刻獲得完整的除錯線索。 | 加上 Enum 與全局捕獲。 |

### 1.3 數據一致性與審計 (Data Consistency & Audit)

**設計哲學：** 確保數據在傳輸、驗證與記錄過程中的絕對準確與安全。

| 時間   | 畫面 (Visual)                                                                                                                | 旁白腳本 (Audio)                                                                                                                                                                                                                 | 執行建議 |
| :--- | :------------------------------------------------------------------------------------------------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :--- |
| 1:20 | **[ID 與 Mapper]**<br>顯示 `Snowflake` ID 生成代碼與 `workerId` 配置。<br>顯示 `ObjectMapperConfig` 的各項安全性與精度配置。                        | 在數據層面，我們整合了 **Yitter 雪花算法**，各服務可獨立配置 `workerId` 以確保分佈式環境下的全局唯一性。<br><br>同時，針對金融數據的敏感性，我們深度定制了 Jackson 規則：包括嚴格的重複 Key 偵測、禁止 BigDecimal 科學記號輸出、禁止基本型別 null 隱式轉 0、以及符合 RFC 3339 標準的時間格式化。這套配置不僅防禦了 JSON 混淆攻擊，更確保了數據精度與安全性的滴水不漏。 |      |
| 1:40 | **[審計與驗證]**<br>顯示 `OpenObjectDiff.diff(old, new)` 的結果 JSON。<br>顯示 `OpenValidator` 在 Controller、Service、Repository 各層調用的代碼。 | 為了滿足審計需求，我開發了 **`OpenObjectDiff`** 工具，能高效比對物件差異並生成 Delta JSON，這在記錄操作日誌時極為強大。<br><br>此外，透過 `OpenValidator` 的 Group 分組設計，我們從接口入口到 Service 層，甚至在 Repository 持久化前，都能進行標準化的參數校驗。這確保了每一層的入參數據結構都嚴格符合規範，在代碼層面築起了多層的安全防禦體系。          |      |

---

## 2. SDK Core Test: 整合測試標準化 (Standardized Testing)

**模組：** `sdk-core-test`
**核心價值：** 提供「容器化」與「真實環境」的靈活切換，讓整合測試既獨立又可靠。

| 時間   | 畫面 (Visual)                                                                                                                        | 旁白腳本 (Audio)                                                                                                                                    | 執行建議              |
| :--- | :--------------------------------------------------------------------------------------------------------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------- | :---------------- | 
| 2:00 | **[真實環境容器化]**<br>顯示繼承 `BaseIntegrationTest`。<br>動畫演示：Maven Test -> Docker Pull/Run MySQL -> Test Passed。<br>標註：**TestContainers**。 | 在微服務測試中，Mock 外部依賴往往會導致測試與生產行為不一致。`sdk-core-test` 深度整合了 **TestContainers**，讓開發者在本地執行測試時，能自動拉起真實版本的 MySQL、Redis 與 Kafka 容器，使單元測試涵蓋到外部依賴的版本影響。<br> | 強調「版本一致性」與「並行效能」。 |
| 2:20 | **[靈活環境切換]**<br>顯示設定檔 `application-test.yml`。<br>將 `test.container.enabled: true` 改為 `false`。<br>顯示連接字串自動切換到遠端 Dev DB。             | 更貼心的是，我們設計了環境切換開關。開發者可以透過簡單的配置，在「完全隔離的容器環境」與「真實的開發環境」之間一鍵切換。這在除錯複雜的聯調問題時非常有用，讓開發者既能享受容器化的隔離性，也能在需要時直接連接外部服務進行排查。                                | 強調「靈活切換」的開發體驗。    |

---

## 3. Infra: 駕馭中間件 (Taming Middlewares)

**模組：** `sdk-infra-mysql`, `sdk-infra-redis`, `sdk-infra-kafka`
**核心價值：** 封裝複雜的分佈式模式，提供開箱即用的高效能與高可靠性組件。

#### 3.1 MySQL: 基礎設施統一管理與模式 (Infra Management & Patterns)

**設計哲學：** 統一的數據存取配置、極致的寫入效能與最後一道安全防線。

| 時間   | 畫面 (Visual)                                                                                              | 旁白腳本 (Audio)                                                                                                                                                      | 執行建議       |
| :--- | :------------------------------------------------------------------------------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------- | :--------- |
| 2:40 | **[MyBatis 與多資料源管理]**<br>顯示 `MybatisPlusConfig` 與 `DynamicDataSource` 配置。<br>展示 `TypeHandler` 與 SQL 攔截器。 | 在資料庫層面，`sdk-infra-mysql` 統一管理了 MyBatis Plus 與 **Dynamic Datasource**。我們封裝了通用的 TypeHandler 與攔截器，實現了讀寫分離與分庫分表對業務代碼的完全透明。開發者只需專注於 SQL 撰寫，底層的資料源切換與數據格式轉換都由 SDK 自動處理。 | 先提管理，再提透明。 |
| 2:50 | **[Batch 模式與安全防禦]**<br>顯示 `OpenMybatisBatchExecutor` 代碼。<br>顯示 `BlockAttackInnerInterceptor` 攔截 SQL。     | 針對大量數據寫入，我們提供了基於 MyBatis Batch 模式的 **BatchExecutor**，效能遠超普通的單筆插入。<br><br>同時，為了防止意外，我們預設啟用了 **BlockAttackInterceptor**，任何全表更新或刪除的 SQL 都會被 SDK 直接攔截，守住資料庫安全的最後一道防線。 |            |
| 3:00 | **[Outbox 與 Retry 模式]**<br>顯示 `MqOutboxRepository` 與 `RetryTaskRepository` 表結構。                          | 此外，SDK 標準化了 **Transactional Outbox** 與 **Retry Task** 模式。每個微服務共享相同的表結構與重試處理邏輯，確保了 DB 操作與 MQ 發送的強原子性，這讓原本複雜的分佈式事務開發變得極其簡便且高可靠。                                     | 強調開發簡便性。   |

#### 3.2 Redis: 雙引擎管理與模板化開發 (Dual Engine & Templates)

**設計哲學：** 統一管理 Lettuce 與 Redisson，透過模板化代碼消除分佈式環境下的併發隱患。

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) | 執行建議 |
| :--- | :--- | :--- | :--- |
| 3:10 | **[Lettuce 與 Redisson 統一管理]**<br>顯示 Redis 自動配置類別。<br>顯示 `OpenRedisString` 的各個模板方法。 | 在 Redis 層面，SDK 實現了 **Lettuce 與 Redisson 的統一管理與優化配置**。我們封裝了強大的 `OpenRedisString` 模板，提供了諸如 `getOrLoad` 模式化 Cache-Aside、`setAsync` 異步寫入、以及針對 Cluster 模式深度優化的 `setBatchCluster` 批次處理。 | |
| 3:25 | **[安全防護與 TTL 抖動]**<br>顯示 `RedisUtil.withJitter`。<br>顯示 `getOrLoad` 中的防穿透與防擊穿邏輯。 | 除了效能優化，我們更重視緩存的穩定性。系統強制引入了 **TTL 抖動 (Jitter)** 機制，並透過統一的模板邏輯，從架構層面杜絕了**緩存穿透、擊穿與雪崩**的風險，確保基礎設施在極端流量下依然穩健。 | |
| 3:40 | **[Redisson 分佈式鎖模板]**<br>顯示 `OpenRedissonLock.withLock` 調用。 | 對於複雜的分佈式鎖場景，我們透過 `OpenRedissonLock` 提供了簡潔的 `withLock` 模板。它自動處理了鎖的獲取、續約與釋放，並透過 Watchdog 機制確保業務執行的安全性，將分佈式競爭的處理難度降到了最低。 | |

#### 3.3 Kafka: 端到端的訊息治理 (End-to-End Message Governance)

**設計哲學：** 統一序列化標準與自動化容錯，拒絕黑盒操作。

| 時間   | 畫面 (Visual)                                                                                                                                        | 旁白腳本 (Audio)                                                                                                                                                                                                                                                                                                            | 執行建議 |
| :--- | :------------------------------------------------------------------------------------------------------------------------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :--- |
| 3:55 | **[Contract-First 與配置優化]**<br>顯示 Kafka 自動配置類別。<br>顯示 `OpenKafkaProducer` 發送代碼。<br>顯示 `exchange-sdk` 中的 Topic 定義與 Event DTO。                  | 對於 Kafka，我們在 `sdk-infra-kafka` 中預設啟用了高效能 **Snappy 壓縮** 與 **Acks=All** 的可靠性配置，並統一了生產與消費日誌以便於開發除錯。同時，針對高吞吐場景深度優化了 **Batch Size**、**Linger.ms** 與異步 Ack，並採用 `CooperativeStickyAssignor` 策略大幅縮短 Rebalance 時間，完美平衡了數據可靠性、延遲與吞吐量。<br>更重要的是，我們推行 **Contract-First** 模式：每個服務透過 Client SDK 提供 Topic 與強型別 Event 契約。這讓消費方能第一時間明確對接規範與數據結構，極大化地降低了跨團隊的溝通成本。 |      |
| 4:10 | **[智慧生產者與消費者]**<br>顯示 Producer 的 Bean Validation 校驗邏輯。<br>動畫：MDC 上下文注入 -> 指數退避重試 -> DLQ。<br>Log: `Kafka Send Automation` -> `Consume Automation`。 | 在 Open Exchange Core 中，Kafka 的自動化是端到端的。生產端在發送前自動執行 **Bean Validation** 攔截非法數據，並注入 MDC 事件上下文確保全鏈路可追蹤。消費端則實現了自動反序列化，並內建了**指數退避 (Exponential Backoff) 重試機制**。當重試超過上限時，消息會自動路由到 DLQ，實現了從發送端到消費端的完整容錯閉環。                                                  |      |

---

## 4. Auth: 金融級安全架構 (Security Architecture)

**模組：** `sdk-auth`, `sdk-auth-jwt`, `sdk-auth-server`
**核心價值：** 實現無狀態架構的高效驗證，同時保留對會話 (Session) 的絕對控制權。

#### 4.1 基礎設施 (Infrastructure)

**模組：** `sdk-auth-jwt`
**職責定位：** 提供底層的 JWT 解析、驗證與 Session 存儲邏輯，是所有安全操作的技術基石。

| 時間   | 畫面 (Visual)                                                                                                                               | 旁白腳本 (Audio)                                                                                                                                                                                                                                           | 執行建議 |
| :--- | :---------------------------------------------------------------------------------------------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :--- |
| 4:25 | **[JWT 驗證與 Redis 混合模式]**<br>顯示 `JwtProvider` 解析代碼。<br>顯示 `JwtSessionStoreRedis` 的儲存邏輯。<br>動畫：Token 簽名驗證通過 -> 查詢 Redis Session 狀態 -> 驗證通過。 | 作為安全架構的最底層，`sdk-auth-jwt` 封裝了 JWT 的核心技術，負責 Token 的解析、驗證與簽發。傳統 JWT 最大的痛點在於無法即時撤銷——一旦簽發，在過期前都有效，這在金融場景下存在巨大的安全隱患。傳統做法通常依賴維護黑名單或設定極短的有效期，但這不僅增加了開發與運維的複雜性，更會讓驗證過程重新變回「有狀態」，抵消了 JWT 原有的架構優勢。為此，我們在 Gateway 上啟用了「混合驗證模式」。透過集成 `JwtSessionStore`，在驗證簽名的同時快速檢查 Redis 中的 Session 狀態。這種設計讓我們既享受無狀態驗證的高效能，又能具備隨時將風險用戶踢下線的能力，完美兼顧了效能與安全性。 | |
|      |                                                                                                                                           |                                                                                                                                                                                                                                                        |      |

#### 4.2 應用接入 (Application Integration)

**模組：** `sdk-auth`
**職責定位：** 定義通用的權限註解、上下文接口與攔截器，提供給 Gateway 與各微服務無感接入的能力。

| 時間   | 畫面 (Visual)                                                                                                                                                                        | 旁白腳本 (Audio)                                                                                                                                                                                                                                                                                                | 執行建議 |
| :--- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :--- |
| 4:40 | **[多重權限註解與適配]**<br>顯示 `@PublicAPI`, `@PrivateAPI`, `@Jwt` 註解。<br>顯示一個 Controller 方法同時標記 `@Jwt` 與 `@PrivateAPI`。<br>圖示：App (JWT) 與 內部服務 (API Key) 調用同一接口 -> 驗證通過。 | `sdk-auth` 提供了全套標準權限註解：`@PublicAPI`、`@PrivateAPI` 與 `@Jwt`。這套設計的核心優勢在於支持「單一接口、多種憑證適配」。在過去，為了讓同一個業務邏輯同時兼容前端 App (JWT) 與內部服務 (API Key) 的調用，我們往往被迫開設不同的接口，這不僅導致了接口膨脹與代碼重複，更增加了維護成本。現在，開發者只需在同一個方法上標記對應的註解，SDK 就會自動識別並驗證任一合法的憑證。驗證通過後，用戶身份資訊會自動注入到 `UserContext` 中，讓業務開發者能以無感的方式獲取上下文，徹底解決了過往 Public 與 Private 接口冗餘的問題。 |      |

#### 4.3 認證中心 (Authentication Center)

**模組：** `sdk-auth-server`
**職責定位：** 專為 Auth 服務設計，負責 Token 的簽發、生命週期管理與標準登入流程，實現認證邏輯的統一治理。

| 時間   | 畫面 (Visual)                                                                                                                                | 旁白腳本 (Audio)                                                                                                                                                                                                                                                              | 執行建議 |
| :--- | :----------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | :--- |
| 4:55 | **[快速構建認證中心]**<br>顯示 Auth 服務引用 `sdk-auth-server` 的 pom.xml。<br>顯示 `UserDetailsService` 實作類別。<br>顯示 `/login`, `/logout`, `/refresh` 接口調用日誌。 | 位於架構頂層的是 `sdk-auth-server`，它是專為構建認證中心而生的核心模組。透過 Spring Boot 自動裝配，Auth 服務只需引入此模組，並實作 `UserDetailsService` 接口來對接帳戶資料，再加上自定義的註冊邏輯，就能快速搭建起一個完整的認證中心。它提供並管理了 `/login`、`/logout` 與 `/refresh` 等標準接口。這種設計不僅確保了全系統驗證邏輯的高度統一，更將複雜的安全配置封裝在 SDK 內部，讓開發者能專注於註冊、用戶權限與業務邏輯的實現。 |      |

---

## 5. Microservice Stack: 溝通與治理 (Communication & Governance)

**模組：** `sdk-spring-cloud-gateway`, `sdk-spring-mvc`, `sdk-spring-cloud-openfeign`, `sdk-spring-cloud-alibaba-nacos`
**核心價值：** 實現全鏈路的上下文透傳、統一的 API 契約與動態的服務治理。

#### 5.1 Spring MVC: 標準化 Web 層 (Standardized Web Layer)

**模組：** `sdk-spring-mvc`
**職責定位：** 統一所有微服務的 Web 層行為，確保請求處理的一致性。

| 時間   | 畫面 (Visual)                                                                                                                                                          | 旁白腳本 (Audio)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | 執行建議 |
| :--- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :--- |
| 5:30 | **[攔截器與異常處理]**<br>顯示 `RequestCorrelationFilter` 注入 TraceID。<br>顯示 `CookieConfig` 配置。<br>顯示 `OpenRestExceptionAdvice` 捕捉異常。<br>顯示 `OpenHttpUtils.resolveBearerToken`。 | `sdk-spring-mvc` 是所有微服務 Web 層的標準基石。它預設啟用了 `RequestCorrelationFilter`，負責傳遞或自動生成唯一的 Request ID 與 Trace ID，並同步至 MDC 日誌中。在效能優化上，它自動開啟了 **Gzip 壓縮**並透過 **ShallowEtag** 實現前端緩存優化以節省頻寬。數據處理方面，它統一了 **UTF-8 編碼**與 `sdk-core` 標準的 `ObjectMapper` 配置，並對所有輸入字串自動執行 **Trim** 處理與 **Date/Time 格式**標準化。在安全性上，則統一配置了 **Cookie 安全策略** (HttpOnly/Lax) 以防止安全隱患。最重要的是，透過 `GlobalExceptionHandler`，我們實現了全域異常的自動捕獲與多語系 (I18n) 轉換，並在回應中帶入 Trace ID 等上下文資訊，確保無論發生何種錯誤，前端收到的永遠是標準化的 JSON 結構與可讀的錯誤訊息。此外，內建的 `OpenHttpUtils` 讓開發者能便捷地獲取 Token，無需處理繁瑣的 Servlet API。 | |
|      |                                                                                                                                                                      |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |      |

#### 5.2 OpenFeign: 服務間通訊 (Inter-service Communication)

| 時間   | 畫面 (Visual)                                                                                                                               | 旁白腳本 (Audio)                                                                                                                                                                                                                  | 執行建議 |
| :--- | :---------------------------------------------------------------------------------------------------------------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :--- |
| 5:45 | **[上下文透傳與調用封裝]**<br>顯示 `DefaultFeignRequestInterceptor`。<br>顯示 `OpenApiClientInvoker.call`。<br>日誌：Feign DEBUG 模式下清晰的 Request/Response 詳情。 | 當微服務之間需要溝通時，`sdk-spring-cloud-openfeign` 提供了全方位的支援。它內建的攔截器能自動將 Trace ID、Request ID、用戶語言偏好、JWT 以及 API Key 無感透傳給下游服務，確保業務上下文絕不丟失。開發體驗上，我們提供了 `OpenApiClientInvoker` 工具類，封裝了遠端調用的狀態效驗與結構效驗，讓開發者能直接取得業務數據內容。統一處理Feign Exception 。 |      |

#### 5.3 邊界治理與動態配置 (Gateway & Nacos)

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) | 執行建議 |
| :--- | :--- | :--- | :--- |
| 6:00 | **[Gateway 封裝與 Nacos 統一配置]**<br>顯示 Gateway 專案極簡的代碼結構。<br>顯示 Nacos 上簡潔的配置列表。 | 對於邊界治理，`sdk-spring-cloud-gateway` 將驗證、日誌與安全配置等核心邏輯全部封裝在 SDK 內部。這讓 Gateway 服務變得極其輕量：開發者只需引用 SDK，即可專注於服務路由的定義，而無需重複開發通用的網關邏輯。與此同時，`sdk-spring-cloud-alibaba-nacos` 則實現了配置的統一管理。它標準化了所有微服務的接入方式，大幅簡化了繁瑣的本地配置，讓開發者能透過 Nacos 介面高效地管理全系統參數。 | |

---

## 6.  Resilience: 效能與韌性

**模組：**  `sdk-library-resilience4j`
**核心價值：** 系統的自我保護能力。

#### 6.2 Resilience: 熔斷與限流 (Circuit Breaker & Rate Limiter)

| 時間   | 畫面 (Visual)                                                                                                                                                      | 旁白腳本 (Audio)                                                                                                                                                                                                                                     | 執行建議 |
| :--- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :--- |
| 5:45 | **[Resilience4j 統一配置]**<br>顯示 `CircuitBreakerConfig` 代碼。<br>顯示 `sdk-library-resilience4j-defaults.yaml` 預設配置。<br>動畫：方法上標記 `@CircuitBreaker` -> 請求失敗率飆升 -> 熔斷器打開。 | 在分佈式系統中，任何一個微服務的故障都可能引發雪崩。`sdk-library-resilience4j` 提供了一套經過驗證的統一熔斷與限流配置模板。開發者無需自行鑽研複雜的閾值設定，只需在調用 DB 或外部 API 的方法上加上 `@CircuitBreaker(name = "default")` 註解，即可直接套用這組標準策略。當錯誤率達到預設的 50% 時，系統會自動切斷該方法的請求，防止故障擴散。這種「配置即代碼」的設計，讓強大的自我保護機制變得像開關一樣簡單易用。 |      |

---

## 7. 總結 (Wrap-up)

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) | 執行建議 |
| :--- | :--- | :--- | :--- |
| 6:00 | **[回到 SDK 全景圖]**<br>文字浮現：**Standardization (標準化)**, **Efficiency (效率)**, **Quality (品質)**。 | 總結來說，這套 SDK 實現了「依賴即治理」。它讓業務團隊能專注於核心邏輯，同時確保了整個 Open Exchange Core 系統具備金融級的穩定性與可維護性。 | |
