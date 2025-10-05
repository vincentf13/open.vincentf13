# sdk-core-log 模組設計

## 模組定位與目標
- 提供平台級日誌基線（格式、輸出通道、輪轉策略），避免各服務重複定義。
- 與追蹤體系整合：預設在 Pattern 中印出 `traceId`、`spanId`，讓例外攔截器與觀測套件能串聯。
- 提供在開發與生產環境差異化配置（彩色 Console、檔案輪轉），同時保留易於覆寫的 Spring 框架內部日誌之整合標準配置。
- 捆綁依賴與排除策略，統一切換Log4j2，配置高效能信息對列與異步寫入，減少跨模組日誌框架混用造成的衝突。

## 組成概覽
| 檔案                   | 位置                                      | 說明                                                                                      |
| -------------------- | --------------------------------------- | --------------------------------------------------------------------------------------- |
| `pom.xml`            | `sdk-common/sdk-core-log/pom.xml`       | 排除 `spring-boot-starter-logging`，改用 `spring-boot-starter-log4j2`，作為平台的 logging starter。 |
| `application.yaml`   | `src/main/resources/application.yaml`   | 預設 `dev` profile 下的日誌等級建議，涵蓋 Web、資料層、Kafka、Redis 等重點套件。                                 |
| `log4j2-spring.xml`  | `src/main/resources/log4j2-spring.xml`  | 預設 Log4j2 配置，依 profile 拆分 `dev`（Console）與 `prod`（RollingFile），支援 Spring 屬性注入。           |
| `logback-spring.xml` | `src/main/resources/logback-spring.xml` | 預留 Logback 版本配置，供少數需要沿用 Logback 的應用複製或參考（預設不會生效）。                                       |

## 依賴與自動配置策略
- 模組新增後，Spring Boot 會載入 `log4j2-spring.xml`，並忽略原生 Logback；若服務仍需 Logback，可在自身專案排除 `spring-boot-starter-log4j2` 並引入 `spring-boot-starter-logging`，同時複製 Logback 配置。
- `application.yaml` 僅在套件被引用且啟用 `dev` profile 時生效，可作為 baseline，服務可在自身的 `application-*.yaml` 覆寫對應區段。
- 所有配置皆透過 `spring:logging.file.path` 與 `spring:logging.level.root` 參數化，利於在不同部署環境調整。

## 日誌記錄器配置細節
- **Pattern**：Console 與 File 均帶入 `%X{traceId}`、`%X{spanId}`，確保與 Sleuth / OTel MDC 欄位一致；缺值時以 `-` 補齊。
- **AsyncAppender**：預設使用 Log4j2 的非阻塞寫入；Logback 版本則示範如何透過 `AsyncAppender` + `neverBlock` 調節吞吐與穩定性。
- **輪轉策略**：
  - `dev`：以日期 + 大小的雙重條件，每日保留 7 天；利於本機除錯且檔案不爆量。
  - `prod`：保留 30 天，單檔 100MB，自動 gzip 並刪除過期檔案。
- **子系統調校**：`application.yaml` 中預設重要套件的等級，維持 `root=INFO`、大量 I/O 元件為 `INFO/WARN`，而自家 mapper 則預設 DEBUG，便於在 dev 時檢視 SQL。

## Profile 與環境層級行為
- `dev` Profile：
  - Console 彩色輸出、非阻塞寫檔示範選項。
  - 支援同時輸出檔案，方便保留歷史記錄，預設 queue size 8K，`neverBlock=false` 保證不丟失。
- `prod` Profile：
  - 僅輸出檔案（可依需求加 Console）。
  - queue size 提升至 16K，`neverBlock=false`，尖峰時阻塞呼叫執行緒避免遺失。
  - 支援以 Spring 屬性調整 `LOG_PATH`、`LOG_LEVEL_ROOT`。

## 導入與使用流程
1. **加入依賴**：在服務模組 `pom.xml` 引用 `open.vincentf13.common:sdk-core-log`。
2. **設定 logging 目錄**：於服務 `application-*.yaml` 中設定 `logging.file.path=/var/log/<service>`，或交由執行環境注入環境變數。
3. **調整套件等級（選用）**：服務可在自身配置覆寫 `logging.level` 區塊，與 baseline 合併後生效。
4. **驗證 traceId**：透過現有的 `CommonException`、HTTP Filter 等元件，確保 MDC 已注入 `traceId`、`requestId`；日誌輸出應可見對應欄位。
5. **監控**：利用平台標準 log shipping（如 Filebeat）收集 `${LOG_PATH}` 下的檔案；若需 log rotation 與 shipping 協調，可調整 `maxHistory` 與檔案模式。

## 擴展與覆寫建議
- 若需新增專屬 Appender（如 Kafka、Elastic），建議在服務專案建立 `log4j2-spring.xml`，並使用 `<SpringProperty>` 引用平台預設屬性；Log4j2 會優先採用位於 classpath 前端的配置檔。
- 對於雲原生部署（stdout-only），可在服務端移除 RollingFile Appender，改為純 Console；建議保留 Pattern 片段，以維持 traceId 一致性。
- 若想強化審計與分類，可利用 Log4j2 RoutingAppender 搭配 Marker；此模組僅提供 baseline，不限制後續擴充。

## 風險與追蹤議題
- 模組預設打包 Log4j2，若某些依賴仍硬性要求 Logback，需在使用方進行依賴排除與配置覆蓋。
- Logback 配置檔僅作為參考樣板；若被同時放入 classpath，可能導致使用者混淆，建議透過文件提醒只選擇其中一套。
- 需定期檢視 queue size 與磁碟使用情況，避免在高峰或 IO 受限時造成阻塞；可配合監控指標（JVM log events dropped）建立告警。
