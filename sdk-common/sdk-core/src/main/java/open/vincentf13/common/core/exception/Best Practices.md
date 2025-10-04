# 例外處理最佳實踐

## 分層策略
- **API 層**：拋出ControllerException，統一攔截並轉換底層異常，回傳具語意的業務錯誤。
- **Service 層**：拋出  ServiceException (checked)  受檢異常，，讓上層決定最終處置。
- **Infrastructure / Utility 層**：拋出InfraException，以 Runtime 例外包裝外部資源失敗，避免業務層過度 try-catch；仍要提供足夠診斷資訊。

## 共通指引
- 保留原始異常與錯誤碼，使用 XxxEception(..., ex) 參數，保留原始異常，減少排查時間並方便集中記錄。
- 可透過 `addSuppressed` 紀錄資源釋放過程的次要異常，避免主因被覆寫。
- 使用一致的訊息格式：`[錯誤碼] 描述 | 補充資訊`，並於日誌中附加脈絡（使用者、請求、traceId）。

## 範例：保留 root cause
```java
try {
  repository.read("data.txt");
} catch (NoSuchFileException ex) {
  throw new AppException("FILE_NOT_FOUND", "讀取檔案失敗", ex);
}
```
輸出範例：
```
open.vincentf13.common.core.exception.AppException: [FILE_NOT_FOUND] 讀取檔案失敗
    at com.example.service.FileService.read(FileService.java:25)
Caused by: java.nio.file.NoSuchFileException: data.txt
    at sun.nio.fs.UnixException.translateToIOException(UnixException.java:86)
```
保留 `cause` 後可直接看到真正觸發原因。

## 範例：addSuppressed 關聯次要異常
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
