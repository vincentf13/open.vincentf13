# sdk-spring-mvc 設計說明

## 模組定位
- 模組名稱：`sdk/sdk-spring-mvc`
- 目標：提供 Spring MVC 與 Spring Boot Web 專案可重複使用的自動設定與共用元件，讓各服務以最少設定即可取得統一的回應格式、例外處理、日誌行為與多語系支持。
- 引用依賴：
  - `sdk-core` 與 `sdk-core-log`：提供錯誤碼、記錄工具與基礎共用類別。
  - `spring-boot-starter-web`：提供 Spring MVC 核心功能。

## 自動設定總覽
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 將以下自動設定類別註冊到 Spring Boot：

| 類別 | 主要責任 |
| --- | --- |
| `AdviceConfig` | 建立 REST 例外處理與回應包裝的 Advice。|
| `CookieConfig` | 調整 Servlet Session Cookie 預設值。|
| `CorsConfig` | 以 `WebMvcConfigurer` 擴充全域 CORS 規則。|
| `FilterConfig` | 登記 `FormContentFilter` 以支援非 POST 的表單請求。|
| `InterceptorConfig` | 建立請求摘要日誌用的攔截器。|
| `MvcProperties` | 提供可在設定檔覆寫的 MVC 自訂屬性。|
| `WebMvcConfig` | 整合攔截器、格式轉換、`ObjectMapper` 與國際化設定。|

所有自動設定均加上 `@ConditionalOnWebApplication(type = SERVLET)` 與 `@ConditionalOnClass(WebMvcConfigurer.class)`，確保僅在 Servlet 型 Web 專案載入。

## 組態屬性 (`MvcProperties`)
- 設定前綴：`open.vincentf13.mvc`
- `request`：控制追蹤 ID 欄位名稱、是否自動生成、是否回寫回應標頭、預設 Filter 順序等。現行程式僅作為屬性承載，尚未有啟用中的 Filter 消費此設定。
- `response.wrapEnabled`：是否啟用統一回應包裝；`ignoreControllerPrefixes` 支援以類別名稱前綴排除特定 Controller。

## REST 回應包裝 (`ApiResponseBodyAdvice`)
- 自動裝配自 `AdviceConfig`。
- 在 `wrapEnabled` 為 `true` 時，攔截所有 Controller 回應：
  - 已經是 `ApiResponse` 或 `ProblemDetail` 的結果直接放行。
  - `byte[]`、`Collection`、陣列與其他物件包成 `ApiResponse.success(data)`。
  - `null` 回應轉為 `ApiResponse.success()`。
  - `String` 會先將回應標頭強制設為 `application/json`，再用 `ObjectMapper` 序列化為 JSON 字串，避免被當作純文字輸出。
  - `ResponseEntity` 保持原狀給使用者自行控制。

## 統一回應格式 (`ApiResponse`)
- 採用 Java `record`，欄位包含 `code`、`message`、`data`、`timestamp`、`meta`。
- 預設成功：`code="0"`、`message="OK"`。
- `failure` 工廠方法可附帶 `meta` 資訊；`withMeta` 支援後續併入額外欄位。

## 例外處理 (`RestExceptionHandler`)
- `AdviceConfig` 預設建立並以 `@RestControllerAdvice` 生效。
- 支援以下案例：
  - Bean 驗證 (`MethodArgumentNotValidException`、`BindException`、`ConstraintViolationException`)：組合欄位錯誤並使用 `BackendErrorCodes.REQUEST_VALIDATION_FAILED`。
  - `MissingServletRequestParameterException`、`HttpMessageNotReadableException` 等標準 MVC 異常：回傳對應錯誤碼與訊息。
  - 業務層 `ControllerException`：記錄警告並回傳自訂錯誤碼。
  - 其他例外：以 `BackendErrorCodes.INTERNAL_ERROR` 轉成 500 回應。
