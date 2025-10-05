# sdk-core-test 模組設計

## 模組定位與目標
- 建立平台統一的測試基線（環境啟動、最佳實務），降低各服務自行堆疊測試基礎設施的成本。
- 提供容器化基礎隔離測試環境。透過 Testcontainers 快速提供 MySQL、Redis、Kafka 等虛擬測試環境，
  並以配置控制容器化與真實外部依賴切換，支援本地與 CI 雙軌情境。
- 提供  API、MySQL、Redis、Kafka 等切面測試範例，使單元測試覆蓋 infra 邏輯。
```
  test/open/vincentf13/common/core/test/ApiTest.java
  test/open/vincentf13/common/core/test/KafkaTest.java
  test/open/vincentf13/common/core/test/MybatisTest.java
  test/open/vincentf13/common/core/test/MysqlTest.java
  test/open/vincentf13/common/core/test/RedisTest.java
```
- 彙整 JUnit 5、Spring Test、AssertJ 等工具鏈配置，確保測試具備穩定性、可診斷性與良好併發效能。

## 核心組件
| 類別 / 檔案                                                                          | 位置                                                                                                                                                                                                                                         | 功能                                                                               |
| -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------- |
| junit-platform.properties                                                        | src/main/resources/junit-platform.properties                                                                                                                                                                                               | 提供Junit 5 baseline基礎配置                                                           |
| `BaseMySqlTestContainer`<br>`BaseRedisTestContainer`<br>`BaseKafkaTestContainer` | src/main/java/open/vincentf13/common/core/test/BaseKafkaTestContainer.java<br>src/main/java/open/vincentf13/common/core/test/BaseMySqlTestContainer.java<br>src/main/java/open/vincentf13/common/core/test/BaseRedisTestContainer.java<br> | 以 `Testcontainers` 建立可切換的 MySQL /REDIS等虛擬容器，並透過 `@DynamicPropertySource` 註冊容器配置。 |
| `TestContainerSettings`                                                          | src/main/java/open/vincentf13/common/core/test/TestContainerSettings.java                                                                                                                                                                  | 解析容器配置（System Property、環境變數），決定是啟動虛擬容器，或走真實依賴環境。                                 |
| `TestBoot`                                                                       | test/src/test/java/test/open/vincentf13/common/core/test/TestBoot.java`                                                                                                                                                                    | 共用 `@SpringBootConfiguration`；測試若需額外 bean 建議使用 `@TestConfiguration`。             |


## Testcontainers 切換模型
- **全域關閉  容器化測試環境**：
	- 關閉VM 參數：`-Dopen.vincentf13.common.core.test.testcontainer.enabled=false`
	- 環境變數：`OPEN_VINCENTF13_COMMON_CORE_TEST_TESTCONTAINER_ENABLED=false`
- **個別關閉 容器化測試環境資源別旗標**（MySQL / Redis / Kafka）：
	- 範例：`-Dopen.vincentf13.common.core.test.testcontainer.mysql.enabled=false`
	- 環境變數以 `_MYSQL_ENABLED`、`_REDIS_ENABLED`、`_KAFKA_ENABLED` 結尾。
- 旗標值接受 `false/0/no/off` 等字串；未設定則預設啟用容器。
- 容器採 `static final` 單例並懶啟動：
	- 多個測試類繼承同一基底可共用同一容器，降低啟動時間。
	- 呼叫 `stop()` 時亦會遵守旗標，避免在停用模式誤關閉外部資源。

## 測試分層建議
- **命名**：
	- 單元 / 切片：`*Tests`
	- 整合 / 端到端：`*IT`
- **分層策略**：
	- 單元測試搭配 Mockito、AssertJ，集中驗證純邏輯。
	- 切片測試使用 Spring Boot 切片註解：`@WebMvcTest` + `MockMvc`、`@DataJpaTest`、`@MybatisTest`、`@RestClientTest` 等。
	- 整合測試繼承對應的 `Base*TestContainer`，由動態屬性注入臨時資源
	- 端到端測試建議使用 RestAssured / Awaitility 驗證 API 與最終一致性。
- **隔離原則**：
	- 測試資料具唯一標識（例如加亂數或測試方法名稱）。
	 - 不濫用 `@DirtiesContext`，改以 `@TestConfiguration` 或測試雙伴實現細部隔離。
	 - 所有測試類別預設 `@TestInstance(PER_METHOD)`，避免共享可變狀態。

## Maven test plugin配置

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <version>3.2.5</version>
  <configuration>
    <failIfNoTests>false</failIfNoTests>
    <reuseForks>true</reuseForks>
  </configuration>
</plugin>
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-failsafe-plugin</artifactId>
  <version>3.2.5</version>
  <executions>
    <execution>
      <goals>
        <goal>integration-test</goal>
        <goal>verify</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

## 導入與使用流程
1. **加入依賴**：服務模組 POM 引入 `sdk-core-test`（scope `test`）。
2. **同步設定**：將此模組提供的 `junit-platform.properties` 與 Maven 插件設定複製至服務專案，或引用共用父 POM。
3. **選擇基底類**：依測試依賴選擇 `BaseMySqlTestContainer`、`BaseRedisTestContainer`、`BaseKafkaTestContainer`，必要時可自訂新類。
4. **配置容器配置**：在 CI 或特定環境設定容器容器，以切連連線到實際共享資源或使用本地容器。
5. **撰寫測試**：
	  - API/切片測試可搭配 `DemoController` 等樣板快速驗證 Web 行為。
	  - 與資料庫、快取、訊息相關的整合測試直接使用 Spring Data / Template。
6. **執行**：
	- 單模組：`mvn -pl sdk-common/sdk-core-test -am test`
	- 指定測試：`mvn -pl <service> -Dtest=YourTest test`

## 疑難排解
- **Testcontainers 無法啟動**：確認 Docker Daemon 可用，並檢查旗標是否不小心設為停用。
- **`libjnidispatch.so Permission denied`**：設定 `-Djna.tmpdir=<repo>/tmp` 或確保預設 tmp 目錄可寫。
- **`Could not initialize plugin: org.mockito.plugins.MockMaker`**：在 JVM 參數加入 `-Djdk.attach.allowAttachSelf=true`。
- **`Found multiple @SpringBootConfiguration`**：避免在測試 classpath 再宣告額外 `@SpringBootConfiguration`，若需額外 bean 請使用 `@TestConfiguration`。
## 延伸建議
- 考慮在 CI pipeline 依測試標籤（`fast`、`slow`、`e2e`）分層執行，縮短回饋時間。
- 若服務需要額外的外部資源（如 Elasticsearch、S3 模擬器），可參考現有基底實作新的 `BaseXxxTestContainer`，並沿用 `TestContainerSettings` 的旗標模式。
- 對最終一致性流程建議引入 Awaitility，避免使用 `Thread.sleep`；與時間相關邏輯可注入 `Clock` 以便 Mock。
