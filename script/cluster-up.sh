#!/usr/bin/env bash
set -eu
if ! set -o pipefail 2>/dev/null; then
  printf 'Warning: pipefail not supported; continuing without it.\n'
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
K8S_DIR="$REPO_ROOT/k8s"
PROM_DIR="$K8S_DIR/infra-prometheus"
GRAFANA_DIR="$K8S_DIR/infra-grafana"
MYSQL_DIR="$K8S_DIR/infra-mysql"
REDIS_DIR="$K8S_DIR/infra-redis"
KAFKA_DIR="$K8S_DIR/infra-kafka"
NACOS_DIR="$K8S_DIR/infra-nacos"

# Core application manifests applied to the cluster in order.
APPLICATION_MANIFESTS=(
  service-test/deployment.yaml
  service-test/service.yaml
  service-test/hpa.yaml
  service-template/deployment.yaml
  service-template/service.yaml
  service-template/hpa.yaml
  ingress.yaml
)

# Monitoring stack manifests; namespace manifest stays first to ensure resources deploy into it.
PROM_MANIFEST_ORDER=(
  monitoring-namespace.yaml
  prometheus-rbac.yaml
  prometheus-configmap.yaml
  prometheus-rules.yaml
  prometheus-deployment.yaml
  prometheus-service.yaml
  alertmanager-configmap.yaml
  alertmanager-statefulset.yaml
  alertmanager-service.yaml
  prometheus-ingress.yaml
)

GRAFANA_MANIFEST_ORDER=(
  grafana-secret.yaml
  grafana-configmap.yaml
  grafana-pvc.yaml
  grafana-deployment.yaml
  grafana-service.yaml
)

MYSQL_MANIFEST_ORDER=(
  pv.yaml
  pvc.yaml
  configmap.yaml
  secret.yaml
  service.yaml
  statefulset.yaml
)

REDIS_MANIFEST_ORDER=(
  configmap.yaml
  headless-service.yaml
  service.yaml
  statefulset.yaml
)

KAFKA_MANIFEST_ORDER=(
  configmap.yaml
  headless-service.yaml
  service.yaml
  statefulset.yaml
  redpanda-console-configmap.yaml
  redpanda-console-deployment.yaml
  redpanda-console-service.yaml
)

NACOS_MANIFEST_ORDER=(
  pv.yaml
  pvc.yaml
  configmap.yaml
  service.yaml
  statefulset.yaml
)

MODE="full"
CONTEXT=""
NAMESPACE=""

usage() {
  cat <<'USAGE'
Usage: cluster-up.sh [options]

Options:
  -c, --context NAME      kubeconfig context to target
  -n, --namespace NAME    namespace for application manifests (deployment/service/HPA/ingress)
      --only-k8s          apply only core application manifests
      --only-mysql        apply only MySQL infrastructure
      --only-redis        apply only Redis infrastructure
      --only-kafka        apply only Kafka infrastructure
      --only-nacos        apply only Nacos infrastructure
      --only-prometheus   apply only monitoring stack manifests (Prometheus + Grafana)
  -h, --help              show this help message

Without flags the script provisions the full stack (application, ingress controller,
metrics-server patch, Argo CD setup, and monitoring stack).
USAGE
}

require_cmd() {
  local cmd=$1
  if ! command -v "$cmd" >/dev/null 2>&1; then
    printf 'Missing required command: %s\n' "$cmd" >&2
    exit 1
  fi
}