- `MessageSourceAware`：若系統提供 `MessageSource`，會依照請求語系取出多語系訊息。
- `meta` 區塊包含 `status`、`timestamp`、`path`、`method`、`traceId`、`requestId`（目前追蹤 ID 須由外部 Filter 設定）。

## 日誌攔截器 (`RequestLoggingInterceptor`)
- 於 `InterceptorConfig` 中建立並由 `WebMvcConfig` 的 `WebMvcConfigurer` 註冊。
- `preHandle` 記錄起始時間；`afterCompletion` 輸出 OpenLog 格式的完成或失敗資訊，包含 HTTP 方法、URI、狀態碼與耗時毫秒數。

## Web MVC 組態 (`WebMvcConfig`)
- Bean：
  - `ShallowEtagHeaderFilter`：為回應加上 `ETag`，支援 304 快取。
  - `LocaleResolver`：以 `AcceptHeaderLocaleResolver` 為基礎，支援 `?lang=` 參數覆寫語系，預設語系為 `en-US`。
  - `WebMvcConfigurer`：
    - 註冊 `RequestLoggingInterceptor`。
    - 加入字串轉換器將輸入 `String` 自動 `trim`，空白轉 `null`。
    - 將全域 `ObjectMapper` 與 UTF-8 設定注入 `MappingJackson2HttpMessageConverter` 與 `StringHttpMessageConverter`。
    - 回傳外部註冊的 `Validator`，以支援 fail-fast 等客製化設定。

## 其他自動設定
- `FilterConfig`：提供 `FormContentFilter`，使 `PUT`/`PATCH`/`DELETE` 等 `x-www-form-urlencoded` 請求可透過 `getParameter` 取得表單欄位；追蹤 Filter 目前仍以註解保留範例。
- `CookieConfig`：透過 `ServletContextInitializer` 調整 Session Cookie，預設名稱 `JSESSIONID`、有效期 30 分鐘、`HttpOnly=true`、`Secure=false`（需依環境調整）。
- `CorsConfig`：
  - 開放所有路徑 `/**`。
  - 允許來自 `http://127.0.0.1:*`、`http://localhost:*`、`http://*.local` 以及萬用字元 `*` 的跨域請求。
  - 允許 `GET`、`POST`、`PUT`、`PATCH`、`DELETE`、`OPTIONS`，允許所有自訂標頭。
  - 暴露 `X-Request-Id`、`X-Trace-Id`、`Content-Dispositio`（保留現況拼字）。
  - `allowCredentials(true)`，`maxAge(3600)`。

## 預設應用程式設定
`src/main/resources/application.yaml` 提供一組建議的全域設定：
- 啟用反向代理支援 (`server.forward-headers-strategy=framework`) 與回應壓縮。
- Session Cookie 相關預設與儲存設定。
- Spring MVC 格式化與 `hidden-method` Filter。
- Multipart 上傳大小上限 (`20MB/50MB`)。
- `open.vincentf13.mvc` 對應到 `MvcProperties` 的預設值。
- 預設多語系訊息檔位於 `src/main/resources/i18n/`。

## 設計取向
1. **按需導入**：所有自動設定均設條件判斷，避免在非 Web 專案或未引入 MVC 時載入。
2. **輕量覆寫**：採用 `@ConditionalOnMissingBean` 讓服務可自行定義相同型別的 Bean 以覆蓋預設行為。
3. **國際化與一致性**：例外處理、回應包裝與多語系資源搭配使用，確保跨服務輸出一致，並可針對不同語系提供訊息。
4. **盡量無侵入**：除非必要不改寫原生行為；例如保留 `ResponseEntity` 與 RFC7807 物件原樣，讓服務仍可精細掌控特殊流程。

## 待評估項目
- 追蹤 ID Filter 目前僅以註解保留，如需啟用需重新開放 Bean 註冊並確認是否與現有 Gateway/Tracing 架構重複。
- CORS 策略與暴露標頭應依部署環境調整，避免過度開放。

