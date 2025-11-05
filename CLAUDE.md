# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a microservices-based exchange/trading platform built with Java 21, Spring Boot 3.4, and deployed on Kubernetes. The project demonstrates local K8s development with GitOps CI/CD using GitHub Actions and ArgoCD.

**Tech Stack**: Java 21, Spring Boot 3.4.1, Spring Cloud 2024.0.0, Spring Cloud Alibaba (Nacos), MyBatis 3.0.4, Redis (Redisson), Kafka, MySQL, Kubernetes

## Build Commands

### Maven Build
```bash
# Full build (uses Maven Daemon for speed)
mvnd -B -T 1C clean package

# Build specific service
mvnd -pl service/exchange/exchange-auth -am package

# Skip tests
mvnd -DskipTests package

# Run tests only
mvnd test

# Run integration tests
mvnd verify
```

**Note**: The project uses `mvnd` (Maven Daemon) for faster builds. CPU core-based thread allocation with `-T 1C`.

### Docker Build
```bash
# Single service
docker build -t exchange-auth:latest service/exchange/exchange-auth

# Multi-architecture (CI approach)
docker buildx build --platform linux/amd64,linux/arm64 \
  -t vincentf13/exchange-auth:${SHA} \
  --push service/exchange/exchange-auth
```

### Kubernetes Deployment
```bash
# Full stack (infrastructure + services + monitoring)
./script/cluster-up.sh

# Infrastructure only
./script/cluster-up.sh --only-mysql
./script/cluster-up.sh --only-redis
./script/cluster-up.sh --only-kafka
./script/cluster-up.sh --only-nacos

# Services only
./script/cluster-up.sh --only-k8s

# Monitoring stack only (Prometheus + Grafana)
./script/cluster-up.sh --only-prometheus
```

## Architecture

### Module Organization

```
open.vincentf13/
├── sdk/                          # 15 reusable SDK modules
│   ├── sdk-core                  # Foundation (exceptions, ID generation, utilities)
│   ├── sdk-spring-mvc            # MVC auto-config (global exception, CORS, i18n)
│   ├── sdk-auth*                 # Authentication series (JWT, Session, Auth Server)
│   ├── sdk-infra-*               # Infrastructure (MySQL, Redis, Kafka)
│   ├── sdk-spring-cloud-*        # Microservices (Gateway, Feign, Nacos)
│   └── sdk-library-resilience4j  # Circuit breaker, retry, rate limiter
├── sdk-contract/exchange-sdk/    # OpenAPI contracts + generated Feign clients
│   ├── exchange-auth-sdk         # Auth service API + client
│   └── exchange-user-sdk         # User service API + client
├── service/                      # Business services
│   └── exchange/
│       ├── exchange-gateway      # API Gateway (routing, JWT validation)
│       ├── exchange-auth         # Auth service (login, credentials, session)
│       └── exchange-user         # User service (registration, KYC, profile)
├── k8s/                          # Kubernetes manifests
└── design/                       # Architecture documentation
    └── exchange/DDL.sql          # Complete database schema
```

### Service Architecture (DDD Layered)

Every service follows this structure:

```
service/exchange/exchange-auth/
├── domain/
│   ├── model/           # Aggregates, entities, value objects, enums
│   └── service/         # Domain services (business logic)
├── infra/
│   └── persistence/
│       ├── mapper/      # MyBatis XML mappers
│       ├── po/          # Persistence Objects (database table mappings)
│       └── repository/  # Repository pattern (Domain ↔ PO conversion via MapStruct)
├── service/             # Application services (@Transactional boundaries)
└── controller/          # REST controllers (implement SDK contract interfaces)
```

**Key Patterns**:
- Domain models are separate from database entities (PO)
- MapStruct handles DTO ↔ Entity ↔ PO conversions
- Repository abstracts persistence logic
- Controllers implement generated interfaces from `sdk-contract`

### Exception Hierarchy

```
CommonException (interface with meta)
├── BaseRuntimeException
│   ├── OpenApiException        # Controller layer (400/401/403/404)
│   └── OpenInfraException      # Infrastructure (DB, cache, MQ)
└── BaseCheckedException
    └── OpenServiceException    # Service layer (business exceptions)
```

All exceptions include `ErrorCode` enum and `meta` Map with `traceId`, `requestId`, `timestamp`. Global exception handling via `AopRestException` in `sdk-spring-mvc`.

### Authentication & Authorization

**Annotation-Driven**:
- `@PublicAPI` - No authentication required (login, registration, health checks)
- `@PrivateAPI` - Internal only, blocked at gateway
- `@Jwt` - Requires JWT token
- `@Session` - Requires session (Redis-backed)
- `@ApiKey` - Requires API key

**Flow**: Gateway validates JWT → Services enforce annotation-based authorization

**Credential Types**:
- PASSWORD (BCrypt/Argon2)
- API_KEY (UUID-based)
- TOTP (planned)

### Compensation Pattern (User Registration)

