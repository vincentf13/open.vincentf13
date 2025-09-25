# Repository Guidelines

## Project Structure & Module Organization
Application code stays in `src/main/java/com/example/demo` with `DemoApplication` as the entry point. Shared configuration, templates, and static assets go in `src/main/resources` (`templates/` for Thymeleaf or views, `static/` for public files). Tests mirror the package tree under `src/test/java`. Deployment manifests live in `k8s/`, with a Helm chart at `k8s/helm/` and raw manifests beside it; Docker build context is rooted at the repo root with `dockerfile`.

## Build, Test, and Development Commands
Run `./mvnw clean verify` before pushing; it compiles, runs unit tests, and prepares the Spring Boot jar in `target/`. Use `./mvnw spring-boot:run` for a local dev server on port 8080. `./mvnw test` executes the JUnit test suite only. Build a container image with `docker build -t demo:latest .`. For local kind clusters, follow `readme.md` after building the image: `kind load docker-image demo:latest --name mycluster`.

## Coding Style & Naming Conventions
Follow standard Java style with 4-space indentation and UTF-8 source files. Use PascalCase for classes, camelCase for methods and variables, and uppercase snake case for constants. Keep controllers/services named after their responsibility (e.g., `UserController`). Favor constructor injection and annotate Spring components explicitly. Remove debugging prints such as `System.out.printf` before committing.

## Testing Guidelines
Write JUnit tests under `src/test/java` with class names ending in `*Tests`. Prefer `@SpringBootTest` only when the full context is required; otherwise, mock dependencies for faster runs. Ensure new features add assertions covering success and failure paths. Aim to keep the suite green in `./mvnw verify`, and document any intentionally skipped tests in the PR description.

## Commit & Pull Request Guidelines
Commit messages should be concise, present tense summaries (e.g., `fix ingress host mapping`). When working in Chinese, keep the first line under 50 characters and leave details in the body if needed. For PRs, link related issues, include reproduction steps when fixing bugs, and attach screenshots or curl samples when touching HTTP endpoints. Confirm `./mvnw verify` and relevant deployment commands succeed before requesting review.

## Deployment Notes
Keep the `dockerfile` and Helm values in sync with application ports; update `k8s/hpa.yaml` and `k8s/ingress.yaml` when ports change. After publishing a new image tag, update `values.yaml` and run `helm upgrade --install demo k8s/helm -f values.yaml`. For kind, you can skip pushing to a registry by reusing `kind load docker-image` and issuing `kubectl rollout restart deploy/demo`.
