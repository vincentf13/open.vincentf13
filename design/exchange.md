# Exchange 服務架構

## 模組與目錄結構
```
open.vincent13/
└── services/
     └── exchange/
        ├── gateway/           # API Gateway/BFF 層
        ├── risk-margin/       # 保證金、風控限額、下單前校驗
        ├── matching/          # 撮合引擎（建議獨立進程與語言）
        ├── account-ledger/
        │   ├── interface/     # REST Controller、DTO、異常處理
        │   ├── service/       # Domain Service、用例編排、交易邏輯邊界
        │   ├── domain/        # 聚合根、值物件、領域服務、Repository 介面
        │   └── infra/         # Repository 實作、Outbox、DB Schema、映射層
        ├── positions/         # 倉位、PnL、逐倉/全倉管理
        ├── market-data/       # 行情匯聚、行情快照、WebSocket 推送
        └── （其他非核心服務）     # funding、liquidation、admin、wallet（目前暫緩實作）
   
--- 
# 業務概述
---

# 核心流程
1. **下單入口**：請求進入 `gateway`，進行基本驗證與路由。
2. **風險校驗**：`risk-margin` 依可用資產、風險規則同步檢查，未通過即時回應。
3. **撮合執行**：合格訂單送入 `matching` 分片隊列；單寫者撮合產生成交與撤單事件，並寫入 WAL。
4. **行情播送**：成交事件推送給 `market-data`，更新快照與 WS 推送頻道。
5. **賬本入賬**：`account-ledger` 以雙分錄寫入資料庫，產生餘額變更事件。
6. **倉位更新**：`positions` 消費賬本事件，調整倉位與 PnL。
7. **資金費率**：`funding` 依指數價定時計算費率並向賬本記錄（功能暫緩）。
8. **強平管理**：`liquidation` 監控標記價，觸發強平/ADL（功能暫緩）。
9. **一致性保障**：全鏈路使用 outbox + 事件去重鍵，確保最終一致並支援重放。

---

# 資料模型與一致性策略
- **賬本**：採關係型資料庫，雙分錄（debit/credit）設計，禁止更新刪除，僅追加記錄。
- **業務鍵**：所有對外轉賬與內部劃轉使用全球唯一業務鍵，確保重放安全。
- **撮合引擎狀態**：內存訂單簿 + 快照 + WAL；單標的單執行緒，依交易對分片擴展。
- **分析側**：事件流匯入 ClickHouse / Elasticsearch，支援 CQRS 與離線報表。

---

# 協定與客戶端策略
- **外部 API**：REST 介面以 OpenAPI 管理；行情推送採 WebSocket 或 gRPC stream。
- **內部事件**：使用 Kafka，訊息 Schema 採 Protobuf/Avro 並版本化管理。
- **客戶端生成**：由契約自動生成 Feign/Retrofit/gRPC 客戶端，取代手寫 SDK，降低維護成本。

---

# 安全與合規設計
- **資產安全**：錢包整合 HSM/KMS 或多簽；提幣加入審批與風控節點（錢包服務暫緩）。
- **風險控制**：限價/爆倉保護、節流與異常行為監控，支援策略快速調整。
- **身份與授權**：基於 OIDC 的身份驗證，敏感操作需二次驗證與完整審計。
- **報表合規**：保留完整審計軌跡與對帳報表，符合監管要求。

---

# 基礎設施與框架
- **配置與中介**：Nacos（或 Apollo）、Redis（熱資料、限流）、Kafka（事件匯流）。
- **資料庫**：MySQL 作賬本與業務庫；ClickHouse 支援行情分析與報表。
- **容器與部署**：Kubernetes 滾動發版，撮合節點可用獨立叢集與親和性設定確保低延遲。
- **排程任務**：分布式排程使用 xxl-job。
- **應用框架**：Spring Boot、Spring MVC、Spring Security、Redisson、MyBatis、Spring Kafka、Spring WebSocket 與 Spring Cloud（OpenFeign、Gateway、Seata 分布式事務）。

---

# 持續優化建議
- 建立跨服務的契約版本策略與自動化生成流程。
- 引入 SLO/SLI 與錯誤預算，量化服務等級指標。
- 擴充壓測場景與撮合回放資料，定期驗證極端行情下的表現。
- 建立安全事件演練與錢包災難恢復手冊。