User registration uses prepare/commit/retry:
1. `exchange-user` calls `exchange-auth` to create credentials
2. On failure, writes to `auth_credentials_pending` table
3. Scheduled job retries from pending table
4. Success removes pending record

**Related Tables**:
- `users` (main table)
- `auth_credentials` (successful credentials)
- `auth_credentials_pending` (retry queue)

### ID Generation

Uses Snowflake algorithm (Yitter) for all primary keys:
- 64-bit distributed IDs
- Worker ID configurable via `ID_GENERATOR_WORKER_ID` env var (default: 1)
- Auto-configured in `sdk-core`: `IdGeneratorAutoConfiguration`
- Usage: `YitIdHelper.nextId()`

## Configuration

### Layered Configuration

1. SDK defaults (lowest priority) - `sdk-*-defaults.yaml`
2. Nacos remote config
3. Local `application.yaml`
4. Environment variables (highest priority)

### Service Configuration Pattern

```yaml
spring:
  application:
    name: exchange-auth
  cloud:
    nacos:
      config:
        import-check:
          enabled: false

mybatis:
  type-aliases-package: open.vincentf13.exchange.auth.infra.persistence.po

open:
  vincentf13:
    mybatis:
      mapper-base-packages: open.vincentf13.exchange.auth.infra.persistence.mapper
```

Custom properties use `open.vincentf13.*` prefix with `@ConfigurationProperties` binding.

## Database

**Connection (K8s)**:
- Host: `infra-mysql-0.infra-mysql-headless.default.svc.cluster.local:3306`
- Database: `exchange`
- User/Password: `root/root`

**Key Tables**:
- `users` - User master (Snowflake IDs)
- `auth_credentials` - Credential storage
- `auth_credentials_pending` - Retry queue for compensation
- `auth_login_audit` - Login audit trail
- `orders`, `trade_tickers`, `ledger_entries`, `positions` (planned)

**Schema**: See `design/exchange/DDL.sql` for complete DDL.

## CI/CD

### GitHub Actions Workflow

Located in `.github/workflows/ci-cd.yaml`:

**Change Detection**: Only builds modified services using `tj-actions/changed-files`
- Scans `SERVICE_DIRS` and `SERVICE_GLOBS` (currently: `service/service-test`, `service/service-template`)
- Set `FORCE_BUILD_ALL=true` to build all services

**Required GitHub Secrets**:
- `DOCKERHUB_USERNAME` - Docker Hub account
- `DOCKERHUB_TOKEN` - Docker Hub access token
- `GH_PAT` - GitHub Personal Access Token (repo scope) for updating GitOps repo

**Flow**:
1. Detect changed services
2. Parallel builds with `mvnd -B -T 1C -DskipTests clean package`
3. Docker buildx multi-arch (linux/amd64, linux/arm64)
4. Push to Docker Hub with tags: `${GITHUB_SHA::7}` and `latest`
5. Update GitOps repo with new image tags

### ArgoCD Setup

```bash
# Login
argocd login argocd-server.argocd.svc.cluster.local:443 \
  --username admin \
  --password "$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d)" \
  --insecure --grpc-web

# Create GitOps app
argocd app create gitops \
  --repo https://github.com/<GITHUB_ACCOUNT>/GitOps.git \
  --path k8s \
  --dest-server https://kubernetes.default.svc \
  --dest-namespace default \
  --sync-policy automated --grpc-web

# Verify
argocd app list --grpc-web
```

**GitOps Repo**: Separate repository with same structure as `k8s/` directory. ArgoCD monitors for changes and auto-deploys.

## Development

### Adding a New Service

1. Copy `service-template/` as baseline
2. Define OpenAPI contract in `sdk-contract/`
3. Implement DDD layers: domain → infra → service → controller
4. Add K8s manifests to `k8s/service-{name}/` (deployment, service, hpa)
5. Update `.github/workflows/ci-cd.yaml` with new service path in `SERVICE_DIRS` and `SERVICE_GLOBS`

### Adding a New SDK Module

1. Create module in `sdk/`
2. Define `sdk-{name}-defaults.yaml` for default configuration
3. Create `AutoConfiguration` class with `@Configuration` and `@EnableConfigurationProperties`
4. Register in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
5. Add to root `pom.xml` `<modules>` and `<dependencyManagement>`

### Annotation Processing Order

**Critical**: Lombok must process before MapStruct. Maven compiler plugin is configured:
```xml
<annotationProcessorPaths>
  <path><!-- Lombok --></path>
  <path><!-- lombok-mapstruct-binding --></path>
  <path><!-- MapStruct --></path>
</annotationProcessorPaths>
```

MapStruct args: `-Amapstruct.defaultComponentModel=spring` (generates Spring beans)

## Monitoring & Observability

### Prometheus & Grafana

- **Prometheus**: `http://prometheus.monitoring.svc.cluster.local:9090`
- **Alertmanager**: `http://alertmanager.monitoring.svc.cluster.local:9093`
- **Grafana**: `http://grafana.monitoring.svc.cluster.local:3000` (admin/admin123)

