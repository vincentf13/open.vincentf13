• Service Boundaries

- 建議拆成 auth-service（身份驗證、憑證、Token）與 user-service（帳號主檔、KYC、偏好），責任清晰也方便不同垂直團隊
  維運。
- 若初期人力有限，可從同一程式碼庫的模組化開始，邊界先以 REST 介面抽象，待負載或流程複雜化再拆微服務。
- 兩服務都走 MySQL，各自持有最小必要資料；auth-service 不需要使用者詳細資料，避免跨界 JOIN。

MySQL Schema 建議

- auth-service：auth_user(auth_id,user_id,login_name,status)、
  auth_credential(auth_id,credential_type,secret_hash,salt,version,expires_at)、
  auth_refresh_token(token_id,auth_id,client_id,issued_at,revoked)、
  auth_mfa_secret(auth_id,method,secret,enrolled_at)、auth_audit_log。
- user-service：user_account(user_id,user_no,email,phone,status,tier,created_at)、
  user_profile(user_id,display_name,country,birthdate)、user_kyc_record、user_preference、user_contact_method 等；保持
  user_id 為跨服務主鍵。
- 採樂觀鎖版本欄位，所有時間欄位使用 UTC datetime，必要欄位加唯一索引（email、phone、user_no）。

註冊流程

- Gateway POST /auth/signup → auth-service 驗證密碼強度、IP 節流。
- auth-service 調用 user-service /users 建立主檔（回傳 user_id）；若 user service 驗重失敗回 409。
- 建立成功後，auth-service 產生 auth_id，寫入 auth_user、auth_credential；發出 user.created 事件供郵件或風控使用。
- 可選：回傳暫態 JWT + refresh token，或要求 Email verification 後才啟用。

登入流程

- Gateway POST /auth/login（login_name + password 或 OTP）。
- auth-service 比對 auth_credential，成功後讀取 user-service 簡要狀態（可用快取或 Feign client，查 user_account
  status、tier）。
- auth-service 產生 JWT（sub=user_id、auth_id、role、iat、exp ≈5-15 分鐘）與 refresh token；刷新流程提供 POST /auth/
  token/refresh。
- 登入成功事件（auth.login.success）發佈給風控／行銷；失敗則記錄於 auth_audit_log 以便鎖帳策略。

服務之間接口

- auth-service → user-service：REST POST /users、GET /users/{identifier}，回傳 JSON {user_id,status,tier,kyc_level}。
- user-service → auth-service 僅在需要停用帳號時呼叫，例如 POST /auth/users/{userId}/disable。
- 建議同時透過 Kafka 發出事件（user.created、auth.token.revoked）供其他模組訂閱，支援異步耦合。

JWT 驗證要點

- 使用 RS256/ECDSA 私鑰簽發，公鑰透過 Discovery 或 Config Server 發佈給 Gateway / Downstream。
- Claims：sub=user_id、aud=exchange 系統、auth_time、session_id、locale、權限/範圍；如需設備綁定可加 device_id。
- Gateway 透過 sdk-spring-cloud-openfeign 的 RequestInterceptor 自動夾帶 Authorization: Bearer ...、Trace ID、語系；後
  端服務只需驗簽 + 解析。
- Refresh token 建議存 DB 或 Redis，啟用旋轉（每次刷新即失效舊 Token）。

其他建議

- Password 使用 Argon2id/BCrypt，加 Pepper（在 Vault）。
- 提供 MFA enrollment API，auth-service 管理第二因子密鑰；登入流程依回應帶狀態要求第二步驗證。
- user-service 負責 Email/手機驗證流程與 KYC 上傳；可發佈 user.kyc.updated 事件給風控。
- 對外 API 經 Gateway 統一率限制與 IP 白名單；內部服務透過 Mutual TLS 或 Service Mesh 管理鑑權。

整體架構讓認證、使用者資料責任分離，配合事件驅動流程，不論後續擴充風控或會員成長層服務都能保持邊界清晰。
