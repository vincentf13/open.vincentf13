# 專案亮點：全鏈路可觀測性與監控體系 (Observability & Monitoring)

## 履歷專案描述 (Project Description for Resume)

 標題：High-Performance Exchange Observability
  (高性能交易所全鏈路可觀測性)

  1. Standardization (標準化)
   * Unified SDK Design: Built a Spring Boot Starter for one-dependency integration.
   * Metric Governance: Enforced consistent naming & tagging via ExchangeMetric Enum.
   * Result: Zero-config monitoring for all microservices.

  2. Business & Performance (業務與效能)
   * Core Flow Tracking: Order QPS (by status), Matching TPS, and Latency (P99/Max).
   * Thread Saturation: Deep dive into Matching Engine’s Queue Depth & Active Threads.
   * Impact: Instantly identified "Hot Symbol" bottlenecks during load testing.

  3. Architecture Vision (架構視野)
   * Layered Dashboards: Separated views for Operations (Business Health) & Developers (System Internals).
   * Future Roadmap: Full-stack correlation linking Business Anomalies to Node/Pod Infrastructure metrics.

  ---

  中文對照版 (如果你的受眾偏好中文)

  標題：高性能交易所全鏈路監控體系

  1. 標準化 SDK (Standardization)
   * 開箱即用: 封裝 Spring Boot Starter，單一依賴自動接入 Prometheus/Grafana。
   * 指標治理: 透過 ExchangeMetric Enum 統一全系統指標命名與標籤規範。

  2. 深度業務觀測 (Business & Performance)
   * 核心鏈路: 監控訂單 QPS 狀態分佈、撮合 TPS 與長尾延遲 (Max Latency)。
   * 瓶頸定位: 深度觀測撮合引擎執行緒池的任務佇列 (Queue Depth) 與飽和度。
   * 實戰價值: 在壓測中精準定位單一熱點交易對導致的效能阻塞。

  3. 架構與規劃 (Vision)
   * 分層視覺化: 為產品運營 (Business View) 與開發人員 (System View) 設計專屬視圖。
   * 全棧關聯: 規劃從業務異常垂直下鑽至 Node/Pod 基礎設施的關聯診斷體系。

  ---

  製作建議
   * 排版: 使用三個並列的區塊（Column），每個區塊對應一個主題。
   * 配圖: 如果有空間，可以在右側或下方放一張你的 Grafana Dashboard 截圖（展現 QPS 曲線或 Thread Pool 狀態），視覺衝擊力會很強。
   * 口語: 配合影片，按照這三點依序展開，時間控制在 1-2 分鐘內最佳。

---

## 面試口述腳本 (Interview Script)

當面試官問到：「你在這個專案中負責的監控部分是如何設計的？」或「你是如何保證系統穩定性的？」時，可以參考以下回答邏輯。

### 1. 開場：為什麼要做這個 SDK？ (Why SDK?)

首先，**最核心的優勢是『開箱即用』的開發體驗。** 我將 SDK 設計成 Spring Boot Starter 的形式，所有的業務微服務只需要在 `pom.xml` 中引入這個依賴，系統就會自動完成 MeterRegistry 的初始化、配置通用標籤 (如 `app`, `env`)，並自動開啟 Prometheus 指標接口。這意味著新服務一啟動，其業務指標與系統指標就能**自動出現在 Grafana 儀表盤上**，完全不需要開發者手動配置基礎設施，極大地提升了團隊的開發效率。"



第二部分："在微服務架構下，如果讓每個服務各自隨意埋點，很快就會面臨指標命名混亂、Tag 定義不一致的問題，導致 Grafana 儀表盤難以維護。

因此，我首先開發了一個**通用的監控 SDK**。我利用 Java 的 Enum (枚舉) 特性，設計了 `ExchangeMetric` 這樣的統一管理類別。開發者在使用時，不需要手打字串，而是直接引用 Enum，這樣強制規範了指標名稱和必要的 Tag (例如 `symbol`, `type`)。

