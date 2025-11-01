# API 契約設計指南

## 推薦策略概覽
- **Controller-first（建議給敏捷開發）**：直接在服務模組撰寫 Spring MVC Controller，透過工具掃描註解生成 OpenAPI，進而輸出 API Interface、客戶端 SDK。
- **Interface-first（適用長期維護）**：先在 `*-rest-api` 模組定義 API Interface，再由控制器與客戶端實作；適合契約穩定、需要跨語言共享時。

> 以下重點說明 Controller-first 流程，並保留 Interface-first 做為補充方案。兩種方式都以同一份 OpenAPI 規格為核心，確保客戶端與服務端同步。

## Controller-first 流程

### 1. 撰寫 Controller
- 位置：`services/<service>/<module>/src/main/java/.../controller/`
- 命名：`*Controller`，依資源分類（例：`OrderCommandController`）。
- 註解要求：
  - `@RestController` + `@RequestMapping("/api/v1/orders")` 控制版本。
  - 方法使用 `@GetMapping`、`@PostMapping` 等註解，搭配 `@Operation`（springdoc）補充說明。
  - 請使用 DTO record（來自 `*-rest-api` 模組或暫置於服務內 `api/dto/`）。

### 2. 生成 OpenAPI 規格
在 Controller 所屬模組加入 `springdoc-openapi-maven-plugin`，利用 `annotations` 模式直接掃描已編譯的 Controller 類別：

```xml
<plugin>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-maven-plugin</artifactId>
    <version>1.8.0</version>
    <executions>
        <execution>
            <id>generate-openapi</id>
            <phase>prepare-package</phase>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <mode>annotations</mode>
                <apiDocsUrl>http://localhost:8080/v3/api-docs</apiDocsUrl>
                <outputFileName>${project.artifactId}.openapi.yaml</outputFileName>
                <outputDir>${project.build.directory}/openapi</outputDir>
                <springBootVersion>${spring-boot.version}</springBootVersion>
                <skip>false</skip>
            </configuration>
        </execution>
    </executions>
</plugin>
```

執行指令（模組恢復後使用）：

```bash
mvn -pl service/exchange/exchange-user -am springdoc-openapi:generate
```

> 建議在 `service/exchange/exchange-user` 模組內維護 OpenAPI YAML，或於 `exchange-user-rest-api` 直接更新契約檔案以保持版本一致。

生成結果放在 `target/openapi/`，建議將 YAML 檔複製或發佈到 `sdk-contract/sdk-<domain>/<module>/<module>-rest-api/src/main/resources/openapi/` 以利版本控。

### 3. 從 OpenAPI 產出 API Interface（可重用）
在 `*-rest-api` 模組使用 `openapi-generator-maven-plugin`，以剛產生的 YAML 為輸入，生成純介面：

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>7.7.0</version>
    <executions>
        <execution>
            <id>generate-api-interface</id>
            <phase>generate-sources</phase>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <inputSpec>${project.basedir}/src/main/resources/openapi/sdk-service-exchange-user.openapi.yaml</inputSpec>
                <generatorName>spring</generatorName>
                <library>spring-boot</library>
                <additionalProperties>
                    <interfaceOnly>true</interfaceOnly>
                    <useTags>true</useTags>
                    <skipDefaultInterface>false</skipDefaultInterface>
                    <useSpringBoot3>true</useSpringBoot3>
                </additionalProperties>
                <apiPackage>open.vincentf13.exchange.user.api</apiPackage>
                <modelPackage>open.vincentf13.exchange.user.dto</modelPackage>
                <output>${project.build.directory}/generated-sources/openapi-api</output>
            </configuration>
        </execution>
    </executions>
</plugin>
```

- 生成檔案會包含 `UserApi` 等介面，可被 Controller `implements`，也可提供 client 模組使用。
- 使用 `build-helper-maven-plugin` 將 `generated-sources/openapi-api` 納入 compile path（避免手動移入 `src/`）。

### 4. 產生客戶端 SDK
在 `*-rest-client` 模組採用相同 YAML，根據需求選擇 Feign 或 WebClient：

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
                <inputSpec>${project.parent.relativePath}/exchange-user-rest-api/src/main/resources/openapi/sdk-service-exchange-user.openapi.yaml</inputSpec>
                <generatorName>java</generatorName>
                <library>feign</library>
                <apiPackage>open.vincentf13.exchange.user.client.api</apiPackage>
                <modelPackage>open.vincentf13.exchange.user.client.dto</modelPackage>
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

> 想要 WebClient / Retrofit / Typescript 客戶端，只需調整 `generatorName` 與 `library`。

### 5. 自動化建議
- 建立 Makefile 或 Maven profile（如 `-Pcontract`) 將「生成 OpenAPI → 生成 Interface → 生成 Client」串成單一指令。
- 在 CI Pipeline 中新增步驟檢查 `openapi.yaml` 與生成檔案是否最新，避免遺漏更新。
- 若 Controller 變動頻繁，可在 IDE 內設定 File Watcher 或使用 `openapi-generator-cli` 搭配 `--watch` 選項。

## Interface-first（補充）
若偏好先設計介面，再讓 Controller 實作，可沿用早前版本的流程：
1. 在 `*-rest-api` 撰寫 `*Api` 介面與 DTO。 
2. 利用 MapStruct/Assembler 在服務內組裝.
3. 透過 OpenAPI Generator 產出客戶端。 

此模式契約最穩定，但初期手動成本較高。

## IDE / 工具支援
- **IntelliJ IDEA Plugins**：
  - `OpenAPI Generator`（JetBrains Marketplace）：直接在 IDE 以 GUI 執行 `openapi-generator`，可設定 profile 並一鍵更新 client。
  - `OpenAPI Specifications / Swagger Editor`：提供 YAML 編輯、語法提示與差異比較。
  - `Smart Tomcat + springdoc` 組合：可在 IDE 內啟動應用、即時預覽 `/swagger-ui.html`。
- **命令列工具**：
  - `openapi-generator-cli`：支援 `generate`, `validate`, `list`，可結合 `npm` 或 `make` 寫自動化腳本。
  - `springdoc-openapi-starter-webmvc-ui`：啟動後可在 `/v3/api-docs` 取得 JSON，適合本地預覽。
- **其他選項**：
  - `Stoplight Studio`、`Insomnia Designer` 等 GUI 工具可載入 OpenAPI，提供視覺化編輯與 Mock。

## 工作流程建議
1. 寫 Controller → 單元/整合測試綠燈。
2. 執行 `mvn springdoc-openapi:generate` 產生最新 OpenAPI。
3. 執行 `mvn openapi-generator:generate` 更新 API Interface 與客戶端。
4. Controller `implements` 生成的介面，並於客戶端模組導入新 artifact。
5. 提交 PR 前確認 `openapi.yaml` 已更新並通過契約測試（例如使用 WireMock 或 rest-assured）。

透過 Controller-first 自動化流程，可維持原有開發習慣，又能自動同步 API 介面與多語系客戶端，減少手動同步的錯誤與成本。
