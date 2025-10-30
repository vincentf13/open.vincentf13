# 業務概述

- 系統聚焦永續合約／保證金交易場景，涵蓋下單、撮合、資金結算、倉位管理與行情播送。
- 模組間透過事件流串接，確保下單→撮合→結算→倉位更新的閉環。
- Gateway 承接使用者流量與邏輯入口，風控服務負責同步檢查，撮合與賬本服務確保資產安全與一致性。

# 模組與目錄結構

```
open.vincentf13/
├── sdk-common/
│   └── sdk-exchange/
│       └── sdk-exchange-maching/
│           ├── rest-api/      # 撮合 OpenAPI 契約、API Interface 與 DTO
│           └── rest-client/   # 依契約生成的客戶端 SDK
└── service/
    └── exchange/
        ├── pom.xml
        ├── gateway/            # API Gateway / BFF 層
        ├── auth/               # 認證服務
        ├── user/               # 使用者主檔服務
        ├── order/              # 委託管理
        ├── account-ledger/     # 雙分錄台帳
        ├── risk-margin/        # 風控與保證金
        ├── matching/           # 撮合引擎
        ├── positions/          # 倉位管理
        └── market-data/        # 行情推播
```

## 模組職責

| 模組               | 核心責任                       |
| ---------------- | -------------------------- |
| `gateway`        | 對外入口、JWT 驗證、流量治理、觀測性匯出     |
| `auth`           | 登入/權杖/多因子流程、會話管理、登入審計與事件發布 |
| `user`           | 使用者主檔、偏好設定、KYC、角色/權限管理     |
| `order`          | 委託下單、撤單、歷史查詢、指令排程、訂單事件溯源   |
| `account-ledger` | 雙分錄資產台帳、結算、利息/費率計算、報表輸出    |
| `risk-margin`    | 保證金/風險係數計算、下單前限額、強平判斷與佇列   |
| `matching`       | 訂單簿維護、撮合引擎、成交輸出與行情報表來源     |
| `positions`      | 倉位快照、未實現損益、破產價/強平價計算       |
| `market-data`    | 行情快照、K 線/深度資料生成、串流推播服務     |

### 模組職責詳述

- **gateway**：
  - 對接外部流量，統一執行 JWT 驗證、節流、設備指紋檢查。
  - 管理路由策略、A/B 測試、灰度/Canary 發佈，並整合觀測性管線。
  - 提供健康檢查、錯誤轉換與安全標頭注入。
- **auth**：
  - 支援密碼、OTP、FIDO、社群登入等多種認證方式，維護登入審計。
  - 維護會話、刷新權杖、黑名單、裝置綁定與登入通知。
  - 發布登入/登出事件給風控、報表與通知模組。
- **user**：
  - 維護使用者主檔、偏好設定、語系與通知訂閱，管理 KYC 流程。
  - 管理角色、權限與範圍（scope），提供 RBAC/ABAC 查詢介面。
- **order**：
  - 統一下單入口，支援市價/限價/條件單等多種委託型別。
  - 提供單筆與批次撤單、委託查詢、歷史訂單匯出。
  - 寫入訂單事件（event sourcing）供撮合、報表與重播。
- **account-ledger**：
  - 以雙分錄維護資產變動，確保借貸平衡並支援審計。
  - 產生結算、利息、資金費率、手續費等財務事件。
- **risk-margin**：
  - 計算保證金與風險指標、提供下單前限額校驗。
  - 維護風控規則版本、建立強平佇列與風險告警。
- **matching**：
  - 維護訂單簿、撮合成交、產生成交紀錄與行情報價來源。
  - 將撮合結果推送給 ledger、positions、market-data。
- **positions**：
  - 根據撮合與台帳事件維護倉位、未實現損益、破產價。
  - 觸發強平邏輯並回寫至 risk-margin 與 order 模組。
- **market-data**：
  - 轉換撮合輸出為行情快照、K 線、深度資料和統計指標。
  - 提供 WebSocket / gRPC 串流與快取服務給前端與夥伴。