第三部分：


### 2. 核心：業務監控與 Thread Pool 深度觀測 (Business & Performance)

"除了標準的 CPU 和記憶體監控，我將監控重心放在**關鍵業務指標**與**組件效能**的深度關聯上。

以交易所最核心的交易鏈路為例，我實作了以下幾個維度的監控：

*   **訂單流 QPS (按狀態分佈)**：不只是監控請求量，我還細分了訂單的處理狀態（如 Success, Rejected, Cancelled）。這讓我們能即時發現是否因為特定風控規則觸發，導致異常的拒單率飆升。
*   **撮合引擎效能 (TPS & Latency)**：實作了每秒撮合筆數 (TPS) 與精確到微秒級的延遲監控。我們不僅看平均延遲，更關注 **Max Latency**，這對於找出撮合過程中的『長尾效應』至關重要。
*   **執行緒池飽和度觀測 (Thread Pool Saturation)**：針對單執行緒模型的撮合引擎，我深度監控了其**任務佇列深度 (Queue Depth)** 與**活躍狀態**。在壓測期間，這幫助我們定位到：延遲增加並非 CPU 不足，而是特定熱點交易對導致任務在佇列中堆積。

此外，對於系統的完整性，我也已經規劃了**帳戶資產變動率 (Account Dynamics)** 的監控。這是我認為提升金流透明度的核心指標——透過觀測帳戶餘額的變動速率，我們能第一時間發覺潛在的異常交易或風控風險。這將會是我們下一階段強化系統『自我審計能力』的重要環節。

這種深度業務埋點，讓監控不再只是看系統『活著沒』，而是能預判業務『健康不健康』。"

### 3. 落地：K8s 與分層視覺化 (Visualization & Layered Design)

"在運維與視覺化層面，我基於 Prometheus 與 Grafana 設計了**分層的監控儀表盤 (Layered Dashboards)**，以滿足不同角色的需求：

*   **Business View (給產品與運營)**：關注核心業務健康度，例如**下單 QPS**、**即時成交量**、**活躍用戶數**。這讓非技術人員也能一眼掌握業務動態。
*   **System View (給開發人員)**：深入技術細節，監控**撮合引擎的 Thread Pool 狀態**、**GC 頻率**、**API 響應延遲 (P99/P95)**。這讓我們在發生問題時能快速定位是代碼層級還是資源層級的問題。

此外，雖然目前專案主要聚焦在應用層監控，但我對**全棧可觀測性**有清晰的規劃。在未來的生產環境落地中，我計畫引入 **Node Exporter** 與 **Kube-State-Metrics** 來採集 Pod 與 Node 的底層指標。

這將賦予我們從『業務異常』到『基礎設施瓶頸』的**垂直關聯診斷能力**。舉個實際場景：當業務面板顯示撮合引擎延遲飆升時，我們能立即下鑽到 Pod 層級，確認是否因為 **Memory Limit 設置不當導致頻繁 GC** (甚至預防 OOMKilled)，或是觀察 Node 層級的 **Disk I/O 瓶頸** 是否影響了 WAL 的寫入速度。這種全鏈路的監控視野能大幅縮短平均故障修復時間 (MTTR)，並為後續的系統擴容與資源優化 (Resource Quotas) 提供精確的數據支撐。"

---

## 關鍵技術點 (Key Technologies)

*   **Java / Spring Boot**: 核心開發語言。
*   **Micrometer**: 應用層指標門面 (Facade)。
*   **Prometheus**: 時序資料庫與指標採集。
*   **Grafana**: 數據視覺化與儀表盤設計。
*   **Kubernetes (K8s)**: 容器化部署與 ServiceMonitor 配置。
*   **Thread Pool Monitoring**: 針對 `ThreadPoolExecutor` 的深度指標採集。