log_step() {
  printf '\n==> %s\n' "$1"
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -c|--context)
        [[ $# -lt 2 ]] && { printf 'Missing value for %s\n' "$1" >&2; exit 1; }
        CONTEXT="$2"
        shift 2
        ;;
      -n|--namespace)
        [[ $# -lt 2 ]] && { printf 'Missing value for %s\n' "$1" >&2; exit 1; }
        NAMESPACE="$2"
        shift 2
        ;;
      --only-k8s)
        [[ "$MODE" != "full" ]] && { printf 'Multiple mode flags specified.\n' >&2; exit 1; }
        MODE="k8s"
        shift
        ;;
      --only-mysql)
        [[ "$MODE" != "full" ]] && { printf 'Multiple mode flags specified.\n' >&2; exit 1; }
        MODE="mysql"
        shift
        ;;
      --only-redis)
        [[ "$MODE" != "full" ]] && { printf 'Multiple mode flags specified.\n' >&2; exit 1; }
        MODE="redis"
        shift
        ;;
      --only-kafka)
        [[ "$MODE" != "full" ]] && { printf 'Multiple mode flags specified.\n' >&2; exit 1; }
        MODE="kafka"
        shift
        ;;
      --only-nacos)
        [[ "$MODE" != "full" ]] && { printf 'Multiple mode flags specified.\n' >&2; exit 1; }
        MODE="nacos"
        shift
        ;;
      --only-prometheus)
        [[ "$MODE" != "full" ]] && { printf 'Multiple mode flags specified.\n' >&2; exit 1; }
        MODE="prom"
        shift
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        printf 'Unknown argument: %s\n' "$1" >&2
        usage
        exit 1
        ;;
    esac
  done
}

ensure_directories() {
  [[ -d "$K8S_DIR" ]] || { printf 'Missing directory: %s\n' "$K8S_DIR" >&2; exit 1; }
  [[ -d "$PROM_DIR" ]] || { printf 'Missing directory: %s\n' "$PROM_DIR" >&2; exit 1; }
  [[ -d "$GRAFANA_DIR" ]] || { printf 'Missing directory: %s\n' "$GRAFANA_DIR" >&2; exit 1; }
  [[ -d "$MYSQL_DIR" ]] || { printf 'Missing directory: %s\n' "$MYSQL_DIR" >&2; exit 1; }
  [[ -d "$REDIS_DIR" ]] || { printf 'Missing directory: %s\n' "$REDIS_DIR" >&2; exit 1; }
  [[ -d "$KAFKA_DIR" ]] || { printf 'Missing directory: %s\n' "$KAFKA_DIR" >&2; exit 1; }
  [[ -d "$NACOS_DIR" ]] || { printf 'Missing directory: %s\n' "$NACOS_DIR" >&2; exit 1; }
}


ensure_ingress_controller() {
  local manifest="https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml"
  if kubectl "${KUBECTL_CONTEXT_ARGS[@]}" -n ingress-nginx get deployment ingress-nginx-controller >/dev/null 2>&1; then
    printf 'Ingress controller already present; skipping install.\n'
  else
    printf 'Installing ingress-nginx controller from %s\n' "$manifest"
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$manifest"
  fi
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" --namespace ingress-nginx \
    wait --for=condition=Ready pods -l app.kubernetes.io/component=controller --timeout=120s
}

apply_application_manifests() {
  local manifest
  for manifest in "${APPLICATION_MANIFESTS[@]}"; do
    local file="$K8S_DIR/$manifest"
    if [[ ! -f "$file" ]]; then
      printf 'Missing manifest: %s\n' "$file" >&2
      exit 1
    fi
  done

  ensure_ingress_controller

  local pids=()
  for manifest in "${APPLICATION_MANIFESTS[@]}"; do
    (
      local file="$K8S_DIR/$manifest"
      printf 'Applying %s\n' "$file"
      kubectl "${KUBECTL_APP_ARGS[@]}" apply -f "$file"
    ) &
    pids+=($!)
  done

  local status=0
  for pid in "${pids[@]}"; do
    if ! wait "$pid"; then
      status=1
    fi
  done
  if [[ $status -ne 0 ]]; then
    printf 'One or more application manifests failed to apply.\n' >&2
    return 1
  fi
}

setup_ingress_and_metrics() {
  local metrics_manifest="https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml"

  log_step "Ensuring ingress controller"
  ensure_ingress_controller

  log_step "Ensuring metrics-server"
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$metrics_manifest"
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" -n kube-system patch deployment metrics-server \
    --type='json' -p='[
      {"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"},
      {"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-preferred-address-types=InternalIP,Hostname,ExternalIP"}
    ]'

  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" -n kube-system rollout status deploy/metrics-server
  kubectl get hpa
}

