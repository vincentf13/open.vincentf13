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

## Testing Expectations
- Mirror packages under `src/test/java`; suffix test classes with `*Tests`.
- Reserve `@SpringBootTest` for full-context cases—favor slices, mocks, or plain unit tests otherwise.
- Cover success and failure paths; keep the suite green under `mvn verify`. Document any intentionally skipped scenarios in PR notes.

## Git & Review Workflow
- Commit messages: concise, present tense (`fix ingress host mapping`); first line < 50 characters, further detail in the body if needed. Chinese summaries are acceptable.
- Before opening a PR, confirm `mvn clean verify` and `kubectl apply -f k8s/<service>/*.yaml` succeed.
- Link related issues, document reproduction steps for fixes, and attach screenshots or curl logs when changing HTTP behavior.

## Deployment Notes
- Apply manifests in order: deployment → service → HPA → ingress (e.g., `kubectl apply -f k8s/service-test/deployment.yaml`, then service, HPA, and finally `k8s/ingress.yaml`).
- Keep image tags in `k8s/<service>/deployment.yaml` aligned with published artifacts; update matching service/HPA manifests and ingress when ports or names change.
- For live rollouts, `kubectl set image deploy/<service> <container>=<image>:<tag>` and monitor with `kubectl rollout status deploy/<service>`.
