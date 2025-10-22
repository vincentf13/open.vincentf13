# sdk-spring-session 設計說明

## 模組定位
- 模組名稱：`sdk-common/sdk-spring-session`
- 目的：為採用 Spring Session（Redis 儲存）的服務提供一組預設依賴、建議設定與可選自動配置，降低重複作業。
- 依賴：
  - `spring-session-data-redis`：啟用 Spring Session 並以 Redis 作為 session repository。
  - `sdk-infra-redis`：沿用內部封裝的 Redis 連線與序列化設定。

## 自動設定現況
- 主要配置類別為 `open.vincentf13.common.spring.session.ConfigCookie`，使用 `@AutoConfiguration` 與 `@ConditionalOnWebApplication(type = SERVLET)`，僅在 Servlet 環境生效。
- 類別本身尚未提供啟用中的 Bean，僅保留 `CookieSerializer` 的註解範例（可視需求開啟以控制 Max-Age、SameSite 等屬性）。

## Redis / Spring Session 建議設定
- `src/main/resources/application-dev.yaml` 提供建議值：
  - `spring.session.store-type=redis` 指定使用 Redis。
  - `spring.session.redis.namespace=app:session`，統一 Key 前綴方便監控。
  - `flush-mode=on-save`、`save-mode=on-set-attribute`：僅在請求結束或變更屬性時寫回，避免額外的 Redis I/O。
  - `cleanup-cron="0 */5 * * * *"`：每 5 分鐘清除 Spring Session 的索引資料。
- 服務可複製此檔案內容作為各環境的預設，再依實際 Redis 連線資訊調整。

## Cookie 設定範例
- 註解中的 `CookieSerializer` 若要啟用，解除註解後請注意：這會覆寫 Servlet Container 的 Cookie 設定，所有屬性需重新明確指定。

## 適用情境
1. 需要跨節點共享登入態的 Spring Boot 服務。
2. 期望統一 Spring Session 的 Redis 命名空間、同步策略與清理排程。
3. 部分服務需客製 Cookie 行為時，可直接採用 `CookieConfig` 中的範例。


## 設計原則
- 模組保持輕量，僅提供基線設定與範例，避免對服務造成過度侵入。
- 允許服務以 `@ConditionalOnMissingBean` 擴充或覆寫行為，維持彈性。
- 透過文件化的範例與預設設定協助團隊快速導入並維持一致性。