apply_prometheus_manifests() {
  for manifest in "${PROM_MANIFEST_ORDER[@]}"; do
    local file="$PROM_DIR/$manifest"
    if [[ ! -f "$file" ]]; then
      printf 'Missing manifest: %s\n' "$file" >&2
      exit 1
    fi
  done

  printf 'Applying %s\n' "$PROM_DIR/monitoring-namespace.yaml"
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$PROM_DIR/monitoring-namespace.yaml"

  for manifest in "${PROM_MANIFEST_ORDER[@]}"; do
    [[ "$manifest" == "monitoring-namespace.yaml" ]] && continue
    local file="$PROM_DIR/$manifest"
    printf 'Applying %s\n' "$file"
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$file"
  done

  printf '\nPrometheus stack applied. Verify with:\n'
  if [[ -n "$CONTEXT" ]]; then
    printf '  kubectl --context %s get pods -n monitoring\n' "$CONTEXT"
  else
    printf '  kubectl get pods -n monitoring\n'
  fi
}

apply_grafana_manifests() {
  for manifest in "${GRAFANA_MANIFEST_ORDER[@]}"; do
    local file="$GRAFANA_DIR/$manifest"
    if [[ ! -f "$file" ]]; then
      printf 'Missing manifest: %s\n' "$file" >&2
      exit 1
    fi
  done

  for manifest in "${GRAFANA_MANIFEST_ORDER[@]}"; do
    local file="$GRAFANA_DIR/$manifest"
    printf 'Applying %s\n' "$file"
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$file"
  done

  local grafana_password
  grafana_password=$(kubectl "${KUBECTL_CONTEXT_ARGS[@]}" get secret grafana-admin -n monitoring -o jsonpath='{.data.admin-password}' | base64 --decode)
  printf '\nGrafana applied. Admin user: admin\n'
  printf 'Grafana admin password: %s\n' "$grafana_password"
}

apply_mysql_cluster() {
  log_step "Applying infra-mysql manifests"
  for manifest in "${MYSQL_MANIFEST_ORDER[@]}"; do
    local file="$MYSQL_DIR/$manifest"
    if [[ ! -f "$file" ]]; then
      printf 'Missing manifest: %s\n' "$file" >&2
      exit 1
    fi
    printf 'Applying %s\n' "$file"
    if [[ "$manifest" == "pv.yaml" ]]; then
      # kind/docker-desktop 等本地叢集常無法提供 PV 的 OpenAPI 定義，需略過驗證。
      kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply --validate=false -f "$file"
    else
      kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$file"
    fi
  done

  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" rollout status statefulset/infra-mysql --timeout=180s
}

apply_redis_cluster() {
  log_step "Applying infra-redis manifests"
  for manifest in "${REDIS_MANIFEST_ORDER[@]}"; do
    local file="$REDIS_DIR/$manifest"
    if [[ ! -f "$file" ]]; then
      printf 'Missing manifest: %s\n' "$file" >&2
      exit 1
    fi
    printf 'Applying %s\n' "$file"
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$file"
  done

  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" rollout status statefulset/infra-redis --timeout=180s

  local job_file="$REDIS_DIR/cluster-job.yaml"
  if [[ -f "$job_file" ]]; then
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" delete job infra-redis-cluster-create --ignore-not-found
    printf 'Applying %s\n' "$job_file"
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$job_file"
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" wait --for=condition=complete --timeout=180s job/infra-redis-cluster-create
  fi
}

apply_kafka_cluster() {
  log_step "Applying infra-kafka manifests"
  printf 'Deleting existing StatefulSet infra-kafka (if present)\n'
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" delete statefulset infra-kafka --ignore-not-found
  printf 'Deleting existing Deployment redpanda-console (if present)\n'
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" delete deployment redpanda-console --ignore-not-found
  for manifest in "${KAFKA_MANIFEST_ORDER[@]}"; do
    local file="$KAFKA_DIR/$manifest"
    if [[ ! -f "$file" ]]; then
      printf 'Missing manifest: %s\n' "$file" >&2
      exit 1
    fi
    printf 'Applying %s\n' "$file"
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$file"
  done

  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" rollout status statefulset/infra-kafka --timeout=300s
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" rollout status deployment/redpanda-console --timeout=180s
}

