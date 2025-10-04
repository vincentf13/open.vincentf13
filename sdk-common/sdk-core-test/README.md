# sdk-core-test

共享測試支援模組，集中對外服務可重用的測試組態與範例。透過 Testcontainers 啟動 MySQL、Redis、Kafka 等臨時資源，並提供 Spring Boot 切片測試的樣板。

## 主要內容
- `TestBoot`：共用的 `@SpringBootConfiguration`，掃描 `open.vincentf13` 套件並啟用自動設定。
- `Base*TestContainer`：封裝 Testcontainers 的啟動與動態屬性註冊。
- 範例測試：`MysqlTest`、`MybatisUserMapperTest`、`RedisTest`、`KafkaTest`、`DemoControlleTest` 展示常見資料庫、ORM/Mapper、快取、訊息隊列與 Web 切片測試。

## 執行前準備
1. **Docker / Container Runtime**：Testcontainers 需要可用的 Docker Daemon；若在 CI 或 WSL 環境，請確認具備對應權限。
2. **暫存檔寫入位置**：若系統不允許 JNA 在預設 tmp 目錄建檔，可加上 `-Djna.tmpdir=<repo>/tmp` 指向專案下可寫的目錄。
3. **ByteBuddy/Mockito 自附掛**：在 JDK 23+ 或某些受限環境，需額外設定 `-Djdk.attach.allowAttachSelf=true`，避免 Mockito 初始化失敗。
4. **Ryuk 管理容器**：若安全性政策禁用 Ryuk，可設定 `TESTCONTAINERS_RYUK_DISABLED=true`，並自行負責容器清理。
5. **切換外部資源**：若欲改用服務自行配置的 MySQL/Redis/Kafka，可設定 `-Dsdk.core.testcontainers.enabled=false`（或對應環境變數 `SDK_CORE_TESTCONTAINERS_ENABLED=false`）；亦可針對單一資源使用 `sdk.core.testcontainers.mysql.enabled` 等旗標細部控制。

## 建議啟動方式
- 單模組測試：`mvn -pl sdk-common/sdk-core-test -am test`
- 指定測試類別：`mvn -pl sdk-common/sdk-core-test -Dtest=MybatisUserMapperTest test`
- 跳過測試打包：`mvn -pl sdk-common/sdk-core-test -DskipTests package`

> **提示** `mvn` 首次執行會在 `~/.m2` 建立快取目錄；若遇到權限不足，請調整目錄權限或改以專案內自訂路徑。

## 疑難排解
- **Testcontainers 報 JNI libjnidispatch.so Permission denied**：確認 `jna.tmpdir` 指向可寫目錄，或在 README 開頭提到的 tmp 位置設定。
- **Could not initialize plugin: org.mockito.plugins.MockMaker**：在 JVM 參數加入 `-Djdk.attach.allowAttachSelf=true`，或於 `junit-platform.properties` 停用自動 Mockito 擴充。
- **Found multiple @SpringBootConfiguration**：避免在同一測試上下文同時載入多個 `@SpringBootConfiguration`，若需要額外 bean，改用 `@TestConfiguration`。

## 為服務模組複用
- 測試模組可直接依賴 `sdk-core-test`，並繼承  `Base*TestContainer` 或匯入 `TestBoot`。
- 若服務需要額外容器，可在專案內實作 `BaseXxxTestContainer`，並在 README 補充使用方式。