# 核心流程（設計）

以下為完整交易閉環的目標狀態，標示「暫緩」者目前尚未有對應模組：





# 資料庫表結構（草案）

### auth 模組

| 表名 | 用途 | 主要欄位 | 備註 |
| --- | --- | --- | --- |
| `auth_credentials` | 登入憑證資料 | `id (PK)`, `user_id`, `credential_type`, `secret_hash`, `salt`, `status`, `expires_at` | 支援密碼、API Key、FIDO 等多元憑證。 |
| `auth_providers` | 第三方登入綁定 | `id (PK)`, `user_id`, `provider`, `provider_user_id`, `linked_at` | 儲存社群/外部身份對應。 |
| `refresh_tokens` | JWT 會話持久化 | `token_id (PK)`, `user_id`, `session_id`, `issued_at`, `expires_at`, `is_active`, `revoked_reason` | 搭配權杖刷新、強制登出與風險登入鎖定。 |
| `login_audits` | 登入審計記錄 | `id (PK)`, `user_id`, `ip`, `user_agent`, `result`, `failure_reason`, `logged_at` | 供風控、合規、行為分析使用。 |

### user 模組

| 表名                         | 用途      | 主要欄位                                                                                     | 備註                       |
| -------------------------- | ------- | ---------------------------------------------------------------------------------------- | ------------------------ |
| `users`                    | 使用者主檔   | `id (PK)`, `external_id`, `email`, `status`, `created_at`, `updated_at`                  | 其他資料表以 `user_id` 外鍵串聯。   |
| `user_profiles`            | 個資與偏好   | `user_id (PK/FK users)`, `display_name`, `country`, `language`, `timezone`               | 與 `users` 1:1，儲存顯示與通知設定。 |
| `kyc_records`              | 身分驗證紀錄  | `id (PK)`, `user_id`, `tier`, `status`, `submitted_at`, `approved_at`, `rejected_reason` | 支援多次送審與審核歷程。             |
| `role_assignments`         | 角色/權限授權 | `id (PK)`, `user_id`, `role`, `scope`, `granted_by`, `granted_at`, `expires_at`          | RBAC 核心資料表。              |
| `notification_preferences` | 通知訂閱設定  | `id (PK)`, `user_id`, `channel`, `is_enabled`, `updated_at`                              | 控制 Email、SMS、Push 等通路。   |

### order 模組

| 表名 | 用途 | 主要欄位 | 備註 |
| --- | --- | --- | --- |
| `orders` | 委託主檔 | `order_id (PK)`, `user_id`, `instrument_id`, `client_order_id`, `side`, `type`, `price`, `quantity`, `status`, `time_in_force`, `created_at`, `updated_at` | 委託生命週期的核心資料。 |
| `order_events` | 委託事件溯源 | `event_id (PK)`, `order_id`, `event_type`, `payload`, `occurred_at`, `actor` | 支援重播、稽核與行為分析。 |
| `order_tasks` | 異步指令佇列 | `task_id (PK)`, `order_id`, `task_type`, `payload`, `status`, `retry_count`, `scheduled_at` | 批次撤單、策略單等非即時動作。 |

### matching 模組

| 表名 | 用途 | 主要欄位 | 備註 |
| --- | --- | --- | --- |
| `trade_tickers` | 成交紀錄 | `trade_id (PK)`, `order_id`, `counterparty_order_id`, `price`, `quantity`, `fee`, `executed_at` | 撮合輸出，供報表與事件回放。 |

### account-ledger 模組

| 表名 | 用途 | 主要欄位 | 備註 |
| --- | --- | --- | --- |
| `ledger_entries` | 雙分錄紀錄 | `entry_id (PK)`, `account_id`, `asset`, `amount`, `direction`, `reference_type`, `reference_id`, `event_time` | 借貸必須平衡，支援審計。 |
| `ledger_balances` | 帳戶餘額 | `id (PK)`, `account_id`, `asset`, `balance`, `available`, `reserved`, `updated_at` | 提供資產查詢與風控試算。 |
| `funding_rates` | 資金費率 | `id (PK)`, `instrument_id`, `rate`, `effective_at`, `calculated_at` | 供費率結算與倉位估值。 |

