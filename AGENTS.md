# Project Handbook

## Repo Layout
- Root `pom.xml` 聚合 `sdk`、`sdk-contract`、`service`，指令從倉庫根目錄執行。
- 共用元件在 `sdk/`（core、spring-mvc、exchange、spring-security、mysql、redis、kafka、test、observability 等子模組），契約/DTO 在 `sdk-contract/`。
- 業務服務位於 `service/<module>/`，各自有 `pom.xml`、`Dockerfile` 與 resources。
- Kubernetes 清單在 `k8s/`，服務配置於 `k8s/<service>/`，共用路由在 `k8s/ingress.yaml`。
- exchange 設計文件統一放在 `service/exchange/design/`（整體設計、Domain/Controller/Service/DB/Kafka 設計、0.web 等）。

## Build & Run
- 政策：本專案 Agent 作業**不執行 Maven 指令與測試**；若必要僅對特定模組執行 `mvn clean compile`。
- 本地啟動服務：`mvn -pl service/<service> spring-boot:run`（預設 8080，可自行覆寫）。

## General Coding
- Java UTF-8、4-space；PascalCase 類名、camelCase 成員、UPPER_SNAKE 常數；偏好建構子注入，移除暫時性 `System.out`。
- API 路徑統一 `/api/...`；Endpoint 表授權欄內部介面填 `private`，服務調用只寫呼叫目標，補償欄標註重試/補償位置。
- Domain/DTO/PO 轉換一律用 `open.vincentf13.sdk.core.object.mapper.OpenObjectMapper`，Domain 層不引入 infra 依賴或框架註解。
- Enum/常數：REST DTO 枚舉放 `sdk-contract/.../enums` 並直接引用；PO 用 enum 型別；領域常數封裝於 Domain/Value Object；Kafka Topic 定義用 Enum 並提供可在註解引用的 `Names`/常數。
- 多行註解格式：開頭 `/**` 換行後每行兩空格縮排，結尾 ` */` 對齊；避免使用星號前綴。
- 批次 insert/update 統一使用 `open.vincentf13.sdk.infra.mysql.OpenMybatisBatchExecutor`（保留每 1,000 筆 flush/clear）；採 Intent-Centric 風格，入參直接用、避免多餘 null 判斷或包裝。

## Exchange Rules
- 任何新功能/重構先對照 `service/exchange/design/`；衝突時以設計文件為準。
- Controller 直接實作對應 `sdk-contract` OpenAPI 介面；驗證/權限註解放介面上，Controller 不重複綁定或 Bean Validation。
- Application 入口應先 `OpenValidator.validateOrThrow(...)` 等 Bean Validation；如無特殊需求直接使用 REST DTO，避免新增 Command 類。
- 嚴格 CQRS：查詢在 `*QueryService`，寫入/更新在 `*CommandService`，Controller 分別注入；DDD 下 Domain 維護工廠與領域常數，Application 只協調用例。
- Kafka 消費：僅成功後手動 ack；異常交由重送或 DLQ，禁止 finally 強制 ack。
- 呼叫端取得實體後以版本欄位做樂觀鎖更新，確保冪等與競態保護。

## Deployment
- 套用順序：deployment → service → HPA → ingress（例：`k8s/service-template/deployment.yaml` → service → HPA → `k8s/ingress.yaml`）。
- 確保 `k8s/<service>/deployment.yaml` 的 image tag 與發佈一致；名稱/port 變更時同步更新 HPA/ingress。
- 線上滾動更新：`kubectl set image deploy/<service> <container>=<image>:<tag>`，並以 `kubectl rollout status` 監看。

## Agent Notes
- 不撰寫或更新任何測試碼；不跑 Maven；回覆最終總結使用中文。
- exchange 需求以 `service/exchange/design/` 為最高優先指引。
