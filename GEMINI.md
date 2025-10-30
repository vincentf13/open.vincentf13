# Gemini 的工作日誌

> **版本**: 1.0
> **狀態**: 初始分析完成

## 關於我與這個文件

你好！我是 Gemini，一個大型語言模型，被設計用來作為你的智能CLI開發夥伴。

這個文件是我的專屬工作空間，用來記錄我對 `open.vincentf13` 這個項目的理解和心智模型。你可以把它看作是我的「第二大腦」。

我會在這裡：
- **記錄核心洞察**: 關於項目的架構、設計模式和關鍵業務邏輯。
- **形成行動計劃**: 在執行複雜任務前，在這裡規劃我的步驟。
- **存儲上下文**: 記住我們的重要對話和決策，以便長期協作。

這個文件是透明的，歡迎你隨時查看，以確保我對你的項目理解無誤。

---

## 我對 `open.vincentf13` 的心智模型

### 核心身份

我將此項目理解為一個**高度專業化的、面向雲原生部署的金融交易平台**。

- **關鍵詞**: `金融交易`, `微服務`, `Java`, `Spring Cloud`, `Kubernetes`, `高可用`

### 架構快照

我的腦海中有一張藍圖，它看起來像這樣：

```mermaid
graph TD
    subgraph 用戶端
        A[Web/Mobile App]
    end

    subgraph 基礎設施
        K8S[Kubernetes Cluster]
    end

    subgraph 核心服務 (在 K8S 中運行)
        GW[service-exchange-gateway]
    end

    subgraph 支撐組件 (在 K8S 中運行)
        KAFKA[Kafka/Redpanda]
        MYSQL[MySQL]
        REDIS[Redis]
        PROM[Prometheus/Grafana]
        ARGO[ArgoCD]
    end
    
    subgraph 開發與維運
        SDK[sdk-common]
        CI[GitHub Actions]
    end


    A --> GW
    %% 其他交易子服務暫時下架，等待重新引入
    
    核心服務 -- 使用 --> SDK

    subgraph 監控與部署
        CI -- builds/pushes --> DockerHub -- triggers --> ARGO
        ARGO -- deploys --> K8S
        PROM -- 監控 --> 核心服務
    end
```

### 關鍵設計原則

- **契約先行 (Contract-First)**: 透過 OpenAPI/AsyncAPI 定義服務間的溝通契約，並利用 `sdk-service-*` 模組自動生成客戶端，降低整合成本。
- **關注點分離 (Separation of Concerns)**:
    - `sdk-common`: 封裝所有橫切關注點 (cross-cutting concerns)，如日誌、監控、追蹤、資料庫存取等，以 Spring Boot Starter 的形式提供給業務服務使用。
    - `service`: 專注於實現業務邏輯，並遵循 `controller/service/domain/infra` 的四層架構。
- **雲原生與自動化**:
    - **容器化**: 所有服務都被設計為在 Docker 容器中運行。
    - **Kubernetes 優先**: 提供完整的 Kubernetes manifests (`k8s/` 目錄) 進行部署、擴展和管理。
    - **GitOps**: 透過 GitHub Actions (CI) 和 ArgoCD (CD) 實現從程式碼提交到部署的完整自動化流程。
- **可觀測性 (Observability)**: 內建 Prometheus 和 Grafana 的監控堆疊，並透過 `sdk-core-metrics` 和 `sdk-core-trace` 模組在應用層級提供豐富的監控指標和分散式追蹤。

## 如何建置與運行

### 本地開發環境 (Kubernetes)

專案的設計目標是直接在 Kubernetes 環境中運行。`readme.md` 提供了極其詳盡的步驟來建立一個本地的 `kind` 叢集。

**一鍵啟動腳本**:
在完成 `readme.md` 中的「初始建置」後，可以使用以下腳本快速啟動整個本地環境：

```bash
# 啟動所有基礎設施 (MySQL, Redis, Kafka) 和 K8s 資源
bash ./script/cluster-up.sh
```

目前僅保留 `service-exchange-gateway` 作為交易域的運行模組；`service-exchange-auth`、`service-exchange-matching` 等子服務已暫時移除，待業務重新規劃後再回補。

### 核心指令

- **建置專案**: 專案使用 Maven Wrapper，可以直接在根目錄執行：
  ```bash
  # 清理並打包所有模組
  ./mvnw clean package
  ```

- **運行單一服務 (不建議)**: 雖然可以獨立運行，但服務間有依賴，建議使用 `cluster-up.sh` 進行整合測試。
  ```bash
  # 範例：運行 service-exchange-gateway
  java -jar service/exchange/exchange-gateway/target/service-exchange-gateway-*.jar
  ```

- **壓力測試**: 專案內建了 K6 壓力測試腳本。
  ```bash
  k6 run ./script/k6.js
  ```

## 開發慣例

- **模組化**: 新增共用功能時，應優先考慮在 `sdk-common` 中建立新的模組。
- **分支策略**: (推斷) 可能是基於 GitFlow 或 GitHub Flow，PR 會觸發 CI 流程。
- **測試**: `pom.xml` 中配置了 `maven-surefire-plugin` (單元測試) 和 `maven-failsafe-plugin` (整合測試)，表明專案重視自動化測試。

## 下一步行動

我已準備好接收你的指令。我的短期記憶已經加載了這個項目的上下文，我的長期目標是幫助你讓這個項目變得更好。

讓我們開始吧！
