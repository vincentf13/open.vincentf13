- Controller 嚴格實現 `sdk-contract` 中定義的 exchange-{service name}-rest-api 定義的 `OpenAPI` 介面，因為 client 端在
  exchange-{service name}-rest-client中繼承此介面，自動實現 client 端的接口調用。
- 權限 (`@Jwt`, `@PublicApi`,`@PrivateApi` 等) 與效驗註解皆寫在`OpenAPI`，方便用戶了解
- **委派任務**: 將通過驗證的請求數據（REST Request DTO）直接委派給 `Service` 層進行處理，不再額外轉換為 Command 物件。
- **回應格式化**: 將 `Service` 層返回的結果（通常是 DTO）封裝成標準的 API 回應格式（由 `sdk-spring-mvc` 提供支援）。

- 注意：所有enum 定義在 exchange-{service name}-rest-api 與 `OpenAPI` 介面同一模塊，方便系統內外共用 enum。
