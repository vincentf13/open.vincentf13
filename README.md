Open Exchange Core 是一個面向 百萬級 TPS 與 微秒級延遲 需求而設計的高效能分散式金融撮合核心系統。

🎯 專案定位

在現代交易所系統中，數據一致性、極低延遲 與 可觀測性 是三大核心挑戰。本專案不只是一個撮合引擎，更是一套完整的金融技術解決方案。

🚀 核心優勢
1. 極致效能與一致性
Matching Engine: 基於 LMAX Disruptor 模式，實現微秒級確定性撮合。
Flip Protocol: 針對交易場景優化的自研分散式協議，確保在極限負載下的數據強一致性。
Double-Entry Accounting: 內置金融級雙分錄帳務系統，每一筆資產變動皆可追蹤且即時平衡。

2. 雲原生自動化運維
10-Min Deployment: 提供完整的 Helm Charts，支援在 Kubernetes 環境下 10 分鐘完成全棧集群搭建。
Observability Stack: 預整合 Grafana 與 Prometheus，開箱即用的監控面板。

🛠️ 技術選型
核心語言: Java 17+ (Performance Oriented)
基礎設施: Kubernetes (K8s) / Docker
數據流轉: Apache Kafka (Sequential Messaging)
前端展示: TypeScript / Next.js (Admin Dashboard)

📺 專案導覽與實戰 (YouTube)
歡迎觀看我的頻道獲取更多技術細節與實機展示：

🎞️ 交易所架構百萬 TPS 的秘密：Flip Protocol 解析
🎞️ 實戰監控：打造高效能全棧監控 SDK 與指標治理
🎞️ Web Demo：即時資產負債表與撮合系統操作演示
