# Project Handbook

## Current Module Layout
- Root aggregator `pom.xml` declares `common-sdk` and `services`; run module-specific commands from the repo root.
- Shared code lives under `common-sdk`, which現在分成 `core`, `spring-mvc`, `exchange`, `spring-security`, `mysql`, `redis`, `kafka`, `test`, `observability` 等子模組。
- Service implementations reside in `services/<service>/src`, each with its own `pom.xml`, `Dockerfile`, and resources (`src/main/resources`, `src/test/java`).
- Kubernetes assets live in `k8s/`; per-service manifests sit under `k8s/<service>/`, while cross-cutting resources stay at the top level (e.g., `k8s/ingress.yaml`).

## Build Commands
- **(Overridden)** 本專案採用 Agent 自動化開發流程，**不再執行 Maven 構建或測試指令**。
- 若需驗證編譯，可針對特定模組執行 `mvn clean compile`。
- Local service run: `mvn -pl services/<service> spring-boot:run`（預設監聽 8080，可在各服務自行覆寫）。

## Coding Style
- Java sources are UTF-8 with 4-space indentation; use PascalCase for classes, camelCase for members, and UPPER_SNAKE_CASE for constants.
- Prefer constructor injection, isolate config in dedicated classes, and remove transient debug output (`System.out.printf`, etc.) before committing.
- Name components by responsibility (e.g., `UserController`, `OrderService`).
- 開發 exchange 模組時，目錄／類別分層、命名與責任切分、規則必須遵照 `design/exchange/整體設計.md` 及 `Domain設計.md` / `Controller_Design.md` / `Service 設計.md` / `DB設計.md` / `Kafka設計.md` 的規範（如 domain/infra/service/controller、聚合邊界等），任何新服務或重構都需先比對設計文件再實作。
- 文件與程式的 API 命名需一致：即使為內部呼叫，統一以 `/api/...` 為前綴；Endpoint 表的 `授權` 欄內部介面填 `private`，`服務調用` 僅記錄「呼叫了哪個服務與接口」，不要描述回傳內容；`補償機制` 用來標註調用失敗時會在哪段程式碼重試或補償。

## Testing Expectations
- **(Overridden)** 根據專案新政策，**不再編寫或更新任何測試程式碼**。
- 既有的測試程式碼可被忽略，且不需要維持 `mvn verify` 通過。

## Git & Review Workflow
- Commit messages: concise, present tense (`fix ingress host mapping`); first line < 50 characters, further detail in the body if needed. Chinese summaries are acceptable.
- Before opening a PR, confirm `mvn clean verify` and `kubectl apply -f k8s/<service>/*.yaml` succeed.
- Link related issues, document reproduction steps for fixes, and attach screenshots or curl logs when changing HTTP behavior.

## Deployment Notes
- Apply manifests in order: deployment → service → HPA → ingress (e.g., `kubectl apply -f k8s/service-test/deployment.yaml`, then service, HPA, and finally `k8s/ingress.yaml`).
- Keep image tags in `k8s/<service>/deployment.yaml` aligned with published artifacts; update matching service/HPA manifests and ingress when ports or names change.
- For live rollouts, `kubectl set image deploy/<service> <container>=<image>:<tag>` and monitor with `kubectl rollout status deploy/<service>`.

## Agent Overrides
- 以後處理本專案時，不需要執行任何 Maven 指令。
- 以後不再編寫或更新測試程式碼。
- 回覆最終總結時請一律使用中文。
- exchange 相關需求一律以 `design/exchange/` 目錄內設計文件為主要參考與判準，若與其他描述衝突，以該目錄為優先。
- Kafka 消費邏輯（例如 PositionReserveRequestListener）僅在成功處理後手動 ack，若發生異常需讓 Kafka 重送或進 DLQ，禁止在 finally 中強制 ack。
- 所有 Domain ↔ DTO ↔ PO 之間的物件轉換一律透過 `open.vincentf13.sdk.core.object.mapper.OpenObjectMapper`，禁止手寫 builder/constructor 直接複製欄位（包含行情查詢、標記價等服務）；Domain 層保持純粹，不引入基礎設施依賴或框架註解。


呼叫端要在拿到實體後用版本欄位做樂觀鎖更新，確保快照與帳戶建立流程具備冪等與競態保護。
- Controller 必須直接實作 `sdk-contract` OpenAPI 介面（exchange-{service}-rest-api），權限/驗證註解集中在介面上；Controller 端不得重複宣告綁定或 Bean Validation 註解；所有 enum 需隨 OpenAPI 介面定義在同一 rest-api 模組，方便 client 共用。
- Application 層處理入參時必須使用 `open.vincentf13.sdk.core.OpenValidator.validateOrThrow(...)` 或同等 Bean Validation 方式於方法一開始驗證 DTO，禁止手寫重複的 null/空字串檢查；必要的商業規則再額外檢查。
- 所有 REST DTO 專用的枚舉（例如帳戶型別）都要放在 `sdk-contract/.../enums` 內，server code 直接引用，不得在 domain/service 再定義重複的 enum；PO 欄位直接使用 enum 型別，依全域 MyBatis Enum TypeHandler 持久化為字串，不得手動轉字串。
- 與 Domain 行為緊密相關的常數（如 OwnerType、EntryType）必須封裝在對應 Domain Model/Value Object 內統一維護，Service/Controller 不得自建字串常量。
- 所有服務模組都必須依設計文件實作 CQRS：查詢邏輯集中在 `*QueryService`，寫入/更新集中在 `*CommandService`，Controller 需分別注入 Query/Command 服務；新增功能時亦要遵守這個拆分慣例，禁止將查詢與命令混在同一服務類別。
- 嚴格遵守 DDD 原則：Domain Model 統一維護領域常數/工廠，Application Service 僅協調用例；跨用例邏輯需落在 Domain 或專用的 Service 層，不得堆疊在 Controller/Application。
- Application 層若無特殊需求，一律直接使用 REST request DTO 作為輸入參數，不再額外定義 Command 類別；既有 Command 物件逐步移除。
- Enum 新增時若需要存取欄位，優先使用 Lombok（例如 `@RequiredArgsConstructor` + `@Getter`）簡化建構子與 getter，維持一致風格。
- Kafka Topic 定義統一改為 Enum（內含 topic 字串與事件類別），透過 `.getTopic()` append/outbox，避免裸字串重複；新 topic 需遵守此模式。
- 若需在註解中引用 Topic 或 Base Package 這類值，Enum/常數定義時同步提供 `Names` 類或 `public static final` 常數，確保註解可引用編譯期常數並仍維持 Enum 封裝。
- 多行註解統一使用 `/** ... */` 形式，不再使用 `/** ... */`；格式規範：開頭 `/**` 後換行，每行內容前用兩個空格縮排（不使用 `*`），結尾 ` */` 與內容對齊。範例：
  ```java
  /**
    方法說明
    - 重點一
    - 重點二
   */
  public void someMethod() {
  ```
- 若適合，盡量符合 Inline wrapper style / Fluent inline query / Expression-based repository call / Call-site query embedding 等風格，避免在呼叫端額外宣告 wrapper 變數。
- 批次 insert/update 請統一透過 `open.vincentf13.sdk.infra.mysql.OpenMybatisBatchExecutor`（或其提供的工具）完成，避免自行管理 `SqlSession`，並保留每 1,000 筆的 flush/clear 邏輯。
