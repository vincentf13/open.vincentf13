# 引用模塊: sdk-core

```text
                  +--------------------+
                  |   CommonException  |
                  +--------------------+
                           ^
          +----------------+-----------------+
          |                                  |
 +----------------------+      +------------------------+
 | BaseCheckedException |      |   BaseRuntimeException |
 |      (Exception)     |      |    (RuntimeException)  |
 +----------------------+      +------------------------+
          ^                                  ^       ^
          |                                  |       |
 +------------------+        +-------------------+   +-----------------+
 | ServiceException |        | ControllerException | | InfraException |
 +------------------+        +-------------------+   +-----------------+

ErrorCode (interface) ---> provided to all exception constructors of BaseChecked/BaseRuntime families
```

## 例外繼承與物件設計
- **核心骨架**：
	  - **CommonException**：`CommonException.initMeta()` 會從 `MDC` 讀取 `traceId`、`requestId` 並寫入 `meta`；以便處理例外或寫入日誌時，可以清楚知道例外發生時的上下文。user id等業務級別信息，由 new 例外時根據當時資訊注入。
	  - `BaseRuntimeException` 繼承 `RuntimeException` 並實作 `CommonException`，統一保存 `code` 與 `meta`。
	  - `BaseCheckedException`繼承 `Exception` 並實作 `CommonException`，提供相同欄位給需要受檢語意的例外。
- **層級專用類別**：
	- `ControllerException`： 延伸 `BaseRuntimeException`，統一攔截並轉換底層異常，回傳具語意的業務錯誤。
	- `ServiceException` ：延伸 `BaseCheckedException`，拋出  ServiceException (checked)  受檢異常，讓上層決定最終處置。；必要時可注入底層 exception 以保留 root cause 或傳遞自訂 `meta`。
	- `InfraException`：拋出InfraException，以 Runtime 例外包裝外部資源失敗，避免業務層過度 try-catch；仍要提供足夠診斷資訊。
- **錯誤碼模型**：所有例外都必須注入 `ErrorCode` 實作（如 `BackendErrorCodes` 枚舉），統一錯誤碼與預設訊息。
- 呼叫端皆可透過 `getCode()`、`getMeta()` 提取錯誤碼與上下文資訊。

## User Case
- 分層設計：
	- Controler層：底層拋出之例外由 ControllerException 統一轉化為具語意的業務錯誤，並由例外攔截器 統一紀錄日誌，後回傳給客戶端。
	- Service 層： 每個接口，須註明會拋出之受檢異常，方便調用者處理可能發生之異常。
	- `InfraException`：拋出InfraException，以 Runtime 例外包裝外部資源失敗，避免業務層過度 try-catch；仍要提供足夠診斷資訊。
-  生成例外之參數說明：
	- ErrorCode：使用 ErrorCode 統一 Code與信息。
	- String errorMessage 參數用於附加口語化描述例外場景。 可使用 OpenLog 物件，寫入 log 信息後，自動返回已格式化之 log 信息注入至此。
	- Map<String, Object> meta 用於附帶上下文信息，例如 user id, order id, trace id , req id。
- 保留原始異常：使用 `new ControllerException|InfraException(..., ex)` 形式保留 root cause底層異常信息，可縮短排查時間。 
- 透過 `addSuppressed` 掛載資源釋放等次要異常，避免主因被覆寫。
- 例外攔截器會將 `meta` 中的 `mdcTraceId`、`mdcRequestId` 注入日誌與 API 回應；自訂流程請沿用這些鍵值以確保可追蹤性。

## 範例：保留 root cause
```java
try {
  repository.read("data.txt");
} catch (NoSuchFileException ex) {
  throw new ControllerException(BackendErrorCodes.INTERNAL_ERROR, "讀取檔案失敗", ex);
}
```
輸出範例：
```
open.vincentf13.common.core.exception.OpenApiException: [50000] 讀取檔案失敗
    at com.example.service.FileService.read(FileService.java:25)
Caused by: java.nio.file.NoSuchFileException: data.txt
    at sun.nio.fs.UnixException.translateToIOException(UnixException.java:86)
```
保留 `cause` 後可直接看到真正觸發原因。

## 範例：`addSuppressed` 關聯次要異常
```java
try {
  process();
} catch (Exception main) {
  try {
    resource.close();
  } catch (Exception closing) {
    main.addSuppressed(closing);
  }
  throw main;
}
```
輸出範例：
```
java.lang.Exception: 主要異常
    at com.example.job.JobRunner.run(JobRunner.java:18)
  Suppressed: java.lang.Exception: 關閉資源時的異常
```
`addSuppressed` 可保留資源釋放失敗等次要訊息，便於事故鑑識。
