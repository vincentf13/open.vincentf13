# 後端架構概覽

## 模組與目錄結構

```
open.vincentf13/
├── pom.xml
├── AGENTS.md
├── LICENSE.md
├── design/                         # 架構說明文件與模組筆記
├── k8s/                            # Kubernetes 資產
├── script/                         # 維運、自動化腳本
├── sdk/                     # 平台共用 Starter 與基礎設施封裝
│   ├── sdk-core/                   # 基礎工具（錯誤處理、Json、共用 Result）
│   ├── sdk-core-log/               # 日誌 starter 與格式規範
│   ├── sdk-core-metrics/           # 指標收集與預設監控配置
│   ├── sdk-core-test/              # 測試輔助、假資料與測試基座
│   ├── sdk-core-trace/             # TraceId/SpanId 注入、追蹤設定
│   ├── sdk-infra-kafka/            # Kafka Producer/Consumer 封裝
│   ├── sdk-infra-mysql/            # MySQL 連線池、審計欄位與 Repository 工具
│   ├── sdk-infra-redis/            # Redis 客戶端與序列化策略
│   ├── sdk-auth-jwt/               # JWT Filter、Token 與 Session 共用元件
│   ├── sdk-auth-server/            # 身分驗證、ACL 與簽名工具
│   ├── sdk-spring-cloud-gateway/   # Gateway 共用設定與過濾器
│   ├── sdk-spring-mvc/             # Web 層配置、CORS、例外處理
│   ├── sdk-spring-security/        # 安全攔截、權限模型與授權流程
│   ├── sdk-spring-session/         # Spring Session/Redis 會話整合
│   └── sdk-spring-websocket/       # WebSocket Starter 與訊息編碼
├── sdk-contract/                   # API 契約與自動化產生客戶端
│   └── exchange-sdk/
│       └── exchange-user-sdk/
│           ├── rest-api/           # 使用者服務 OpenAPI 契約與 DTO
│           └── rest-client/        # 依契約生成的客戶端 SDK
├── service/
│   ├── pom.xml
│   ├── exchange/
│   │   ├── pom.xml
│   │   └── exchange-gateway/       # 對外 API Gateway / BFF
│   ├── service-template/           # 建立新服務的腳手架範例
│   └── service-test/               # 測試服務與驗證場景
├── target/                        # Maven 聚合輸出
└── readme.md
```

### `sdk`（原 `sdk-common`）說明

- 作為平台基座，統一封裝日誌、追蹤、驗證、安全與基礎設施，避免各服務重複實作。
- `sdk-core*` 系列提供共通的系統能力：核心工具、日誌、指標、追蹤與測試資源。
- `sdk-infra-*` 聚焦基礎設施整合（MySQL、Redis、Kafka），提供一致的連線設定與封裝。
- `sdk-spring-*` 針對 Spring 層所需的 MVC、安全、WebSocket、Gateway 擴充，輸出為 starter。
- 每個子模組維護 README 與設定說明，新增共用能力時優先在此抽象並覆蓋自動測試。

### `sdk-contract` 說明

- 集中維護各領域（目前為交易域）API 契約與自動化產生的客戶端 SDK。
- `exchange-sdk` 聚焦交易領域契約，目前提供 `exchange-user-sdk` 子模組；各子模組的 `rest-api` 暴露 OpenAPI 契約與 DTO，`rest-client` 依契約生成 Feign/HTTP 客戶端。
- 業務服務的 Controller 需 `implements` 對應的 `*Api` 介面，確保輸入輸出與契約保持一致。
- 契約調整會對應釋出新版本，請依語義化版本規則管理 breaking change 並同步通知依賴的服務。

### 依賴管理建議

- 根目錄 `pom.xml` 聚合各模組並集中管理插件版本，搭配 BOM 確保依賴對齊。
- 共用 Starter 需以明確 artifactId 命名並具備向下相容測試，避免破壞既有服務。
- 服務若需覆寫版本，優先在各自子模組的 `<dependencyManagement>` 控制，避免直接鎖定 transitive 依賴。

### `service`

- `exchange` 集合目前僅保留 Gateway 模組，其餘交易子服務（auth、matching、market-data、account-ledger、positions、risk-margin）暫時移除，待後續重新引入時再恢復聚合。
- `service-template` 提供建立新服務時的腳手架與最佳實務樣板。
- `service-test` 收錄端到端驗證與 PoC 場景，便於開發期驗證整合流程。

### `.github`

- 存放 GitHub Actions workflow、Issue／PR 模板與 Codeowners 規則，定義 CI/CD 與協作流程。

### `.mvn`

- 集中 Maven Wrapper 與 Maven Daemon 設定；`mvnd.properties` 可調整 JVM 參數以提升構建效能。

### `k8s`

- 收納所有 Kubernetes manifest，依服務拆分目錄（例如 `k8s/service-exchange`）。
- 套用順序建議：Deployment → Service → HPA → Ingress，確保依賴關係正確。

### `script`

- 集中維運腳本，例如資料庫遷移、自動化測試與清理工具；建議腳本自帶說明並啟用 `set -eo pipefail`。


交易所相關內容已獨立整理於 [exchange service](exchange.md)，本文件聚焦跨服務的共用架構原則。
