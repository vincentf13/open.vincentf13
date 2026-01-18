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
KAFKA_CONNECT_DIR="$K8S_DIR/infra-kafka-connect"
NACOS_DIR="$K8S_DIR/infra-nacos"

# Core application manifests applied to the cluster in order.
APPLICATION_MANIFESTS=(
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

KAFKA_CONNECT_MANIFEST_ORDER=(
  storageclass.yaml
  pv.yaml
  pvc-plugins.yaml
  configmap.yaml
  service.yaml
  statefulset.yaml
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
      --only-kafka-connect apply only Kafka Connect infrastructure
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

ensure_port_probe_tool() {
  if command -v python3 >/dev/null 2>&1; then
    return 0
  fi
  if command -v python >/dev/null 2>&1; then
    return 0
  fi
  if command -v ss >/dev/null 2>&1; then
    return 0
  fi
  if command -v lsof >/dev/null 2>&1; then
    return 0
  fi
  printf 'Missing required command: python3/python/ss/lsof (need one for port selection)\n' >&2
  exit 1
}

log_step() {
  printf '\n==> %s\n' "$1"
}

find_free_port() {
  if command -v python3 >/dev/null 2>&1; then
    python3 - <<'PY'
import socket
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.bind(("127.0.0.1", 0))
print(s.getsockname()[1])
s.close()
PY
    return
  fi

  if command -v python >/dev/null 2>&1; then
    python - <<'PY'
import socket
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.bind(("127.0.0.1", 0))
print(s.getsockname()[1])
s.close()
PY
    return
  fi

  local start_port=18080
  local end_port=18180
  local port

  if command -v ss >/dev/null 2>&1; then
    for ((port=start_port; port<=end_port; port++)); do
      if ! ss -ltn 2>/dev/null | awk '{print $4}' | rg -q "[:.]${port}$"; then
        printf '%s\n' "$port"
        return
      fi
    done
  elif command -v lsof >/dev/null 2>&1; then
    for ((port=start_port; port<=end_port; port++)); do
      if ! lsof -iTCP -sTCP:LISTEN -P 2>/dev/null | rg -q "[:.]${port}[[:space:]]"; then
        printf '%s\n' "$port"
        return
      fi
    done
  fi

  printf '%s\n' "$start_port"
}

get_secret_value() {
  local namespace=$1
  local secret=$2
  local key=$3

  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" -n "$namespace" get secret "$secret" \
    -o "jsonpath={.data.$key}" 2>/dev/null | base64 --decode
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
      --only-kafka-connect)
        [[ "$MODE" != "full" ]] && { printf 'Multiple mode flags specified.\n' >&2; exit 1; }
        MODE="kafka-connect"
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
  [[ -d "$KAFKA_CONNECT_DIR" ]] || { printf 'Missing directory: %s\n' "$KAFKA_CONNECT_DIR" >&2; exit 1; }
  [[ -d "$NACOS_DIR" ]] || { printf 'Missing directory: %s\n' "$NACOS_DIR" >&2; exit 1; }
}

ensure_docker_images() {
  log_step "Checking for required Docker images"
  local images=(
    "mysql:8.0"
    "redis:7.2.4-alpine"
    "apache/kafka:3.7.0"
    "docker.redpanda.com/redpandadata/console:latest"
    "confluentinc/cp-kafka-connect:7.5.0"
    "curlimages/curl:8.9.1"
    "nacos/nacos-server:v2.3.2"
    "registry.k8s.io/ingress-nginx/controller:v1.14.0@sha256:e4127065d0317bd11dc64c4dd38dcf7fb1c3d72e468110b4086e636dbaac943d"
  )

  for image in "${images[@]}"; do
    if ! docker image inspect "$image" >/dev/null 2>&1; then
      printf 'Image "%s" not found locally, pulling...\n' "$image"
      if ! docker pull "$image"; then
        printf 'Failed to pull image "%s". Please check your network and Docker setup.\n' "$image" >&2
        return 1
      fi
    else
      printf 'Image "%s" already exists locally.\n' "$image"
    fi
  done
}



ensure_ingress_controller() {
  local manifest="https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml"
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$manifest"
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" --namespace ingress-nginx \
    wait --for=condition=Ready pods -l app.kubernetes.io/component=controller --timeout=300s
}

handle_ingress_apply_error() {
  local output="$1"
  printf '%s\n' "$output" >&2
  if [[ "$output" == *"validate.nginx.ingress.kubernetes.io"* && "$output" == *"already defined in ingress"* ]]; then
    if [[ "$output" =~ ingress[[:space:]]+([a-z0-9-]+)/([a-z0-9-]+) ]]; then
      local conflict_ns="${BASH_REMATCH[1]}"
      local conflict_name="${BASH_REMATCH[2]}"
      printf 'Deleting conflicting ingress %s/%s\n' "$conflict_ns" "$conflict_name" >&2
      if kubectl "${KUBECTL_CONTEXT_ARGS[@]}" -n "$conflict_ns" delete ingress "$conflict_name"; then
        return 0
      fi
      return 1
    fi
  fi
  return 1
}

apply_ingress_manifest() {
  local file="$K8S_DIR/ingress.yaml"

  local out
  printf 'Applying %s\n' "$file"
  if out=$(kubectl "${KUBECTL_APP_ARGS[@]}" apply -f "$file" 2>&1); then
    printf '%s\n' "$out"
    return 0
  fi

  if handle_ingress_apply_error "$out"; then
    printf 'Re-applying %s after deleting conflict\n' "$file"
    kubectl "${KUBECTL_APP_ARGS[@]}" apply -f "$file"
    return $?
  fi

  return 1
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

  local pids=()
  for manifest in "${APPLICATION_MANIFESTS[@]}"; do
    (
      local file="$K8S_DIR/$manifest"
      if [[ "$manifest" == "ingress.yaml" ]]; then
        apply_ingress_manifest
      else
        printf 'Applying %s\n' "$file"
        kubectl "${KUBECTL_APP_ARGS[@]}" apply -f "$file"
      fi
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

  local ingress_pid metrics_pid failures=0

  (
    log_step "Ensuring ingress controller"
    ensure_ingress_controller
  ) &
  ingress_pid=$!

  (
    log_step "Ensuring metrics-server"
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$metrics_manifest"
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" -n kube-system patch deployment metrics-server \
      --type='json' -p='[
        {"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"},
        {"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-preferred-address-types=InternalIP,Hostname,ExternalIP"}
      ]'
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" -n kube-system rollout status deploy/metrics-server
    kubectl get hpa
  ) &
  metrics_pid=$!

  if ! wait "$ingress_pid"; then
    failures=1
  fi
  if ! wait "$metrics_pid"; then
    failures=1
  fi

  return $failures
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

apply_kafka_connect() {
  log_step "Applying infra-kafka-connect manifests"
  printf 'Deleting existing StatefulSet infra-kafka-connect (if present)\n'
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" delete statefulset infra-kafka-connect --ignore-not-found
  for manifest in "${KAFKA_CONNECT_MANIFEST_ORDER[@]}"; do
    local file="$KAFKA_CONNECT_DIR/$manifest"
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

  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" rollout status statefulset/infra-kafka-connect --timeout=180s
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
    apply_kafka_connect
    apply_nacos_cluster
  )
  local names=(
    "MySQL"
    "Redis"
    "Kafka"
    "Kafka Connect"
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
  log_step "Configuring Argo CD"

  log_step "Waiting for Argo CD server"
  local ready=false
  for i in {1..36}; do
    if kubectl "${KUBECTL_CONTEXT_ARGS[@]}" -n argocd get deploy argocd-server >/dev/null 2>&1 \
      && kubectl "${KUBECTL_CONTEXT_ARGS[@]}" -n argocd get svc argocd-server >/dev/null 2>&1; then
      ready=true
      break
    fi
    sleep 5
  done
  if [[ "$ready" = false ]]; then
    printf 'Argo CD server resources not ready; aborting configuration.\n' >&2
    return 1
  fi
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" rollout status deployment/argocd-server -n argocd --timeout=180s

  # Port forward in the background to make the server accessible.
  local argocd_port
  argocd_port=$(find_free_port)
  if [[ -z "$argocd_port" ]]; then
    printf 'Failed to select a local port for Argo CD port-forward.\n' >&2
    return 1
  fi
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" -n argocd port-forward svc/argocd-server ${argocd_port}:443 &
  local pf_pid=$!

  # Use a subshell to ensure the trap cleans up the port-forward process correctly.
  (
    trap 'printf "Stopping Argo CD port-forward..."; kill $pf_pid; printf "Done.\n"' EXIT
    
    # Give port-forward a moment to establish connection.
    sleep 5

    log_step "Logging into Argo CD"
    local argo_password
    # Retry getting the secret, as it may take a moment to be created.
    for i in {1..12}; do
      argo_password=$(kubectl "${KUBECTL_CONTEXT_ARGS[@]}" -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' 2>/dev/null | base64 --decode)
      if [[ -n "$argo_password" ]]; then
        break
      fi
      printf 'Waiting for argocd-initial-admin-secret...\n'
      sleep 10
    done

    if [[ -z "$argo_password" ]]; then
      printf 'Failed to get Argo CD admin password after multiple retries.\n' >&2
      return 1
    fi

    # Retry logging in, as the server might not be ready immediately.
    local login_success=false
    for i in {1..12}; do
      if argocd login localhost:${argocd_port} \
        --username admin \
        --password "$argo_password" \
        --insecure --grpc-web; then
        login_success=true
        break
      fi
      printf 'Waiting for Argo CD server to be ready for login...\n'
      sleep 10
    done

    if [[ "$login_success" = false ]]; then
        printf "Failed to log into Argo CD after multiple retries.\n" >&2
        return 1
    fi

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
  )
  # Capture the exit code from the subshell to determine success or failure.
  local exit_code=$?
  return $exit_code
}

apply_foundational_services() {
  local pids=()
  local names=()

  (
    log_step "Applying infrastructure clusters"
    apply_infra_clusters
  ) &
  pids+=($!)
  names+=("Infrastructure")

  (
    log_step "Setting up ingress and metrics"
    setup_ingress_and_metrics
  ) &
  pids+=($!)
  names+=("Ingress and Metrics")

  local failures=0
  local idx
  for idx in "${!pids[@]}"; do
    if ! wait "${pids[$idx]}"; then
      printf 'Foundational service "%s" failed to apply. Check logs.\n' "${names[$idx]}" >&2
      failures=1
    else
      printf 'Foundational service "%s" completed.\n' "${names[$idx]}"
    fi
  done

  return $failures
}

main() {
  local start_ts end_ts duration
  start_ts=$(date +%s)

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
      require_cmd base64
      [[ -d "$MYSQL_DIR" ]] || { printf 'Missing directory: %s\n' "$MYSQL_DIR" >&2; exit 1; }
      log_step "Applying MySQL manifests"
      apply_mysql_cluster
      printf '\nMySQL infrastructure ready. Connect via:\n'
      printf '  mysql -h infra-mysql-0.infra-mysql-headless.default.svc.cluster.local -P 3306 -u root\n'
      printf '  mysql -h infra-mysql-1.infra-mysql-headless.default.svc.cluster.local -P 3306 -u root\n'
      local mysql_root_password mysql_app_user mysql_app_password
      mysql_root_password=$(get_secret_value default infra-mysql-secret MYSQL_ROOT_PASSWORD || true)
      mysql_app_user=$(get_secret_value default infra-mysql-secret MYSQL_USER || true)
      mysql_app_password=$(get_secret_value default infra-mysql-secret MYSQL_PASSWORD || true)
      if [[ -n "$mysql_root_password" ]]; then
        printf '  root password: %s\n' "$mysql_root_password"
      fi
      if [[ -n "$mysql_app_user" && -n "$mysql_app_password" ]]; then
        printf '  app user/password: %s / %s\n' "$mysql_app_user" "$mysql_app_password"
      fi
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
    kafka-connect)
      require_cmd kubectl
      [[ -d "$KAFKA_CONNECT_DIR" ]] || { printf 'Missing directory: %s\n' "$KAFKA_CONNECT_DIR" >&2; exit 1; }
      log_step "Applying Kafka Connect manifests"
      apply_kafka_connect
      printf '\nKafka Connect ready. REST endpoint:\n'
      printf '  http://infra-kafka-connect.default.svc.cluster.local:8083\n'
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
      require_cmd docker
      ensure_port_probe_tool
      ensure_directories

      log_step "Ensuring Docker images"
      ensure_docker_images

      log_step "Applying services in parallel"
      local pids=()
      local names=()

      (
        log_step "Applying foundational services"
        apply_foundational_services
      ) &
      pids+=($!)
      names+=("Foundational Services")

      (
        log_step "Applying monitoring stack"
        apply_monitoring_stack
      ) &
      pids+=($!)
      names+=("Monitoring")

      (
        log_step "Ensuring ArgoCD"
        ensure_argocd
      ) &
      pids+=($!)
      names+=("ArgoCD")

      (
        log_step "Applying application manifests"
        apply_application_manifests
      ) &
      pids+=($!)
      names+=("Application Manifests")

      (
        configure_argocd
      ) &
      pids+=($!)
      names+=("ArgoCD Configuration")

      local failures=0
      local idx
      for idx in "${!pids[@]}"; do
        if ! wait "${pids[$idx]}"; then
          printf 'Service "%s" failed to apply. Check logs.\n' "${names[$idx]}" >&2
          failures=1
        else
          printf 'Service "%s" completed.\n' "${names[$idx]}"
        fi
      done

      if [[ $failures -ne 0 ]]; then
        printf '\nOne or more services failed to apply.\n' >&2
        exit 1
      fi

      log_step "Final verification"
      kubectl "${KUBECTL_CONTEXT_ARGS[@]}" get pods

      printf '\nCluster endpoints are ready. Access services using in-cluster DNS:\n'
      local ingress_ns ingress_svc ingress_host
      read -r ingress_ns ingress_svc < <(
        kubectl "${KUBECTL_CONTEXT_ARGS[@]}" get svc -A \
          -l app.kubernetes.io/component=controller,app.kubernetes.io/name=ingress-nginx \
          -o jsonpath='{.items[0].metadata.namespace} {.items[0].metadata.name}' 2>/dev/null || true
        printf '\n'
      )
      if [[ -n "$ingress_ns" && -n "$ingress_svc" ]]; then
        ingress_host="${ingress_svc}.${ingress_ns}.svc.cluster.local"
        printf '  Ingress: http://%s/\n' "$ingress_host"
      fi
      printf '  MySQL primary: infra-mysql-0.infra-mysql-headless.default.svc.cluster.local:3306\n'
      printf '  MySQL secondary: infra-mysql-1.infra-mysql-headless.default.svc.cluster.local:3306\n'
      local mysql_root_password mysql_app_user mysql_app_password
      mysql_root_password=$(get_secret_value default infra-mysql-secret MYSQL_ROOT_PASSWORD || true)
      mysql_app_user=$(get_secret_value default infra-mysql-secret MYSQL_USER || true)
      mysql_app_password=$(get_secret_value default infra-mysql-secret MYSQL_PASSWORD || true)
      if [[ -n "$mysql_root_password" ]]; then
        printf '    root password: %s\n' "$mysql_root_password"
      fi
      if [[ -n "$mysql_app_user" && -n "$mysql_app_password" ]]; then
        printf '    app user/password: %s / %s\n' "$mysql_app_user" "$mysql_app_password"
      fi
      printf '  Redis cluster: infra-redis.default.svc.cluster.local:6379\n'
      printf '  Kafka broker: infra-kafka.default.svc.cluster.local:9092\n'
      printf '  Kafka Connect REST: http://infra-kafka-connect.default.svc.cluster.local:8083\n'
      printf '  Redpanda Console: http://redpanda-console.default.svc.cluster.local:8080\n'
      printf '  Nacos console: http://infra-nacos.default.svc.cluster.local:8848\n'
      printf '  Argo CD API/UI: https://argocd-server.argocd.svc.cluster.local\n'
      local argocd_password
      argocd_password=$(get_secret_value argocd argocd-initial-admin-secret password || true)
      if [[ -n "$argocd_password" ]]; then
        printf '    argocd admin/password: admin / %s\n' "$argocd_password"
      fi
      printf '  Prometheus: http://prometheus.monitoring.svc.cluster.local:9090\n'
      printf '  Alertmanager: http://alertmanager.monitoring.svc.cluster.local:9093\n'
      printf '  Grafana: http://grafana.monitoring.svc.cluster.local:3000\n'
      local grafana_user grafana_password
      grafana_user=$(get_secret_value monitoring grafana-admin admin-user || true)
      grafana_password=$(get_secret_value monitoring grafana-admin admin-password || true)
      if [[ -n "$grafana_user" && -n "$grafana_password" ]]; then
        printf '    grafana admin/password: %s / %s\n' "$grafana_user" "$grafana_password"
      fi
      printf '\n'
      ;;
    *)
      printf 'Unknown execution mode: %s\n' "$MODE" >&2
      exit 1
      ;;
  esac

  end_ts=$(date +%s)
  duration=$((end_ts - start_ts))
  printf 'Execution time: %s seconds\n' "$duration"
}

main "$@"
