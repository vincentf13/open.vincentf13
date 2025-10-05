# sdk-core-test 模組設計

* 結構

  * 單元、切片、整合、端到端分層管理
  * `*Test` 給單元與切片，`*IT` 給整合與端到端

* 測資料與隔離

  * 每測例獨立資料鍵或隨機後綴
  * 資料庫用 Testcontainers，透過 `@DynamicPropertySource` 注入連線
  * 不濫用 `@DirtiesContext`，避免反覆重建 Spring 容器

* 斷言與可讀性

  * AssertJ 的鏈式斷言與 `assertThatThrownBy` 提升可讀性
  * BigDecimal 用比較器，物件比對用 `usingRecursiveComparison`

* 時間與重試

  * 注入 `Clock` 以測時間邏輯
  * 有最終一致性的流程一律用 Awaitility，不用 `Thread.sleep`

* 切片測試

  * Web 用 `@WebMvcTest` 配合 `MockMvc`
  * 資料層用 `@DataJpaTest` 或 `@MybatisTest`，外加 `@AutoConfigureTestDatabase(replace = NONE)` 配容器 DB

* 端到端與依賴

  * RestAssured 驗 API，Awaitility 等待最終一致性
  * 下游 HTTP 用 WireMock，Kafka/Redis/MySQL 用 Testcontainers 模組
  * 容器宣告 `@Container static final` 降低啟動成本


* 並行安全

  * 只用 `PER_METHOD` 實例生命週期
  * 禁止共享可變靜態狀態與暫存
  * 需要序列化者以 `@Execution(SAME_THREAD)` 或標記 `@Tag("serial")` 再由 CI 排除並行

* 可觀測性

  * 測試期開 `enableLoggingOfRequestAndResponseIfValidationFails`
  * 對自定義指標與追蹤用 InMemory registry/exporter 斷言是否產生

* CI 與分層執行

  * PR 跑單元與關鍵切片，夜間跑整合與端到端
  * 使用測試標籤：`fast`、`slow`、`e2e` 以便分流

* 失敗可診斷

  * 測試命名描述明確
  * 失敗訊息包含輸入、期望、實際
  * 對隨機測試固定種子或輸出 seed 方便重現



# 推薦配置

`src/test/resources/junit-platform.properties`

```properties
# 啟用平行執行
junit.jupiter.execution.parallel.enabled=true
# 測試方法預設並發
junit.jupiter.execution.parallel.mode.default=concurrent
# 測試類預設並發
junit.jupiter.execution.parallel.mode.classes.default=concurrent
# 並行度 = CPU 核心數（動態）
junit.jupiter.execution.parallel.config.strategy=dynamic
# 預設超時門檻（避免卡死），單測可用 @Timeout 覆寫
junit.jupiter.execution.timeout.default=5 m
# 自動載入擴充（如 MockitoExtension）
junit.jupiter.extensions.autodetection.enabled=true
# 每測例各自建立測試實例，避免共享狀態
junit.jupiter.testinstance.lifecycle.default=per_method
# 顯示名稱用底線轉空白，報表更可讀
junit.jupiter.displayname.generator.default=org.junit.jupiter.api.DisplayNameGenerator$ReplaceUnderscores
```

Maven（建議）

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <version>3.2.5</version>
  <configuration>
    <failIfNoTests>false</failIfNoTests>
    <reuseForks>true</reuseForks>
    <!-- 多 JVM 需求大時再調，平時交給 JUnit 內部並行即可 -->
    <!-- <forkCount>1</forkCount> -->
  </configuration>
</plugin>
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-failsafe-plugin</artifactId>
  <version>3.2.5</version>
  <executions>
    <execution>
      <goals><goal>integration-test</goal><goal>verify</goal></goals>
    </execution>
  </executions>
</plugin>
```