Services expose metrics at `/actuator/prometheus`. Prometheus scrapes every 15s with annotations.

### Logging

- **Framework**: Log4j2 (not Logback)
- **Format**: JSON structured logs
- **MDC Context**: `traceId`, `requestId`, `userId` propagated across services via Feign interceptors
- **TraceId Generation**: At gateway entry point, propagated downstream

### Alerting

Rules defined in `k8s/infra-prometheus/prometheus-rules.yaml`. Configure receivers (Slack webhook, etc.) in `k8s/infra-prometheus/alertmanager-configmap.yaml`.

## Kubernetes Resources

**Access via Telepresence** or cluster DNS (`.svc.cluster.local`):
- Ingress: `http://ingress-nginx-controller.ingress-nginx.svc.cluster.local`
- Nacos: `http://infra-nacos.default.svc.cluster.local:8848`
- Redis: `infra-redis.default.svc.cluster.local:6379`
- Kafka: `infra-kafka.default.svc.cluster.local:9092`
- Redpanda Console: `http://redpanda-console.default.svc.cluster.local:8080`

### HPA (Horizontal Pod Autoscaler)

Services use CPU-based autoscaling. Requires metrics-server:
```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# Patch for local clusters (insecure TLS)
kubectl -n kube-system patch deploy metrics-server --type='json' -p='[
  {"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"},
  {"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-preferred-address-types=InternalIP,Hostname,ExternalIP"}
]'
```

Verify: `kubectl get hpa` should show CPU percentages.

## Naming Conventions

### Package Structure
```
open.vincentf13.{domain}.{service}.{layer}.{package}

Examples:
- open.vincentf13.exchange.auth.domain.model
- open.vincentf13.exchange.auth.infra.persistence.repository
- open.vincentf13.exchange.auth.service
- open.vincentf13.exchange.auth.controller
```

### Class Naming
- Controllers: `*Controller` (e.g., `AuthController`)
- Services: `*Service` (e.g., `AuthCredentialService`)
- Repositories: `*Repository` (e.g., `AuthCredentialRepository`)
- Domain Services: No suffix (e.g., `LoginDomain`)
- Domain Models: No suffix (e.g., `User`, `AuthCredential`)
- POs: `*PO` (e.g., `AuthCredentialPO`)
- DTOs: `*Request`, `*Response`, `*DTO` (e.g., `LoginRequest`)
- Mappers (MyBatis): `*Mapper` (e.g., `AuthCredentialMapper`)
- Mappers (MapStruct): `*Converter` (e.g., `AuthCredentialConverter`)

### Database Naming
- Tables: snake_case (e.g., `auth_credentials`)
- Columns: snake_case (e.g., `user_id`, `created_at`)
- Indexes: `idx_{table}_{columns}` (e.g., `idx_users_email`)
- Foreign Keys: `fk_{table}_{ref_table}` (e.g., `fk_orders_users`)

## Testing

```bash
# Run unit tests
mvnd test

# Run integration tests
mvnd verify

# Test specific service
mvnd -pl service/exchange/exchange-auth test

# K6 load testing
k6 run ./integration/simulators/src/main/resources/k6/k6.js
```

**Test Infrastructure**: Uses Testcontainers for MySQL, Redis, Kafka integration testing.

## Troubleshooting

### Service Not Registering to Nacos
```bash
# Check Nacos logs
kubectl logs -f deployment/infra-nacos

# Verify service discovery
curl http://infra-nacos.default.svc.cluster.local:8848/nacos/v1/ns/instance/list?serviceName=exchange-auth
```

### Feign Client Failures
- Check Nacos service discovery
- Verify Circuit Breaker status: `/actuator/health`
- Check target service health: `/actuator/health`
- Verify `traceId` propagation in logs

### Database Connection Issues
```bash
# Verify MySQL pods
kubectl get pods | grep mysql

# Test connection
kubectl exec -it infra-mysql-0 -- mysql -uroot -proot
```

### Redis Connection Issues
```bash
# Verify Redis cluster
kubectl exec -it infra-redis-0 -- redis-cli cluster info

# Test connection
kubectl exec -it infra-redis-0 -- redis-cli ping
```

## Important Notes

- **Java 21 Required**: Uses Virtual Threads (Project Loom), Records, Pattern Matching
- **Maven Daemon**: Prefer `mvnd` over `mvn` for faster builds
- **Contract-First**: Controllers implement generated interfaces from `sdk-contract`
- **No JWT/Session in Logs**: Never log credentials, tokens, or sensitive data
- **Snowflake IDs**: All primary keys use `YitIdHelper.nextId()`, not auto-increment
- **Compensation Pattern**: Use pending tables + scheduled retry for distributed transactions
- **Multi-Arch Builds**: CI builds for both amd64 and arm64 via buildx
- **Cluster DNS**: Access services via `{service}.{namespace}.svc.cluster.local`
