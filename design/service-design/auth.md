- 建立專職的 service-auth，用 Spring Security ，由它負責驗證帳密，並頒發短生命週期的 JWT access token 與 refresh token。
- 在 service-exchange-gateway ，用 Spring Cloud Gateway 只保留「入口控制」職責：
	- 將 /login 等路徑路由到 service-auth。
    - 其他業務路徑加上 AuthenticationWebFilter 或自訂 Global Filter，驗證  HTTP header/Authorization 中的 JWT，解析 Scope/Authority 後再轉發到 Server
		模式：
		- 只驗證由 auth-service 簽發的 JWT，不存 session。
		- 在 SecurityFilterChain 中用 @PreAuthorize、Method Security 或 Web
        filter 判斷權限；資料層權限可以額外檢查租戶／使用者 ID。
  - token 交握：Gateway 驗證 access token 成功後，下游服務就能信任 Gateway 轉送的請求；若需要轉發使用者資訊，可放在 JWT claim（sub、roles 等）或額外的 header（務必簽名或只允許內部網路）。
  - 若要支援行動裝置或前端 SPA，建議：
      1. 首次登入：前端 → Gateway → auth-service（帳密驗證後拿到
         access+refresh token）。
      2. 後續 API：前端帶 access token 打 Gateway → Gateway 驗證 → 呼叫下游
         服務。
      3. access token 失效時，前端用 refresh token 透過 Gateway 打 auth-
         service 的刷新端點。
  - 補強措施：
      - 使用 Redis 儲存 refresh token／，DB儲存黑名單。
