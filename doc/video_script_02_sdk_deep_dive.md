# Open Exchange Core - 技術展示系列 Ep.2：SDK 設計與架構治理深度解析

**影片長度：** 約 2.5 - 3 分鐘
**核心目標：** 展示如何透過模組化的 SDK 實現大規模微服務的治理，強調「約定優於配置」與「統一標準」。

---

## 0. 開場 (Intro)

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) |
| :--- | :--- | :--- |
| 0:00 | **[SDK 全景圖]**<br>畫面中央是 `sdk` 父模組，四周發散出子模組圖示：<br>🛡️ Auth<br>🛠️ Core<br>📨 Kafka<br>💾 Infra (MySQL/Redis)<br>🌐 Gateway/MVC | 在微服務架構中，如果沒有強力的治理手段，系統很快就會變成一盤散沙。為了確保 Open Exchange Core 數十個服務的一致性與品質，我設計了一套全方位的 SDK 解決方案。 |

---

## 1. 核心基礎設施 (Core & Test)

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) |
| :--- | :--- | :--- |
| 0:20 | **[代碼特寫：OpenLog.java]**<br>顯示 `OpenLog.info(Event, KV)` 的調用。<br>Highlight `StackWalker` 的代碼片段。<br>右側顯示整齊劃一的 JSON Log。 | 首先是 `sdk-core`。我們統一了全站的日誌標準，開發者無需關心格式，只需傳入事件與鍵值對。底層透過 `StackWalker` 自動定位呼叫來源，大幅簡化了代碼，同時保證了日誌的可解析性，為後續的 ELK 分析打好基礎。 |
| 0:40 | **[測試框架：sdk-core-test]**<br>顯示一個 `IntegrationTest` 類別，繼承自 SDK 提供的 Base Test。<br>一鍵啟動 TestContainers (MySQL/Kafka)。 | 而在 `sdk-core-test` 中，我們封裝了 TestContainers 與 JUnit 5。開發者只需要一個註解，就能自動拉起真實的 MySQL 與 Kafka 容器進行整合測試，確保了測試環境與生產環境的高度一致。 |

---

## 2. 基礎設施封裝 (Infra: Kafka, MySQL, Redis)

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) |
| :--- | :--- | :--- |
| 0:55 | **[Kafka 深度封裝]**<br>顯示 `ConfigKafkaConsumer.java`。<br>Highlight `BatchMessagingMessageConverter` 與 `ErrorHandlerFactory`。<br>動畫顯示：Message -> Convert -> Retry -> DLQ。 | 針對消息隊列，`sdk-infra-kafka` 提供了開箱即用的 Batch 消費模式與 Dead Letter Queue (DLQ) 機制。我們還特別處理了 JSON 二次編碼問題，並內建了統一的錯誤重試策略，讓業務開發者無需為 Kafka 的複雜配置煩惱。 |
| 1:15 | **[數據持久層]**<br>快速切換 `sdk-infra-mysql` (MyBatis Plus 配置) 與 `sdk-infra-redis` (Redisson 分佈式鎖)。 | 對於 MySQL 與 Redis，我們分別封裝了 MyBatis Plus 的自動填充邏輯與 Redisson 的分佈式鎖實現。這不僅減少了重複代碼，更統一了資料庫的 Audit 欄位維護與緩存的序列化標準。 |

---

## 3. 安全與認證 (Auth & Security)

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) |
| :--- | :--- | :--- |
| 1:30 | **[安全架構圖]**<br>Gateway (Auth-JWT 解析) -> 內部服務 (傳遞 UserContext)。<br>顯示 `sdk-auth-server` 發放 Token 的流程。 | 安全性是金融系統的命脈。`sdk-auth` 模組實現了基於 JWT 的無狀態認證。我們區分了 `sdk-auth-server`（負責發放）與 `sdk-auth-jwt`（負責解析），確保只有 Gateway 需要處理繁重的驗簽工作，內部服務則透過 ThreadLocal 無縫傳遞用戶上下文。 |

---

## 4. 微服務生態 (Gateway, MVC, OpenFeign, Nacos)

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) |
| :--- | :--- | :--- |
| 1:50 | **[Spring Cloud 整合]**<br>顯示 `sdk-spring-mvc` 的 `GlobalExceptionHandler`。<br>顯示 `sdk-spring-cloud-openfeign` 的攔截器 (傳遞 TraceId)。 | 在微服務通訊層面，`sdk-spring-mvc` 統一了全站的 API 回應結構與異常處理。同時，我們透過 SDK 對 OpenFeign 與 Gateway 進行了增強，實現了 Trace ID 的自動透傳，確保了調用鏈路的可追蹤性。 |
| 2:10 | **[DevTool 與 Resilience]**<br>顯示 `sdk-devtool` 的 Swagger/OpenAPI 自動生成。<br>顯示 `sdk-library-resilience4j` 的熔斷配置。 | 最後，我們還提供了 `sdk-devtool` 自動生成 API 文件，以及 `sdk-library-resilience4j` 提供統一的熔斷與限流配置。這些模組共同構成了一個強大的微服務治理底座。 |

---

## 5. 總結 (Wrap-up)

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) |
| :--- | :--- | :--- |
| 2:30 | **[回到 SDK 全景圖]**<br>文字浮現：**Standardization (標準化)**, **Efficiency (效率)**, **Quality (品質)**。 | 總結來說，這套 SDK 實現了「依賴即治理」。它讓業務團隊能專注於核心邏輯，同時確保了整個 Open Exchange Core 系統具備金融級的穩定性與可維護性。 |

---

## 準備工作清單 (Action Items)

1.  **代碼準備**：
    *   `OpenLog.java`: 展示 `resolveCallerClass` 方法。
    *   `ConfigKafkaConsumer.java`: 展示 `ErrorHandlerFactory`。
    *   `GlobalExceptionHandler.java`: 展示統一異常回傳 `Result<T>`。
2.  **圖表**：
    *   繪製一張 SDK 依賴樹狀圖，視覺上很有衝擊力。
