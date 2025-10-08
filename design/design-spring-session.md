# sdk-spring-session 設計說明

## 模組定位
- 模組名稱：`sdk-common/sdk-spring-session`
- 目的：提供導入 Spring Session（Redis 儲存）的服務一組預設依賴與建議設定，降低重複配置成本。
- 依賴：
  - `spring-session-data-redis`：啟用 Spring Session 並以 Redis 作為 session repository。
  - `sdk-infra-redis`：沿用內部封裝的 Redis 連線與序列化基礎設定。

## 自動設定現況
- 主要配置類別為 `open.vincentf13.common.spring.session.CookieConfig`（注意封包命名使用 `sping`）。
- `CookieConfig` 以 `@AutoConfiguration` 與 `@ConditionalOnWebApplication(type = SERVLET)` 標註，僅在 Servlet 環境載入。
- 目前類別中沒有啟用中的 Bean；`CookieSerializer` 設定範例被保留為註解，供需要自訂 cookie Max-Age 或 SameSite 行為時參考。

## Redis / Spring Session 設定建議
- `src/main/resources/application-dev.yaml` 提供一份範例設定：
  - `spring.session.store-type=redis`：指定使用 Redis 儲存 session。
  - `spring.session.redis.namespace=app:session`：統一 Redis key 前綴，方便分流管理。
  - `flush-mode=on-save`：僅在請求結束或顯式儲存時寫回 Redis，避免不必要的更新。
  - `save-mode=on-set-attribute`：僅在對 session 屬性新增、更新或刪除時觸發寫入。
  - `cleanup-cron="0 */5 * * * *"`：每 5 分鐘清除 Spring Session 建立的索引。
- 服務可將此檔案內容複製到各自的 `application-*.yaml` 作為基準，再依環境調整 Redis 連線資訊。

## Cookie 設定範例
- 被註解的 `CookieSerializer` 實作，若要啟用，需取消註解 Bean 並注意這會覆蓋 Servlet Container 的 Cookie 設定，所有屬性需明確指定。

## 適用情境
1. 服務採用 Spring Session 並以 Redis 共用登入態或跨節點共享 session。
2. 需要統一 session 寫入策略與命名空間，便於監控與維運。
3. 少量服務需要客製 Cookie 行為時，可複用 `CookieConfig` 的範例實作。


## 整體設計原則
- 保持模組輕量，僅提供必要的 session/Redis 基線設定。
- 允許服務依照自身需求擴充或覆蓋設定，而非強制綁定所有細節。
- 所有附帶資訊（如 Cookie 設定）以範例或註解形式提供，降低強制侵入性。

