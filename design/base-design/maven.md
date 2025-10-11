# Maven 與 MVND 架構說明

專案採多模組 Maven 佈局，並透過 `.mvn/` 目錄集中管理 Maven 與 Maven Daemon (mvnd) 的啟動參數。本文件整理 POM 架構、建議配置以及 mvnd 使用重點與效能差異，方便協作時快速對齊。

# 多模組 POM 架構

## 根 POM（`pom.xml`）
- 以 `spring-boot-starter-parent` 作為 parent，統一 Spring Boot 3.3.4 與相依版本 `pom.xml:7-34`。
- 定義 `sdk-common` 與 `service` 兩大聚合模組，維持 SDK 與業務服務分層 `pom.xml:22-25`。
- 於 `<dependencyManagement>` 中集中鎖定 Spring Cloud、MyBatis、Testcontainers 與內部 SDK 模組版本，避免子模組重複宣告 `pom.xml:36-143`。
- `<pluginManagement>` 負責 Spring Boot Maven Plugin 的統一配置（重打包、Build Info 產出），子模組只需繼承即可 `pom.xml:146-169`。
- `<build><plugins>` 區塊預先放入 Surefire/Failsafe 測試流程設定，確保單元與整合測試的預設行為一致 `pom.xml:171-195`。

## SDK 聚合模組（`sdk-common/pom.xml`）
- 聚合共享 SDK 與基礎建設模組，例如 `sdk-core`、`sdk-infra-*`、`sdk-service-exchange` 等，統一以 `open.vincentf13.common` 群組維護 `sdk-common/pom.xml:14-35`。
- 子模組皆繼承根 POM 的版本與插件治理，僅需專注於各自的依賴與程式碼。

## Service 聚合模組（`service/pom.xml`）
- 聚合 `service-exchange`、`service-test`、`service-template` 等實際服務，協助業務面程式分層 `service/pom.xml:15-24`。
- 額外聲明 Spring Boot Maven Plugin，讓可執行 JAR 的打包與部署保持一致 `service/pom.xml:26-31`。

---

# Maven 推薦配置（`.mvn/maven.config`、`.mvn/jvm.config`）
- 預設啟用並行構建 `-T 1C`，讓 Maven 自動依 CPU 核心數分配工作執行緒 `.mvn/maven.config:9`。
- 打開 Maven Build Cache 與增量編譯，重複構建時省去重複的編譯與資源處理 `.mvn/maven.config:12-18`。
- 調高 artifact 下載執行緒數（20 條），首次下載依賴時能顯著縮短時間 `.mvn/maven.config:15-16`。
- 預設跳過 Javadoc、Checkstyle、Enforcer，可在需要時加上 `-Denforcer.skip=false` 等參數覆寫 `.mvn/maven.config:26-33`。
- JVM 參數預設在 512m ~ 1.5g 之間，搭配 G1 與字串去重降低 GC 壓力；`-XX:ActiveProcessorCount=2` 在 CI 上可避免超載 `.mvn/jvm.config:1-9`。

> 建議透過 `./mvnw clean verify` 或 `./mvnw -T 1C package` 執行，確保團隊共用相同設定。必要時可在命令列加上 `-Dmaven.test.skip=true` 進一步縮短時間。

---

# MVND 推薦配置（`.mvn/mvnd.properties`）
- `mvnd.threads=1C`、`mvnd.artifact.threads=20`、`mvnd.build.cache.enabled=true` 等設定與 Maven 保持一致，確保指令互換時有相同行為 `.mvn/mvnd.properties:7-18`。
- `mvnd.jvmArgs` 直接複用 Maven 的 JVM 參數，Daemon 啟動後即可熱身，不需額外手動調整 `.mvn/mvnd.properties:33-34`。
- `mvnd.daemonStorage` 指到 `~/.m2/registry`，持久化 Daemon 狀態與 Build Cache `.mvn/mvnd.properties:36`。

**常用指令**
- `mvnd clean verify`：快速構建，享受 Daemon 帶來的熱身優勢。
- `mvnd --status`：查看當前活躍的 Daemon 與快取狀態。
- `mvnd --stop`：手動釋放 Daemon（例如變更 JVM 參數或釋放記憶體）。

---

# MVN 與 MVND 的效能差異與選擇
- **啟動時間**：mvnd 以 Daemon 常駐 JVM、類載入與 Plugin，第二次之後的構建可節省 1~2 秒啟動開銷，對短流程（`compile`、`test`）特別明顯。
- **編譯階段**：在同樣啟用 Build Cache / Incremental 的情況下，mvnd 多執行緒排程更積極；實務上中型模組可比傳統 Maven 快 20~40%。
- **依賴下載**：兩者同樣受益於 `.m2` 快取與平行下載設定，速度相當；首次下載仍受網路頻寬限制。
- **資源占用**：mvnd Daemon 常駐會額外佔用一組 JVM 記憶體，若在記憶體受限的 CI/容器環境，可改回 `./mvnw` 降低峰值占用。

> 建議：本地開發預設使用 `mvnd`（快速回饋），CI / 一次性腳本維持 `./mvnw`，確保環境可復現。

---

# 快速檢查與疑難排解
- 查看有效設定：`./mvnw -X help:effective-settings` 或 `mvnd -X help:effective-pom`。
- 清除快取後重建：`mvnd --stop` + `./mvnw clean install -U`，避免舊快取造成的奇怪行為。
- 版本升級：調整根 POM 的 `<properties>` 或 `<dependencyManagement>` 後，記得以 `./mvnw versions:display-dependency-updates` 驗證是否有衝突。