apply_nacos_cluster() {
  log_step "Applying infra-nacos manifests"
  for manifest in "${NACOS_MANIFEST_ORDER[@]}"; do
    local file="$NACOS_DIR/$manifest"
    if [[ ! -f "$file" ]]; then
      printf 'Missing manifest: %s\n' "$file" >&2
      exit 1
    fi
    printf 'Applying %s\n' "$file"
    if [[ "$manifest" == "pv.yaml" ]]; then
      kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply --validate=false -f "$file"
    else
      kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$file"
    fi
  done

  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" rollout status statefulset/infra-nacos --timeout=180s
}

apply_infra_clusters() {
  local funcs=(
    apply_mysql_cluster
    apply_redis_cluster
    apply_kafka_cluster
    apply_nacos_cluster
  )
  local names=(
    "MySQL"
    "Redis"
    "Kafka"
    "Nacos"
  )

  local pids=()
  local idx
  for idx in "${!funcs[@]}"; do
    "${funcs[$idx]}" &
    pids+=($!)
    printf 'Started provisioning %s (pid %s)\n' "${names[$idx]}" "${pids[-1]}"
  done

  local failures=0
  for idx in "${!pids[@]}"; do
    if ! wait "${pids[$idx]}"; then
      printf 'Provisioning %s failed. Check logs above for details.\n' "${names[$idx]}" >&2
      failures=1
    else
      printf '%s provisioning completed.\n' "${names[$idx]}"
    fi
  done

  if [[ $failures -ne 0 ]]; then
    return 1
  fi
}


ensure_argocd() {
  if kubectl "${KUBECTL_CONTEXT_ARGS[@]}" get namespace argocd >/dev/null 2>&1; then
    printf 'Argo CD namespace already exists; skipping install.\n'
  else
    printf 'Installing Argo CD into namespace argocd\n'
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" create namespace argocd
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
  fi
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" rollout status deployment/argocd-server -n argocd --timeout=180s
}

apply_monitoring_stack() {
  local prom_pid graf_pid status=0

  apply_prometheus_manifests &
  prom_pid=$!

  (
    until kubectl "${KUBECTL_CONTEXT_ARGS[@]}" get namespace monitoring >/dev/null 2>&1; do
      sleep 2
    done
    apply_grafana_manifests
  ) &
  graf_pid=$!

  if ! wait "$prom_pid"; then
    status=1
  fi
  if ! wait "$graf_pid"; then
    status=1
  fi

  return $status
}

configure_argocd() {
  log_step "Logging into Argo CD"
  local argo_password
  argo_password=$(kubectl "${KUBECTL_CONTEXT_ARGS[@]}" -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 --decode)
  local argocd_endpoint="argocd-server.argocd.svc.cluster.local:443"
  argocd login "$argocd_endpoint" \
    --username admin \
    --password "$argo_password" \
    --insecure --grpc-web

  log_step "Configuring Argo CD application"
  if argocd app get gitops --grpc-web >/dev/null 2>&1; then
    printf 'Argo CD application "gitops" already exists; skipping create.\n'
  else
    argocd app create gitops \
      --repo https://github.com/vincentf13/GitOps.git \
      --path k8s \
      --dest-server https://kubernetes.default.svc \
      --dest-namespace default \
      --sync-policy automated --grpc-web
  fi
}

