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
- MyBatis Mapper 需優先使用 `insertSelective` / `updateSelective` / `findBy(PO)` / `batchInsert` / `batchUpdate` 模板，盡量共用這些 conventions 避免重工。
- 開發 exchange 模組時，目錄／類別分層、命名與責任切分必須遵照 `design/exchange/整體設計.md` 的規範（例如 domain/infra/service/controller、聚合邊界等），任何新服務或重構都需先比對設計文件再實作。
- 文件與程式的 API 命名需一致：即使為內部呼叫，統一以 `/api/...` 為前綴；Endpoint 表的 `授權` 欄內部介面填 `private`，`服務調用` 僅記錄「呼叫了哪個服務與接口」，不要描述回傳內容；`補償機制` 用來標註調用失敗時會在哪段程式碼重試或補償。

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
