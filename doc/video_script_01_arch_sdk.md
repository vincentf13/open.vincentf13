# Open Exchange Core - 技術展示系列 Ep.1：系統架構與 SDK 設計

**影片長度：** 約 1分30秒 - 2分鐘
**核心亮點：** 微服務解耦、高併發支撐能力、統一架構治理 (Governance)

---

## 1. 系統架構總覽 (Architecture Overview)

**目標：** 展示系統如何應對高併發流量，以及服務拆分的邏輯。

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) | 執行建議 |
| :--- | :--- | :--- | :--- |
| 0:00 | **[動態架構圖]**<br>畫面中央是流量入口 Gateway。<br>後方連接四大核心服務：<br>1. **Matching** (撮合)<br>2. **Asset** (資產/帳務)<br>3. **Risk** (風控)<br>4. **Market** (行情) | 這是 Open Exchange Core，一個專為高頻交易設計的虛擬貨幣交易所核心。為了應對每秒數萬筆的下單流量，我們採用了完全解耦的微服務架構。 | 使用 Draw.io 或 Keynote 製作，強調服務間的邊界。 |
| 0:20 | **[擴展性演示動畫]**<br>當流量上升時，**Matching** 服務方塊分裂成多個實例 (Replica)，標註 "Horizontal Scaling"。<br>Asset 服務保持穩定。 | 這種架構最關鍵的優勢在於「獨立擴展」。當市場行情劇烈波動時，我們可以針對計算密集的「撮合引擎」進行橫向擴容，而不會影響到負責資金安全的資產結算服務，確保了系統在高負載下的穩定性。 | |

---

## 2. SDK 設計與架構治理 (SDK Design & Governance)

**目標：** 解釋如何管理數十個微服務，並展示「Spring Boot Starter」的封裝能力。

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) | 執行建議 |
| :--- | :--- | :--- | :--- |
| 0:40 | **[IDE 專案結構特寫]**<br>展開 Project View，聚焦在 `sdk` 模組目錄：<br>- `sdk-core`<br>- `sdk-infra-kafka`<br>- `sdk-infra-redis`<br>- `sdk-auth` | 然而，在微服務架構中，如何維護數十個服務的程式碼一致性是一大挑戰。為了避免重複造輪子，我設計並實作了一套標準化的 SDK。 | 實際錄製 IntelliJ IDEA 的畫面，展開 `sdk` 資料夾。 |
| 0:55 | **[程式碼展示 - 依賴管理]**<br>打開一個業務服務 (如 `exchange-order`) 的 `pom.xml`。<br>高亮顯示引用 `<artifactId>sdk-infra-kafka</artifactId>`。 | 這些 SDK 基於 Spring Boot Starter 機制封裝。對於業務開發者來說，只需要引入一個 Maven 依賴... | |
| 1:10 | **[程式碼展示 - 自動配置]**<br>快速切換到 SDK 內部的 `AutoConfiguration` 類別。<br>展示 `@Bean` 配置、攔截器 (Interceptor) 設定。 | ...所有的底層複雜度，包括 Kafka 的序列化配置、Redis 連接池優化、分佈式追蹤 (Tracing) 埋點、以及統一的 Security 認證，都會自動完成配置。 | 這邊要展示 SDK 原始碼，證明是自研封裝而非單純引用。 |
| 1:25 | **[畫面分割：左邊 Code / 右邊 Log]**<br>左邊是簡單的 Controller 程式碼。<br>右邊是格式整齊統一的 JSON Log (包含 TraceId, SpanId)。 | 這不僅大幅提升了開發效率，更重要的是，它強制統一了全站的日誌格式與監控標準，讓問題排查變得精準且高效。 | 強調「規範」與「治理」的價值。 |

---

## 3. 總結 (Wrap-up)

| 時間 | 畫面 (Visual) | 旁白腳本 (Audio) | 執行建議 |
| :--- | :--- | :--- | :--- |
| 1:40 | **[回到全景架構圖]**<br>在所有微服務底部，出現一層穩固的基座，標註 "Shared SDK Layer"。 | 透過 SDK 負責底層治理，業務服務專注核心邏輯。這就是 Open Exchange Core 構建高可靠金融系統的基石。 | |

---

## 準備工作清單

1.  **IDE 準備**：
    *   確保 `sdk` 專案編譯通過，無紅色錯誤。
    *   準備好 `sdk-infra-kafka` (或類似) 的 `AutoConfiguration` 程式碼。
    *   準備好 `exchange-order` 的 `pom.xml`。
2.  **架構圖**：
    *   需要一張清晰的微服務方塊圖，最好能表達出「撮合」與「資產」分開的概念。