# sdk-spring-session 設計說明

## 模組定位
- 模組名稱：`sdk-common/sdk-spring-session`
- 目的：提供導入 Spring Session（Redis 儲存）的服務一組預設依賴與建議設定，降低重複配置成本。
- 依賴：
  - `spring-session-data-redis`：啟用 Spring Session 並以 Redis 作為 session repository。
  - `sdk-infra-redis`：沿用內部封裝的 Redis 連線與序列化基礎設定。

## 自動設定現況
- 主要配置類別為 `open.vincentf13.common.sping.session.CookieConfig`（注意封包命名使用 `sping`）。
- `CookieConfig` 以 `@AutoConfiguration` 與 `@ConditionalOnWebApplication(type = SERVLET)` 標註，僅在 Servlet 環境載入。
- 目前類別中沒有啟用中的 Bean；`CookieSerializer` 設定範例被保留為註解，供需要自訂 cookie Max-Age 或 SameSite 行為時參考。
- 由於缺乏在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 的條目，`CookieConfig` 預設不會被 Spring Boot 自動註冊；若要啟用需手動匯入或補上 imports 檔。

## Redis / Spring Session 設定建議
- `src/main/resources/application-dev.yaml` 提供一份範例設定：
  - `spring.session.store-type=redis`：指定使用 Redis 儲存 session。
  - `spring.session.redis.namespace=app:session`：統一 Redis key 前綴，方便分流管理。
  - `flush-mode=on-save`：僅在請求結束或顯式儲存時寫回 Redis，避免不必要的更新。
  - `save-mode=on-set-attribute`：僅在對 session 屬性新增、更新或刪除時觸發寫入。
  - `cleanup-cron="0 */5 * * * *"`：每 5 分鐘清除 Spring Session 建立的索引。
- 服務可將此檔案內容複製到各自的 `application-*.yaml` 作為基準，再依環境調整 Redis 連線資訊。

## Cookie 設定範例
- 被註解的 `CookieSerializer` 實作示範：
  - Cookie 名稱使用 `SESSION`。
  - 預設 `HttpOnly=true`、`Secure=true`（需 HTTPS）、`SameSite=None`（支援跨域）。
  - 可視需求設定 `Domain`、`Path` 與 `Max-Age`。
- 若要啟用，需取消註解 Bean 並注意這會覆蓋 Servlet Container 的 Cookie 設定，所有屬性需明確指定。

## 適用情境
1. 服務採用 Spring Session 並以 Redis 共用登入態或跨節點共享 session。
2. 需要統一 session 寫入策略與命名空間，便於監控與維運。
3. 少量服務需要客製 Cookie 行為時，可複用 `CookieConfig` 的範例實作。

## 待辦與風險
- **自動配置未生效**：缺少 `AutoConfiguration.imports` 導致 `CookieConfig` 不會自動載入，需補上或改用預設 Spring Boot 設定。
- **封包命名錯誤**：`open.vincentf13.common.sping.session` 拼字為 `sping`，若後續補齊 `AutoConfiguration.imports` 也要確認套件路徑一致，避免類別無法被掃描。
- **Cookie Serializer 尚未啟用**：若需要統一定義 Cookie，應將 Bean 轉為啟用狀態並視環境設定 `Secure`、`SameSite` 等屬性。

## 整體設計原則
- 保持模組輕量，僅提供必要的 session/Redis 基線設定。
- 允許服務依照自身需求擴充或覆蓋設定，而非強制綁定所有細節。
- 所有附帶資訊（如 Cookie 設定）以範例或註解形式提供，降低強制侵入性。

