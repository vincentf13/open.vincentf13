# 認證服務規劃

- （暫緩）`service-exchange-auth`：原規劃使用 Spring Security 驗證帳密並簽發 HS256 JWT access token，現階段模組暫時下架，待需求確認後再恢復。
- `service-exchange-gateway`：持續作為單一入口，預留路由與過濾器，未來若重新啟用 auth 服務，可在 Gateway 內重新導向 `/login` 等路徑。
- JWT 驗證流程仍可透過 `sdk-spring-security` 與自訂 `AuthenticationWebFilter` 實作；Gateway 作為資源伺服器僅需驗證簽章即可。
- 若恢復 auth 服務，建議使用短期 access token + Redis 黑名單機制；是否引入 refresh token 可依實際客戶端需求決定。

暫時僅需維護 Gateway 的安全設定與共用 secret；當 auth 模組復職時，再補上專屬的部署文件與測試範例。
