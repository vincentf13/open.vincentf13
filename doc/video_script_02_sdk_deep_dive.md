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
| 3:25 | **[Cluster 優化與 TTL 抖動]**<br>顯示 `setBatchCluster` 的 Slot 分組邏輯。<br>顯示 `RedisUtil.withJitter`。 | 針對 Redis Cluster 的效能瓶頸，我們特別實現了**自動槽位 (Slot) 路由的批次查詢與寫入**，大幅提升了集群模式下的吞吐量。同時，我們在所有緩存寫入中強制引入了 **TTL 抖動 (Jitter)** 機制，從架構層面杜絕了緩存雪崩的風險。 | |
| 3:40 | **[Redisson 分佈式鎖模板]**<br>顯示 `OpenRedissonLock.withLock` 調用。 | 對於複雜的分佈式鎖場景，我們透過 `OpenRedissonLock` 提供了簡潔的 `withLock` 模板。它自動處理了鎖的獲取、續約與釋放，並透過 Watchdog 機制確保業務執行的安全性，將分佈式競爭的處理難度降到了最低。 | |

#### 3.3 Kafka: 端到端的訊息治理 (End-to-End Message Governance)

**設計哲學：** 統一序列化標準與自動化容錯，拒絕黑盒操作。

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) | 執行建議 |
| :--- | :--- | :--- | :--- |
| 3:55 | **[統一配置與生產者]**<br>顯示 Kafka 自動配置類別 (ObjectMapper 注入)。<br>顯示 `OpenKafkaProducer` 發送代碼。<br>Log: `Kafka Send Success`。 | 對於 Kafka，我們接管了生產與消費兩端的配置，強制使用 `sdk-core` 標準化的 ObjectMapper 進行序列化。生產端透過 `OpenKafkaProducer` 封裝了異步發送邏輯，內建了參數校驗與發送日誌記錄，確保消息在源頭就是正確且可追蹤的。 | |
| 4:10 | **[智慧消費者與容錯]**<br>顯示 Consumer 接收 JSON 自動轉為 Event 物件。<br>動畫：重試 (1s -> 2s -> 4s) -> DLQ。<br>Log: `Consume Failed` -> `Retry` -> `DLQ`。 | 消費端則實現了高度自動化：SDK 能將 JSON 自動轉換為強型別的 Event 物件，並自動記錄接收、成功與失敗的日誌信息。更關鍵的是，我們內建了**指數退避 (Exponential Backoff) 重試機制**。當重試超過三次仍失敗時，消息會被自動路由到 Dead Letter Queue (DLQ)，實現了完整的容錯閉環，讓開發者無需操心底層的異常處理。 | |

---

## 4. Auth: 金融級安全架構 (Security Architecture)

**模組：** `sdk-auth`, `sdk-auth-jwt`, `sdk-auth-server`
**核心價值：** 實現無狀態架構的高效驗證，同時保留對會話 (Session) 的絕對控制權。

#### 4.1 無狀態架構與職責分離 (Stateless & Separation)

**設計哲學：** 簽發與驗證分離，減少單點瓶頸。

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) | 執行建議 |
| :--- | :--- | :--- | :--- |
| 4:00 | **[Auth 架構圖]**<br>圖示：`Auth Server` (發證) -> 用戶 -> `Gateway` (驗證)。<br>顯示 RSA 公私鑰圖示：Server 私鑰簽名，Gateway 公鑰驗證。 | 在安全架構上，我們採用了標準的 OAuth2 思想但進行了輕量化設計。`sdk-auth-server` 負責持有私鑰簽發 JWT，而 Gateway 與各微服務則透過 `sdk-auth-jwt` 使用公鑰進行無狀態驗證。這種職責分離確保了認證中心不會成為高併發流量下的瓶頸。 |

#### 4.2 可撤銷的 JWT 與混合模式 (Revocable JWT & Hybrid Mode)

**設計哲學：** 解決 JWT 無法撤銷的痛點，實現即時踢人下線。

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) | 執行建議 |
| :--- | :--- | :--- | :--- |
| 4:20 | **[Redis Session Check]**<br>顯示 `JwtFilter` 中的 `checkSessionActive` 邏輯。<br>動畫：用戶 Token 有效，但 Redis 中 Session 狀態為 Invalid -> 拒絕請求。 | 純粹的 JWT 雖然高效，但最大的缺點是無法即時撤銷。為此，我們在 `sdk-auth` 中引入了「混合模式」。在關鍵的金融操作中，Filter 會額外檢查 Redis 中的 Session 狀態。這讓我們既享受了 JWT 的無狀態便利，又保留了在發現風險時能立即將用戶「踢下線」的控制權。 |

#### 4.3 內部傳遞與開發體驗 (Internal Propagation & DX)

**設計哲學：** 安全對業務開發者透明，像寫本地代碼一樣獲取用戶資訊。

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) | 執行建議 |
| :--- | :--- | :--- | :--- |
| 4:40 | **[ThreadLocal 與註解]**<br>顯示 `UserContext.getUserId()` 的調用。<br>顯示 `@PrivateAPI` 與 `@PublicAPI` 註解在 Controller 上。 | 對於內部服務調用，SDK 利用 **ThreadLocal** 實現了用戶上下文的無感傳遞。開發者在任何一層代碼中，只需調用 `UserContext.get()` 即可獲取當前用戶資訊。同時，我們提供了 `@PrivateAPI` 與 `@PublicAPI` 註解，讓接口權限控制變得像宣告變數一樣簡單直觀。 |

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
