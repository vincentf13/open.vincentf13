# Project Handbook

## Current Module Layout
- Root aggregator `pom.xml` declares `common-sdk` and `services`; run module-specific commands from the repo root.
- Shared code lives under `common-sdk`, which現在分成 `core`, `spring-mvc`, `exchange`, `spring-security`, `mysql`, `redis`, `kafka`, `test`, `observability` 等子模組。
- Service implementations reside in `services/<service>/src`, each with its own `pom.xml`, `Dockerfile`, and resources (`src/main/resources`, `src/test/java`).
- Kubernetes assets live in `k8s/`; per-service manifests sit under `k8s/<service>/`, while cross-cutting resources stay at the top level (e.g., `k8s/ingress.yaml`).

## Build & Test Commands
- 使用系統 Maven (`mvn`) 進行構建與測試。
- Full pipeline: `mvn clean verify` 會編譯、執行單元測試並在 `target/` 下產出制品。
- Targeted build: `mvn -pl <module> -am verify`（例如：`mvn -pl common-sdk/exchange/matching/rest-api -am verify`）。
- Local service run: `mvn -pl services/<service> spring-boot:run`（預設監聽 8080，可在各服務自行覆寫）。

## Coding Style
- Java sources are UTF-8 with 4-space indentation; use PascalCase for classes, camelCase for members, and UPPER_SNAKE_CASE for constants.
- Prefer constructor injection, isolate config in dedicated classes, and remove transient debug output (`System.out.printf`, etc.) before committing.
- Name components by responsibility (e.g., `UserController`, `OrderService`).
- MyBatis Mapper 需優先使用 `insertSelective` / `updateSelective` / `findBy(PO)` / `updateSelective`（對應 updateBy 用途）/ `upsertSelective` 模板，盡量共用這些 conventions 避免重工；除非必要不自訂 resultMap，仰賴自動轉換與全域 TypeHandler。
- 針對查詢語句，只要情境允許，一律實作為單一 `findBy(PO)` 入口，由呼叫端透過 PO 組條件，避免額外客製查詢方法；Repository 標準介面：`findOne`（組 PO 呼叫 `findBy(PO)`，多筆報錯）、`findBy`（組 PO 呼叫 `findBy(PO)`）、`insert`（Domain→PO→`insertSelective`）、`upsert`（Domain→PO→`upsertSelective`）、`updateBy`（Domain 作為 set 值，額外參數作 WHERE，`updateSelective` 判空組條件）。特殊方法需先討論。
- 開發 exchange 模組時，目錄／類別分層、命名與責任切分必須遵照 `design/exchange/整體設計.md` 及 `Domain設計.md` / `Controller 設計.md` / `Service 設計.md` / `DB設計.md` / `Kafka設計.md` 的規範（如 domain/infra/service/controller、聚合邊界等），任何新服務或重構都需先比對設計文件再實作。
- 文件與程式的 API 命名需一致：即使為內部呼叫，統一以 `/api/...` 為前綴；Endpoint 表的 `授權` 欄內部介面填 `private`，`服務調用` 僅記錄「呼叫了哪個服務與接口」，不要描述回傳內容；`補償機制` 用來標註調用失敗時會在哪段程式碼重試或補償。
- `sdk-contract` 下的 REST API 介面僅定義方法與 DTO，不得標註 Spring MVC 的 `@RequestMapping`/`@GetMapping` 等類別級註解；實際路由與權限註解一律放在實作該介面的 Controller 上。

## Testing Expectations
- Mirror packages under `src/test/java`; suffix test classes with `*Tests`.
- Reserve `@SpringBootTest` for full-context cases—favor slices, mocks, or plain unit tests otherwise.
- Cover success and failure paths; keep the suite green under `mvn verify`. Document any intentionally skipped scenarios in PR notes.
- 需要改用服務自行配置的資料庫/Redis/Kafka 時，可透過 `-Dopen.vincentf13.sdk.core.test.testcontainer.enabled=false`（或環境變數 `OPEN_VINCENTF13_SDK_CORE_TEST_TESTCONTAINER_ENABLED=false`）停用共用測試容器；也可使用 `open.vincentf13.sdk.core.test.testcontainer.{mysql|redis|kafka}.enabled` 的旗標細部調整。

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
- Kafka 消費邏輯（例如 PositionReserveRequestListener）僅在成功處理後手動 ack，若發生異常需讓 Kafka 重送或進 DLQ，禁止在 finally 中強制 ack。
- 所有 Domain ↔ DTO ↔ PO 之間的物件轉換一律透過 `open.vincentf13.sdk.core.OpenMapstruct`，禁止手寫 builder/constructor 直接複製欄位（包含行情查詢、標記價等服務）；Domain 層保持純粹，不引入基礎設施依賴或框架註解。
- 任何帳戶、倉位、風險快照（`account` / `positions` / `risk_snapshot`）的狀態變更都必須包在 `@Transactional` 範圍內，確保跨 repository 更新的一致性。


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
- 所有 DDL 的審計欄位 `created_by`/`updated_by` 由資料庫統一處理：INSERT 時同時寫入 created_by 與 updated_by，UPDATE 時更新 updated_by；應用程式層禁止手動賦值這兩欄。`created_at`/`updated_at` 同樣由 DB 預設或 trigger 管控，Domain/PO/Mapper 不得手動設值。
- 全域 MyBatis Enum TypeHandler 已將 Enum `name()` 寫入資料庫並自動還原，禁止在程式中手動轉字串或自建 enum↔字串映射。