main() {
  parse_args "$@"

  KUBECTL_CONTEXT_ARGS=()
  if [[ -n "$CONTEXT" ]]; then
    KUBECTL_CONTEXT_ARGS=(--context "$CONTEXT")
  fi
  KUBECTL_APP_ARGS=("${KUBECTL_CONTEXT_ARGS[@]}")
  if [[ -n "$NAMESPACE" ]]; then
    KUBECTL_APP_ARGS+=(--namespace "$NAMESPACE")
  fi

  case "$MODE" in
    k8s)
      require_cmd kubectl
      ensure_directories
      log_step "Applying application manifests"
      apply_application_manifests
      printf '\nApplication manifests applied.\n'
      return
      ;;
    prom)
      require_cmd kubectl
      require_cmd base64
      ensure_directories
      log_step "Applying monitoring stack manifests"
      apply_monitoring_stack
      return
      ;;
    mysql)
      require_cmd kubectl
      [[ -d "$MYSQL_DIR" ]] || { printf 'Missing directory: %s\n' "$MYSQL_DIR" >&2; exit 1; }
      log_step "Applying MySQL manifests"
      apply_mysql_cluster
      printf '\nMySQL infrastructure ready. Connect via:\n'
      printf '  mysql -h infra-mysql-0.infra-mysql-headless.default.svc.cluster.local -P 3306 -u root\n'
      printf '  mysql -h infra-mysql-1.infra-mysql-headless.default.svc.cluster.local -P 3306 -u root\n'
      printf '\n'
      ;;
    redis)
      require_cmd kubectl
      [[ -d "$REDIS_DIR" ]] || { printf 'Missing directory: %s\n' "$REDIS_DIR" >&2; exit 1; }
      log_step "Applying Redis manifests"
      apply_redis_cluster
      printf '\nRedis infrastructure ready. Cluster endpoints:\n'
      printf '  redis://infra-redis.default.svc.cluster.local:6379\n'
      printf '  redis://infra-redis-0.infra-redis-headless.default.svc.cluster.local:6379\n'
      printf '  redis://infra-redis-1.infra-redis-headless.default.svc.cluster.local:6379\n'
      printf '  redis://infra-redis-2.infra-redis-headless.default.svc.cluster.local:6379\n'
      printf '\n'
      ;;
    kafka)
      require_cmd kubectl
      [[ -d "$KAFKA_DIR" ]] || { printf 'Missing directory: %s\n' "$KAFKA_DIR" >&2; exit 1; }
      log_step "Applying Kafka manifests"
      apply_kafka_cluster
      printf '\nKafka infrastructure ready. Client endpoints:\n'
      printf '  PLAINTEXT://infra-kafka.default.svc.cluster.local:9092\n'
      printf '  Redpanda Console: http://redpanda-console.default.svc.cluster.local:8080\n'
      printf '\n'
      ;;
    nacos)
      require_cmd kubectl
      [[ -d "$NACOS_DIR" ]] || { printf 'Missing directory: %s\n' "$NACOS_DIR" >&2; exit 1; }
      log_step "Applying Nacos manifests"
      apply_nacos_cluster
      printf '\nNacos infrastructure ready. Access via:\n'
      printf '  http://infra-nacos.default.svc.cluster.local:8848\n'
      printf '\n'
      ;;
    full)
      require_cmd kubectl
      require_cmd argocd
      require_cmd base64
      ensure_directories

      log_step "Applying infrastructure clusters"
      apply_infra_clusters

      log_step "Applying application manifests"
      apply_application_manifests

      setup_ingress_and_metrics

      ensure_argocd
      configure_argocd

      log_step "Applying monitoring stack"
      apply_monitoring_stack
      kubectl "${KUBECTL_CONTEXT_ARGS[@]}" get pods -n monitoring

      printf '\nCluster endpoints are ready. Access services using in-cluster DNS:\n'
      printf '  MySQL primary: infra-mysql-0.infra-mysql-headless.default.svc.cluster.local:3306\n'
      printf '  MySQL secondary: infra-mysql-1.infra-mysql-headless.default.svc.cluster.local:3306\n'
      printf '  Redis cluster: infra-redis.default.svc.cluster.local:6379\n'
      printf '  Kafka broker: infra-kafka.default.svc.cluster.local:9092\n'
      printf '  Redpanda Console: http://redpanda-console.default.svc.cluster.local:8080\n'
      printf '  Nacos console: http://infra-nacos.default.svc.cluster.local:8848\n'
      printf '  Argo CD API/UI: https://argocd-server.argocd.svc.cluster.local\n'
      printf '  Prometheus: http://prometheus.monitoring.svc.cluster.local:9090\n'
      printf '  Alertmanager: http://alertmanager.monitoring.svc.cluster.local:9093\n'
      printf '  Grafana: http://grafana.monitoring.svc.cluster.local:3000\n'
      printf '\n'
      ;;
    *)
      printf 'Unknown execution mode: %s\n' "$MODE" >&2
      exit 1
      ;;
  esac
}

main "$@"