### risk-margin 模組

| 表名 | 用途 | 主要欄位 | 備註 |
| --- | --- | --- | --- |
| `risk_limits` | 風控參數 | `id (PK)`, `instrument_id`, `tier`, `initial_margin_rate`, `maintenance_margin_rate`, `max_leverage`, `updated_at` | 下單前保證金與限額判斷依據。 |
| `liquidation_queue` | 強平佇列 | `id (PK)`, `position_id`, `status`, `queued_at`, `processed_at`, `reason` | 追蹤強平排程與處理狀態。 |

### positions 模組

| 表名 | 用途 | 主要欄位 | 備註 |
| --- | --- | --- | --- |
| `positions` | 倉位主檔 | `position_id (PK)`, `user_id`, `instrument_id`, `side`, `quantity`, `entry_price`, `mark_price`, `unrealized_pnl`, `liquidation_price`, `updated_at` | 倉位快照與估值資料。 |
| `position_events` | 倉位事件溯源 | `event_id (PK)`, `position_id`, `event_type`, `delta_qty`, `delta_pnl`, `reference_id`, `occurred_at` | 追蹤倉位變化與強平歷程。 |

### market-data 模組

| 表名 | 用途 | 主要欄位 | 備註 |
| --- | --- | --- | --- |
| `market_snapshots` | 行情快照 | `snapshot_id (PK)`, `instrument_id`, `bid_depth`, `ask_depth`, `last_price`, `volume_24h`, `captured_at` | 提供行情查詢與串流基礎。 |

### 共用參數

| 表名 | 用途 | 主要欄位 | 備註 |
| --- | --- | --- | --- |
| `instrument_metadata` | 交易商品設定 | `instrument_id (PK)`, `symbol`, `base_asset`, `quote_asset`, `status`, `tick_size`, `lot_size`, `launch_at` | 供所有交易模組共用的靜態資訊。 |

> 後續若擴充期權、現貨或策略單，可依各模組需求延伸專屬資料表與事件流，並沿用上述設計原則。
# 資料模型與一致性策略（目標）

- **賬本**：使用關係型資料庫維護不可變雙分錄表，以事件溯源確保追蹤性。
- **業務鍵**：所有轉賬／撤銷皆使用全球唯一業務鍵，支援冪等與重放。
- **撮合狀態**：採內存訂單簿 + 定期快照 + WAL 備援，依交易對分片擴充。
- **分析側**：事件流計畫匯入 ClickHouse/Elasticsearch，支援即時報表與回溯分析。

# 協定與客戶端策略

- **外部 API**：以 OpenAPI 管理 REST 契約；行情推送以 WebSocket 為主，後續可擴充 gRPC stream。
- **內部事件**：以 Kafka 為主要匯流，訊息 Schema 採 Protobuf/Avro 並逐版演進（目前尚在整合中）。
- **客戶端生成**：由 `sdk-exchange-*` 自動產生 Feign/Web 客戶端，避免手寫 SDK 帶來維護成本。
# 安全與合規設計（原則）

- **資產安全**：賬本服務保留審計軌跡，錢包/HSM 整合待錢包服務完成後納入。
- **風險控制**：在 Gateway 與風控服務導入節流、簽名與爆倉保護；策略使用配置化管理。
- **身份與授權**：規劃導入 OIDC 與多因子驗證，敏感操作需完整審計與審批流程。
- **合規報表**：透過賬本與事件流提供對帳、監管報表所需資料。

# 持續優化建議

- 擴充壓測與撮合回放資料，驗證極端行情下的性能與穩定性。
- 規劃資金費率、強平與錢包模組，並完善對應的事件流與監控。
