# API 契約設計指南

## 目標
- 透過 API Interface（`@RequestMapping` 介面）維持型別安全與一致的註解。
- 借助自動化工具，由 API Interface 產生 OpenAPI 規格、Controller 骨架與 Client SDK，降低重複開發成本。
- 將 REST 契約集中於 `sdk-common` 的 `*-rest-api` 模組，供 Controller 與外部客戶端共用。

## 模組分層
- `sdk-common/sdk-service-<domain>/<module>/<module>-rest-api`
  - 放置 API Interface、DTO（record）、公共常數與產出的 OpenAPI 檔案。
- `sdk-common/sdk-service-<domain>/<module>/<module>-rest-client`
  - 依 OpenAPI 規格產生 Feign/WebClient 等客戶端，供其他模組引用。
- `services/<service>/<service>-<module>`
  - 實際 Controller 實作，實作或擴充自動生成的 Controller 基礎類別，承接業務邏輯。

## API Interface 規範
- 套件路徑：`.../api/`，依資源劃分子套件（例：`api/order/OrderCommandApi`）。
	- 命名：`*Api` 或 `*RestApi`，以資源行為為語意（`OrderQueryApi`、`AccountPositionApi`）。
- 版本管理：以 `@RequestMapping("/api/v1/...")` 控制版本，升版時複製既有介面為 `v2`，避免破壞既有契約。
- 介面內：
	  - 使用 Spring MVC 註解（`@RequestMapping`、`@GetMapping`、`@PostMapping`）。
	  - 參數搭配 `@RequestBody`、`@PathVariable`、`@RequestParam` 等註解。
	  - DTO 一律使用 Java `record` 並放於 `.../api/dto/`。
	  - 可加入 `@Validated`、`@Schema` 等補充資訊供工具生成文件。

## 工具鏈與產出流程

### 1. 由 API Interface 產出 OpenAPI 規格
`*-rest-api` 模組設定 `springdoc-openapi-maven-plugin`：

```xml
<plugin>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-maven-plugin</artifactId>
    <version>1.8.0</version>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <apiDocsUrl>http://localhost:8080/v3/api-docs</apiDocsUrl>
                <outputFileName>${project.artifactId}.openapi</outputFileName>
                <outputDir>${project.build.directory}/openapi</outputDir>
                <skip>false</skip>
            </configuration>
        </execution>
    </executions>
</plugin>
```

搭配 `springdoc-openapi-starter-webmvc` 於測試時啟動 mini Spring context 並載入 API Interface。執行：

```bash
mvn -pl sdk-common/sdk-service-exchange/sdk-service-exchange-matching/sdk-service-exchange-matching-rest-api \
    -am springdoc-openapi:generate
```

完成後在 `target/openapi/` 取得 `*-rest-api.openapi.yaml`，供後續生成使用。

> 若不想啟動完整 Spring Boot，可撰寫 `@Configuration` 測試專用的 `MockMvc` 啟動器，或使用 `springdoc-openapi-maven-plugin` 的 `classes` 模式直接掃描註解。

### 2. 產生 Controller 基礎類別（Server Stub）
在對應服務模組引入 `openapi-generator-maven-plugin`，輸入前一步的 OpenAPI 檔案：

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>7.7.0</version>
    <executions>
        <execution>
            <id>generate-api-server</id>
            <phase>generate-sources</phase>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <inputSpec>${project.basedir}/../sdk-common/sdk-service-exchange/sdk-service-exchange-matching/sdk-service-exchange-matching-rest-api/target/openapi/sdk-service-exchange-matching-rest-api.openapi.yaml</inputSpec>
                <generatorName>spring</generatorName>
                <library>spring-boot</library>
                <apiPackage>open.vincentf13.exchange.matching.generated.api</apiPackage>
                <modelPackage>open.vincentf13.exchange.matching.generated.dto</modelPackage>
                <additionalProperties>
                    <interfaceOnly>true</interfaceOnly>
                    <useTags>true</useTags>
                    <skipDefaultInterface>true</skipDefaultInterface>
                </additionalProperties>
                <output>${project.build.directory}/generated-sources/openapi-server</output>
            </configuration>
        </execution>
    </executions>
