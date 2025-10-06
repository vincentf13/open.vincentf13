# sdk-spring-mvc 模組說明

`sdk-spring-mvc` 提供 REST 服務常用的 MVC 擴充元件，聚焦在請求追蹤、標準回應包裝與統一例外處理。其餘國際化、CORS、Validator 等行為改以 Spring Boot 內建的 `spring.*` 屬性調整，避免重複維護額外的組態。

## 自動配置內容
- `SpringWebMvcAutoConfiguration` 以 `@AutoConfiguration` 方式生效，僅在 Servlet Web 應用載入；僅保留少量自訂行為，其餘沿用 Spring Boot 預設設定。
- Filter：`RequestCorrelationFilter` 產出 `traceId/requestId`，寫入 Header、MDC 與 Request attribute，方便跨服務追蹤；可透過 `MvcProperties.Request` 調整 header 名稱或是否寫回 response。
- Interceptor：`RequestLoggingInterceptor` 在請求完成時透過 FastLog 輸出摘要日誌（method、URI、status、duration）。
- Advice：`ApiResponseBodyAdvice` 可將回傳值統一包裝為 `ApiResponse`；若需要維持原樣，可設定忽略的 controller 前綴。
- ExceptionHandler：`RestExceptionHandler` 處理常見的參數驗證與序列化錯誤，直接映射到 `BackendErrorCodes` 中的語意化錯誤碼。
- Message Converters：沿用專案自定的 `ObjectMapper` 並強制 UTF-8 預設編碼，確保 JSON/String 回應一致。

## 主要設定參數（`application.yaml`）
只保留實現自訂功能所需的屬性：
```yaml
open:
  vincentf13:
    mvc:
      request:
        trace-id-header: X-Trace-Id
        request-id-header: X-Request-Id
        generate-correlation-ids: true
        write-response-header: true
        filter-order: -200
      response:
        wrap-enabled: true
        ignore-controller-prefixes: []
```

其餘 Spring MVC 行為請改用官方屬性設定，例如：
- i18n / MessageSource：`spring.messages.basename`, `spring.web.locale`, `spring.web.locale-resolver`
- Validator fail-fast：`spring.mvc.formatters`, `spring.mvc.converters` 或直接透過 `jakarta.validation` 屬性
- CORS：`spring.web.cors.*`

## 整合注意事項
1. 服務需引入 `sdk-spring-mvc` 依賴（根 POM 已統一版本），其餘 MVC 屬性仍可透過 Spring Boot 預設方式覆寫。
2. 若希望停用回應包裝，可設定 `open.vincentf13.mvc.response.wrap-enabled=false`；或在 `ignore-controller-prefixes` 加入特定 package。
3. RequestCorrelationFilter 預設寫回 response header，若外部已有 API Gateway 注入，可將 `write-response-header` 調為 `false`，避免覆蓋。
4. 日誌輸出採 FastLog key-value 風格，建議其他業務層日誌也使用 FastLog，以利集中查詢。
5. 若需要額外的 MVC 元件（如自訂 ArgumentResolver），可在服務專案自行宣告；`@ConditionalOnMissingBean` 設計可避免與模組衝突。

透過簡化後的模組，團隊可以利用 Spring Boot 既有設定管理大部分 MVC 行為，同時保留一致的請求追蹤與錯誤回應能力。
