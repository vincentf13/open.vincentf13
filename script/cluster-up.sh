#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
K8S_DIR="$REPO_ROOT/k8s"
PROM_DIR="$K8S_DIR/infra-prometheus"
GRAFANA_DIR="$K8S_DIR/infra-grafana"
MYSQL_DIR="$K8S_DIR/infra-mysql"
REDIS_DIR="$K8S_DIR/infra-redis"
KAFKA_DIR="$K8S_DIR/infra-kafka"

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
  headless-service.yaml
  service.yaml
  statefulset.yaml
)

PORT_FORWARD_PIDS=()
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
      --only-prometheus   apply only monitoring stack manifests (Prometheus + Grafana)
  -h, --help              show this help message

Without flags the script provisions the full stack (application, ingress controller,
metrics-server patch, Argo CD setup, monitoring stack, and port forwards).
USAGE
}

cleanup() {
  if [[ ${#PORT_FORWARD_PIDS[@]} -gt 0 ]]; then
    printf '\nStopping port-forwards...\n'
    # shellcheck disable=SC2046
    kill ${PORT_FORWARD_PIDS[@]} 2>/dev/null || true
  fi
}
trap cleanup EXIT

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

start_port_forward() {
  local description=$1
  shift
  log_step "Port-forward: $description"
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" "$@" &
  local pid=$!
  PORT_FORWARD_PIDS+=("$pid")
  sleep 2
  if ! kill -0 "$pid" 2>/dev/null; then
    printf 'Failed to keep port-forward running: %s\n' "$description" >&2
    exit 1
  fi
  printf 'Port-forward running (pid %s).\n' "$pid"
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
}

apply_application_manifests() {
  for manifest in "${APPLICATION_MANIFESTS[@]}"; do
    local file="$K8S_DIR/$manifest"
    if [[ ! -f "$file" ]]; then
      printf 'Missing manifest: %s\n' "$file" >&2
      exit 1
    fi
  done

  for manifest in "${APPLICATION_MANIFESTS[@]}"; do
    local file="$K8S_DIR/$manifest"
    printf 'Applying %s\n' "$file"
    kubectl "${KUBECTL_APP_ARGS[@]}" apply -f "$file"
  done

  local ingress_manifest="https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml"
  printf 'Ensuring ingress-nginx controller from %s\n' "$ingress_manifest"
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$ingress_manifest"
}

setup_ingress_and_metrics() {
  local ingress_manifest="https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml"
  local metrics_manifest="https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml"

  log_step "Ensuring ingress controller"
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$ingress_manifest"
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" --namespace ingress-nginx \
    wait --for=condition=Ready pods -l app.kubernetes.io/component=controller --timeout=120s

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
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$file"
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
  for manifest in "${KAFKA_MANIFEST_ORDER[@]}"; do
    local file="$KAFKA_DIR/$manifest"
    if [[ ! -f "$file" ]]; then
      printf 'Missing manifest: %s\n' "$file" >&2
      exit 1
    fi
    printf 'Applying %s\n' "$file"
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$file"
  done

  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" rollout status statefulset/infra-kafka --timeout=180s
}

apply_infra_clusters() {
  apply_mysql_cluster
  apply_redis_cluster
  apply_kafka_cluster
}

apply_monitoring_stack() {
  apply_prometheus_manifests
  apply_grafana_manifests
}

configure_argocd() {
  log_step "Logging into Argo CD"
  local argo_password
  argo_password=$(kubectl "${KUBECTL_CONTEXT_ARGS[@]}" -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 --decode)
  argocd login localhost:8080 \
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
    full)
      require_cmd kubectl
      require_cmd argocd
      require_cmd base64
      ensure_directories

      log_step "Applying infrastructure clusters"
      apply_infra_clusters

      local infra_ns_args=()
      if [[ -n "$NAMESPACE" ]]; then
        infra_ns_args=(--namespace "$NAMESPACE")
      fi

      start_port_forward "MySQL-0 3307->3306" "${infra_ns_args[@]}" port-forward svc/infra-mysql-0 3307:3306
      start_port_forward "Redis 6380->6379" "${infra_ns_args[@]}" port-forward svc/infra-redis 6380:6379
      start_port_forward "Kafka 19092->9092" "${infra_ns_args[@]}" port-forward svc/infra-kafka 19092:9092

      log_step "Applying application manifests"
      apply_application_manifests

      setup_ingress_and_metrics

      start_port_forward "Ingress controller 8081->80" --namespace ingress-nginx port-forward deploy/ingress-nginx-controller 8081:80
      start_port_forward "Argo CD API 8080->443" -n argocd port-forward svc/argocd-server 8080:443

      configure_argocd

      start_port_forward "Argo CD UI (secondary) 8082->443" -n argocd port-forward svc/argocd-server 8082:443

      log_step "Applying monitoring stack"
      apply_monitoring_stack
      kubectl "${KUBECTL_CONTEXT_ARGS[@]}" get pods -n monitoring

      start_port_forward "Prometheus UI 9090->9090" -n monitoring port-forward svc/prometheus 9090:9090
      start_port_forward "Alertmanager UI 9093->9093" -n monitoring port-forward svc/alertmanager 9093:9093
      start_port_forward "Grafana UI 3000->80" -n monitoring port-forward svc/grafana 3000:3000

      printf '\nAll port-forwards are active. Press Ctrl+C to stop them when finished.\n'
      wait
      ;;
    *)
      printf 'Unknown execution mode: %s\n' "$MODE" >&2
      exit 1
      ;;
  esac
}

main "$@"
