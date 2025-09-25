# Repository Guidelines

## Project Structure & Module Organization
Application code lives in `src/main/java/com/example/demo`, with `DemoApplication` as the Spring Boot entry point. Shared configuration, templates, and static assets stay under `src/main/resources` (`templates/` for views, `static/` for public files). Tests mirror the package tree in `src/test/java`. Kubernetes manifests reside in `k8s/`, where `deployment.yaml`, `service.yaml`, `hpa.yaml`, and `ingress.yaml` define the live stack. The Docker build context is the repo root with `dockerfile`.

## Build, Test, and Development Commands
Run `./mvnw clean verify` before pushing; it compiles, runs unit tests, and leaves artifacts in `target/`. Use `./mvnw spring-boot:run` for a local dev server on port 8080. `./mvnw test` runs only the JUnit suite. Build container images via `docker build -t demo:latest .`, then load into kind with `kind load docker-image demo:latest --name mycluster`.

## Coding Style & Naming Conventions
Follow standard Java style with 4-space indentation and UTF-8 sources. Use PascalCase for classes, camelCase for methods/fields, and upper snake case for constants. Name controllers, services, and repositories after their responsibility (e.g., `UserController`). Favor constructor injection, keep configuration in distinct classes, and remove debug prints like `System.out.printf` before committing.

## Testing Guidelines
Place unit and slice tests in `src/test/java`, mirroring package names and ending classes with `*Tests`. Use `@SpringBootTest` only when full context wiring is required; otherwise prefer lighter slices or mocks. Cover success and failure paths, and ensure the suite remains green under `./mvnw verify`. Document any intentionally skipped tests in the PR description.

## Commit & Pull Request Guidelines
Write concise, present-tense commit messages (e.g., `fix ingress host mapping`). Chinese summaries are fine; keep the first line under 50 characters and move detail to the body if needed. For PRs, link related issues, share repro steps for fixes, and include screenshots or curl samples when changing HTTP behavior. Confirm `./mvnw verify` and `kubectl apply -f k8s/*.yaml` succeed before requesting review.

## Deployment Notes
Apply manifests in order: deployment, service, HPA, then ingress (`kubectl apply -f k8s/deployment.yaml` etc.). Keep `image` tags in `k8s/deployment.yaml` aligned with published images; update `hpa.yaml` and `ingress.yaml` if ports or names change. For registry pushes, update the deployment with `kubectl set image deploy/demo demo=<registry>/demo:<tag>` and watch rollout status.
