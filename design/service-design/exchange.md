# Exchange 服務架構

## 模組與目錄結構
```
open.vincentf13/
├── sdk-common/
│   └── sdk-service-exchange/
│       └── sdk-service-exchange-matching/
│           ├── rest-api/      # 撮合 OpenAPI 契約、API Interface 與 DTO
│           └── rest-client/   # 依契約生成的客戶端 SDK
└── service/
    └── service-exchange/
        ├── pom.xml
        ├── service-exchange-gateway/        # API Gateway / BFF 層
        ├── service-exchange-risk-margin/    # 保證金與下單前風控
        ├── service-exchange-matching/       # 撮合引擎與訂單流邏輯
        ├── service-exchange-account-ledger/ # 賬本雙分錄與資金結算
        ├── service-exchange-positions/      # 倉位、PnL 計算
        └── service-exchange-market-data/    # 行情聚合與推送
```

### 子模組定位
- `service-exchange-gateway`：提供對外入口與路由，後續將整合 `sdk-spring-cloud-gateway` 內的共用過濾器。
- `service-exchange-risk-margin`：承接下單前的額度檢查、保證金計算與合規校驗。
- `service-exchange-matching`：負責訂單簿管理、撮合邏輯與事件產生，並透過 API 契約對外公開狀態查詢。
- `service-exchange-account-ledger`：管理資產雙分錄與資金結算事件，後續會串接 MySQL 與出帳流程。
- `service-exchange-positions`：以賬本事件為基礎更新倉位、浮動盈虧與風控指標。
- `service-exchange-market-data`：彙整撮合輸出與外部行情，對外釋出快照、增量 feed 與 WebSocket。
- `sdk-service-exchange-matching`：集中交易契約（API Interface + DTO）與自動產出的客戶端；其他服務在完成契約後會比照納入。

### 目前進度
- 各服務模組皆已建立 Spring Boot 腳手架並繼承 `service-exchange` 聚合 POM，預設依賴 `sdk-core`。
- `sdk-service-exchange-matching` 提供的 REST 契約為第一個落地案例，供 Gateway 與測試使用。
- 資料存取層、事件匯流與快照邏輯仍在實作中，尚未連結實際基礎設施。
- `funding`、`liquidation`、`wallet` 等延伸服務尚未建模，保留於後續規劃。

## 業務概述
- 系統聚焦永續合約／保證金交易場景，涵蓋下單、撮合、資金結算、倉位管理與行情播送。
- 模組間透過事件流串接，確保下單→撮合→結算→倉位更新的閉環。
- Gateway 承接使用者流量與邏輯入口，風控服務負責同步檢查，撮合與賬本服務確保資產安全與一致性。

## 核心流程（設計）
1. **下單入口**：請求進入 `service-exchange-gateway`，完成驗證、節流與路由。
2. **風險校驗**：`service-exchange-risk-margin` 依可用資產、風控規則同步檢查，未通過即時回應。
3. **撮合執行**：合格訂單送入 `service-exchange-matching`，以單執行緒訂單簿完成撮合並寫入 WAL（研發中）。
4. **行情播送**：撮合事件推送至 `service-exchange-market-data`，產生快照、推播與公共 API。
5. **賬本入賬**：`service-exchange-account-ledger` 以雙分錄落帳，輸出資產變動事件。
6. **倉位更新**：`service-exchange-positions` 消費賬本事件，計算倉位狀態與風險指標。
7. **其他模組**：資金費率與強平管理列入下一階段 backlog，尚未建置。

## 資料模型與一致性策略（目標）
- **賬本**：使用關係型資料庫維護不可變雙分錄表，以事件溯源確保追蹤性。
- **業務鍵**：所有轉賬／撤銷皆使用全球唯一業務鍵，支援冪等與重放。
- **撮合狀態**：採內存訂單簿 + 定期快照 + WAL 備援，依交易對分片擴充。
- **分析側**：事件流計畫匯入 ClickHouse/Elasticsearch，支援即時報表與回溯分析。

## 協定與客戶端策略
- **外部 API**：以 OpenAPI 管理 REST 契約；行情推送以 WebSocket 為主，後續可擴充 gRPC stream。
- **內部事件**：以 Kafka 為主要匯流，訊息 Schema 採 Protobuf/Avro 並逐版演進（目前尚在整合中）。
- **客戶端生成**：由 `sdk-service-exchange-*` 自動產生 Feign/Web 客戶端，避免手寫 SDK 帶來維護成本。

## 安全與合規設計（原則）
- **資產安全**：賬本服務保留審計軌跡，錢包/HSM 整合待錢包服務完成後納入。
- **風險控制**：在 Gateway 與風控服務導入節流、簽名與爆倉保護；策略使用配置化管理。
- **身份與授權**：規劃導入 OIDC 與多因子驗證，敏感操作需完整審計與審批流程。
- **合規報表**：透過賬本與事件流提供對帳、監管報表所需資料。

## 基礎設施與框架
- **應用框架**：Spring Boot 3.x 為基礎，Gateway 模組搭配 Spring Cloud Gateway；共用設定由 `sdk-spring-*` 系列提供。
- **基礎設施封裝**：`sdk-infra-mysql`、`sdk-infra-redis`、`sdk-infra-kafka` 提供存取封裝，將依模組需求逐步導入。
- **部署策略**：預設以容器化 + Kubernetes 滾動發版；撮合服務可獨立節點並調整親和性以確保低延遲。
- **觀測性**：`sdk-core-log`、`sdk-core-metrics`、`sdk-core-trace` 提供日誌、指標與追蹤基線設定。

## 持續優化建議
- 建立跨模組契約版本策略，確保 `sdk-service-exchange` 與服務實作同步演進。
- 擴充壓測與撮合回放資料，驗證極端行情下的性能與穩定性。
- 規劃資金費率、強平與錢包模組，並完善對應的事件流與監控。
- 補強自動化測試（契約測試、整合測試）與 SLO/SLA 量測，以支撐上線品質。
