
# Spring Boot 配置覆蓋順序（整合 Nacos / 多 profile / 依賴）

### 1. 依賴 Jar 內部的 `application.yml`
- 若某個外部依賴（library 或 starter）內有 `application.yml`，屬於 **classpath:jar!/application.yml**。
- 這層最先被載入，**最低優先權**。
- 用於提供預設值，不應放業務設定。
    

---

### 2. 專案自身 `src/main/resources/application.yml`c
- 本地專案主要配置文件。
- 若依賴中同樣定義了相同屬性，這裡會**覆蓋依賴內的值**。
    

---

### 3. Profile 檔案：`application-<profile>.yml`
- 例如：`application-dev.yml`
- 被激活的 Profile（透過 `spring.profiles.active=dev`）會**覆蓋 base 檔案**。
- 若同一屬性在 base 與 profile 中皆存在，以 profile 為準。
    

---

### 4. 外部配置（同層級覆蓋 classpath）
- 放在應用程式執行目錄的 `config/`、當前目錄、`classpath:/config/`、`classpath:/` 依序被載入。
- 即 **外部檔案 > 專案內部檔案**。
- 通常 Docker 或部署時掛載此層以動態覆蓋。
    

---

### 5. Config Server（如 Nacos、Spring Cloud Config）
- 若啟用 `spring.cloud.config` 或 `spring.cloud.nacos.config`，拉取遠端設定。
- **預設會覆蓋本地檔案**，除非特別設 `spring.cloud.config.override-none=true`。
- Nacos 可依命名空間、group、dataId 分層，若多個配置檔載入，覆蓋順序依 `spring.cloud.nacos.config.extension-configs[n].refresh` 和 `priority` 決定。
    - **後定義的覆蓋前定義的**
    - `shared-configs` < `extension-configs` < `main config (dataId=application.yml)`
        

---

### 6. 命令列參數
- 例如：
    
    ```bash
    java -jar app.jar --server.port=9000
    ```
- **優先於所有配置檔**。
    

---

### 7. 環境變數
- 系統環境變數、`SPRING_APPLICATION_JSON`。
- 比命令列低，通常用於容器環境。
    

---

## 實際常見優先序（由低到高）

1. 依賴內部 `application.yml`
    
2. 專案內部 `application.yml`
    
3. 專案內部 `application-dev.yml`（當前 profile）
    
4. 外部配置（同目錄 config/application.yml）
    
5. Config Server / Nacos 拉取配置
    
6. 命令列參數
    
7. 系統環境變數
    