</plugin>
```

常見策略：
- `interfaceOnly=true`：只生成 `@RestController` 對應的基礎介面/抽象類別，再由我們的 Controller 實作。
- `skipDefaultInterface=true` 可避免與既有 API Interface 重複；若要生成具體 `@RestController` 骨架，可改成 `false`，並在服務內繼承該抽象類別。
- 將輸出目錄加到 `build-helper-maven-plugin` 的 `add-source`，讓生成碼參與編譯。

產出後於服務模組建立實際 Controller：

```java
@RestController
@RequiredArgsConstructor
public class OrderController extends OrderApiController { // 生成的抽象 Controller

    private final OrderApplicationService orderApplicationService;

    @Override
    public ResponseEntity<OrderDetailResponse> createOrder(OrderCreateRequest request) {
        return ResponseEntity.ok(orderApplicationService.create(request));
    }
}
```

### 3. 產生外部客戶端
在 `*-rest-client` 模組使用同一份 OpenAPI 檔案生成 Feign 或 WebClient：

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>7.7.0</version>
    <executions>
        <execution>
            <id>generate-feign-client</id>
            <phase>generate-sources</phase>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <inputSpec>${project.parent.relativePath}/sdk-service-exchange-matching-rest-api/target/openapi/sdk-service-exchange-matching-rest-api.openapi.yaml</inputSpec>
                <generatorName>java</generatorName>
                <library>feign</library>
                <apiPackage>open.vincentf13.exchange.matching.client.api</apiPackage>
                <modelPackage>open.vincentf13.exchange.matching.client.dto</modelPackage>
                <configOptions>
                    <dateLibrary>java8</dateLibrary>
                    <useFeignClient>true</useFeignClient>
                </configOptions>
                <output>${project.build.directory}/generated-sources/openapi-client</output>
            </configuration>
        </execution>
    </executions>
</plugin>
```

常見替代方案：
- `generatorName=webclient` 產生 Reactor WebClient。 
- `generatorName=kotlin-spring` 產生 Kotlin 客戶端。
- 若仍需手寫輕量客戶端，可改為 `generatorName=typescript-fetch` 等語言變體。

同樣透過 `build-helper-maven-plugin` 將生成碼納入編譯路徑，並在 `pom.xml` 把 `openapi-generator-maven-plugin` 放於 `generate-sources` 之前執行。

## 開發流程建議
1. **設計契約**：在 `*-rest-api` 編寫/調整 API Interface 與 DTO。
2. **更新規格**：執行 `springdoc-openapi:generate` 產生最新 `openapi.yaml`。
3. **生成骨架**：於需要的模組（controller、client）執行 `openapi-generator:generate`，生成最新程式碼。
4. **實作業務**：在服務模組擴充生成的抽象類別；在客戶端模組增加封裝或自訂錯誤處理。
5. **覆蓋測試**：
   - 對生成 Controller 撰寫整合/契約測試，確保輸入輸出契合。
   - 對客戶端包裝增加 smoke test（可透過 WireMock 模擬 API）。
6. **版本管理**：
   - 每次功能變動更新 `CHANGELOG` 或對應設計文件。
   - 若需要破壞性調整，新增 `v2` 介面並持續維護 `v1`，待客戶端更新後再移除。

## 實務建議
- 生成檔案放在 `target/generated-sources`，避免進版控；僅保留自訂包裝或 facade。
- 將 `openapi.yaml` 作為發佈 artefact（例如上傳至 GitHub Packages 或內部 registry），提供其他語言的團隊使用。
- 針對 `@SecurityRequirement`、`@Tag` 等註解保持完整，確保文件與 Swagger UI 一致。
- 在 CI Pipeline 中加入 "契約生成 → client build" 的驗證步驟，避免遺漏執行生成流程。
- 若需要快照測試，可將 `openapi.yaml` 與先前版本比較，確保不會意外刪除欄位或變更型別。

透過上述流程，可從單一 API Interface 同步生產 Controller 骨架與多種客戶端實作，顯著降低手動同步的負擔並提升契約一致性。
