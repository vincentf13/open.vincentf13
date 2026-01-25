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

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) | 執行建議 |
| :--- | :--- | :--- | :--- |
| 1:20 | **[ID 與 Mapper]**<br>顯示 `Snowflake` ID 生成代碼。<br>顯示 `ObjectMapperConfig` (Fail on primitive null, BigDecimal)。 | 在數據層面，我們整合了 **Yitter 雪花算法** 生成全局唯一 ID。同時，針對金融數據的敏感性，我們嚴格定制了 Jackson 的序列化規則：強制檢查重複 Key、禁止 null 轉 0、並確保 BigDecimal 的精度在傳輸過程中絲毫無損。 | |
| 1:40 | **[審計與驗證]**<br>顯示 `OpenObjectDiff.diff(old, new)` 的結果 JSON。<br>顯示 `OpenValidator` 的 Group 校驗代碼。 | 為了滿足審計需求，我開發了 **`OpenObjectDiff`** 工具，能高效比對物件差異並生成 Delta JSON，這在記錄操作日誌時極為強大。此外，透過 `OpenValidator` 的 Group 分組設計，我們能在 CRUD 不同階段，對 Domain、DTO 與持久化物件執行精確且標準化的參數校驗。 | |

---

## 2. SDK Core Test: 整合測試標準化 (Standardized Testing)

**模組：** `sdk-core-test`
**核心價值：** 透過容器化技術，讓整合測試環境與生產環境高度一致 (Production-Like)。

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) | 執行建議 |
| :--- | :--- | :--- | :--- |
| 1:55 | **[IDE 程式碼：BaseIntegrationTest]**<br>顯示一個繼承自 `BaseIntegrationTest` 的業務測試類別。<br>Highlight `@Testcontainers` 註解。 | 在微服務測試中，Mock 外部依賴往往會掩蓋真實問題。`sdk-core-test` 深度整合了 **TestContainers**。開發者只需要繼承 `BaseIntegrationTest`... | |
| 2:10 | **[動畫：容器啟動]**<br>畫面下方 Terminal 快速滾動。<br>依序亮起圖示：🐬 MySQL, 🧊 Redis, 📨 Kafka (Redpanda)。<br>顯示測試通過的綠色勾勾。 | ...SDK 就會自動在 Docker 中拉起真實的 MySQL、Redis 與 Kafka 實例，並自動注入 Spring Context。這讓開發者能在本地跑通包含完整資料庫與消息隊列交互的整合測試，確保代碼在部署前就擁有生產級的可靠性。 | |

---

## 3. Infra: 駕馭中間件 (Taming Middlewares)

**模組：** `sdk-infra-kafka`, `sdk-infra-mysql`, `sdk-infra-redis`
**關鍵字：** 批次消費 (Batch Consumer), 死信隊列 (DLQ), 分佈式鎖 (Redisson), 自動填充 (MyBatis Plus)

*   **待補完內容：**
    *   **Kafka**: `BatchMessagingMessageConverter` 解決 JSON 二次編碼，`ErrorHandlerFactory` 統一重試策略。
    *   **Redis**: Redisson 分佈式鎖的封裝，以及自定義的序列化配置。
    *   **MySQL**: MyBatis Plus 的 `MetaObjectHandler` 自動維護 `created_time`, `updated_time`。

---

## 4. Auth: 金融級安全架構 (Security Architecture)

**模組：** `sdk-auth`, `sdk-auth-jwt`, `sdk-auth-server`
**關鍵字：** 無狀態認證 (Stateless Auth), JWT 簽發與解析, 上下文傳遞 (ThreadLocal)

*   **待補完內容：**
    *   架構職責分離：`auth-server` (發證) vs `auth-jwt` (驗證)。
    *   `UserContext` 的設計：如何在內部服務間無感傳遞用戶資訊 (ThreadLocal)。
    *   Gateway 層的統一鑑權攔截。

---

## 5. Microservice Stack: 溝通與治理 (Communication & Governance)

**模組：** `sdk-spring-cloud-gateway`, `sdk-spring-mvc`, `sdk-spring-cloud-openfeign`, `sdk-spring-cloud-alibaba-nacos`
**關鍵字：** 統一響應體 (Result<T>), 全局異常處理 (Global Exception Handler), TraceId 透傳, 服務發現

*   **待補完內容：**
    *   **MVC**: `GlobalExceptionHandler` 與 `Result<T>` 統一 API 格式。
    *   **Feign**: RequestInterceptor 如何自動將 Header (如 TraceId, UserID) 傳遞給下游。
    *   **Gateway**: GlobalFilter 實現的統一入口邏輯。
    *   **Nacos**: 統一的配置中心載入邏輯。

---

## 6. Utilities & Resilience: 效能與韌性 (Efficiency & Resilience)

**模組：** `sdk-devtool`, `sdk-library-resilience4j`
**關鍵字：** 自動文檔 (Swagger/OpenAPI), 熔斷限流 (Circuit Breaker/Rate Limiter)

*   **待補完內容：**
    *   **DevTool**: 如何自動掃描 Controller 生成標準化 API 文件。
    *   **Resilience4j**: 統一的熔斷配置策略 (CircuitBreakerConfig)，防止雪崩效應。

---