# Repository Guidelines

## Project Structure & Module Organization
Application code now lives per service under `services/<service>/src` (for example `services/demo/src/main/java/com/example/demo`). Each service keeps its own `pom.xml`, `Dockerfile`, and resources alongside the code. Shared configuration, templates, and static assets stay under each service’s `src/main/resources`, with tests mirroring the package tree in `src/test/java`. Kubernetes manifests reside in `k8s/`; per-service resources live in subdirectories such as `k8s/demo/deployment.yaml` or `k8s/service-template/deployment.yaml`, while shared ingress remains at `k8s/ingress.yaml`. The Docker build context for each service is its own directory (e.g. `services/demo` or `services/service-template`).

## Build, Test, and Development Commands
Run `./mvnw clean verify` before pushing; it compiles, runs unit tests, and leaves artifacts in `target/`. Use `./mvnw spring-boot:run` for a local dev server on port 8080. `./mvnw test` runs only the JUnit suite. Build container images via `docker build -t demo:latest .`, then load into kind with `kind load docker-image demo:latest --name mycluster`.

## Coding Style & Naming Conventions
Follow standard Java style with 4-space indentation and UTF-8 sources. Use PascalCase for classes, camelCase for methods/fields, and upper snake case for constants. Name controllers, services, and repositories after their responsibility (e.g., `UserController`). Favor constructor injection, keep configuration in distinct classes, and remove debug prints like `System.out.printf` before committing.

## Testing Guidelines
Place unit and slice tests in `src/test/java`, mirroring package names and ending classes with `*Tests`. Use `@SpringBootTest` only when full context wiring is required; otherwise prefer lighter slices or mocks. Cover success and failure paths, and ensure the suite remains green under `./mvnw verify`. Document any intentionally skipped tests in the PR description.

## Commit & Pull Request Guidelines
Write concise, present-tense commit messages (e.g., `fix ingress host mapping`). Chinese summaries are fine; keep the first line under 50 characters and move detail to the body if needed. For PRs, link related issues, share repro steps for fixes, and include screenshots or curl samples when changing HTTP behavior. Confirm `./mvnw verify` and `kubectl apply -f k8s/*.yaml` succeed before requesting review.

## Deployment Notes
Apply manifests in order: deployment, service, HPA, then ingress (e.g. `kubectl apply -f k8s/demo/deployment.yaml`, `k8s/demo/service.yaml`, `k8s/demo/hpa.yaml`, followed by the service-template equivalents, and finally `k8s/ingress.yaml`). Keep image tags in the per-service deployment manifests (`k8s/demo/deployment.yaml`, `k8s/service-template/deployment.yaml`, etc.) aligned with published images; update the corresponding service/HPA manifests and ingress if ports or names change. For registry pushes, update each deployment (`kubectl set image deploy/demo ...`, `kubectl set image deploy/service-template ...`) and watch rollout status.
