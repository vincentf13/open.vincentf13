#!/usr/bin/env bash
set -eu
if ! set -o pipefail 2>/dev/null; then
  printf 'Warning: pipefail not supported; continuing without it.\n'
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$REPO_ROOT/log"
mkdir -p "$LOG_DIR"

export K8S_DIR="$REPO_ROOT/k8s"
export PROM_DIR="$K8S_DIR/infra-prometheus"
export GRAFANA_DIR="$K8S_DIR/infra-grafana"
export MYSQL_DIR="$K8S_DIR/infra-mysql"
export REDIS_DIR="$K8S_DIR/infra-redis"
export KAFKA_DIR="$K8S_DIR/infra-kafka"
export KAFKA_CONNECT_DIR="$K8S_DIR/infra-kafka-connect"
export NACOS_DIR="$K8S_DIR/infra-nacos"

# Core application manifests applied to the cluster in order.
APPLICATION_MANIFESTS=(
  service-template/deployment.yaml
  service-template/service.yaml
  service-template/hpa.yaml
  service-test/deployment.yaml
  service-test/service.yaml
  service-test/hpa.yaml
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

require_cmd() {
  local cmd=$1
  if ! command -v "$cmd" >/dev/null 2>&1; then
    printf 'Missing required command: %s\n' "$cmd" >&2
    exit 1
  fi
}

ensure_port_probe_tool() {
  if command -v python3 >/dev/null 2>&1 || command -v python >/dev/null 2>&1 || command -v ss >/dev/null 2>&1 || command -v lsof >/dev/null 2>&1; then
    return 0
  fi
  printf 'Missing required command: python3/ss/lsof\n' >&2
  exit 1
}

# --- Simplified Logging Helper ---
run_with_log() {
  local task_name="$1"
  shift
  local log_file="$LOG_DIR/${task_name}.log"

  : > "$log_file"
  printf "[\033[0;90m .. \033[0m] %-30s (running) Log: %s\n" "$task_name" "$log_file"

  if "$@" >> "$log_file" 2>&1; then
    printf "[\033[0;32m OK \033[0m] %-30s\n" "$task_name"
  else
    printf "[\033[0;31mFAIL\033[0m] %-30s (Check log: %s)\n" "$task_name" "$log_file" >&2
    return 1
  fi
}

log_step() {
  printf '\n==> %s\n' "$1"
}

find_free_port() {
  if command -v python3 >/dev/null 2>&1; then
    python3 -c 'import socket; s=socket.socket(); s.bind(("", 0)); print(s.getsockname()[1]); s.close()'
    return
  fi
  printf '18080\n'
}

get_secret_value() {
  local namespace=$1
  local secret=$2
  local key=$3
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" -n "$namespace" get secret "$secret" -o "jsonpath={.data.$key}" 2>/dev/null | base64 --decode
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -c|--context) CONTEXT="$2"; shift 2 ;;
      -n|--namespace) NAMESPACE="$2"; shift 2 ;;
      --only-k8s) MODE="k8s"; shift ;;
      --only-mysql) MODE="mysql"; shift ;;
      --only-redis) MODE="redis"; shift ;;
      --only-kafka) MODE="kafka"; shift ;;
      --only-kafka-connect) MODE="kafka-connect"; shift ;;
      --only-nacos) MODE="nacos"; shift ;;
      --only-prometheus) MODE="prom"; shift ;;
      -h|--help) usage; exit 0 ;;
      *) printf 'Unknown argument: %s\n' "$1" >&2; exit 1 ;;
    esac
  done
}

ensure_directories() {
  for d in "$K8S_DIR" "$PROM_DIR" "$GRAFANA_DIR" "$MYSQL_DIR" "$REDIS_DIR" "$KAFKA_DIR" "$KAFKA_CONNECT_DIR" "$NACOS_DIR"; do
    [[ -d "$d" ]] || { printf 'Missing directory: %s\n' "$d" >&2; exit 1; }
  done
}

ensure_docker_images() {
  local images=("mysql:8.0" "redis:7.2.4-alpine" "apache/kafka:3.7.0" "docker.redpanda.com/redpandadata/console:latest" "confluentinc/cp-kafka-connect:7.5.0" "curlimages/curl:8.9.1" "nacos/nacos-server:v2.3.2" "registry.k8s.io/ingress-nginx/controller:v1.14.0@sha256:e4127065d0317bd11dc64c4dd38dcf7fb1c3d72e468110b4086e636dbaac943d" "busybox:1.36")
  
  for image in "${images[@]}"; do
    if ! docker image inspect "$image" >/dev/null 2>&1; then
      printf "Pulling %s...\n" "$image"
      docker pull "$image" >/dev/null 2>&1
    fi
  done
}

ensure_ingress_controller() {
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml >/dev/null
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" --namespace ingress-nginx wait --for=condition=Ready pods -l app.kubernetes.io/component=controller --timeout=600s
}

ensure_metrics_server() {
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml >/dev/null
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" -n kube-system patch deployment metrics-server --type='json' -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"},{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-preferred-address-types=InternalIP,Hostname,ExternalIP"}]' >/dev/null
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" -n kube-system rollout status deploy/metrics-server --timeout=300s
}

apply_ingress_manifest() {
  kubectl "${KUBECTL_APP_ARGS[@]}" apply -f "$K8S_DIR/ingress.yaml"
}

apply_application_manifests() {
  local pids=()
  for m in "${APPLICATION_MANIFESTS[@]}"; do
    if [[ "$m" == "ingress.yaml" ]]; then
      apply_ingress_manifest &
    else
      kubectl "${KUBECTL_APP_ARGS[@]}" apply -f "$K8S_DIR/$m" &
    fi
    pids+=($!)
  done
  kubectl "${KUBECTL_APP_ARGS[@]}" apply -f "$K8S_DIR/service-exchange" &
  pids+=($!)
  for pid in "${pids[@]}"; do wait "$pid"; done
}

setup_ingress_and_metrics() {
  ensure_ingress_controller & local p1=$!
  ensure_metrics_server & local p2=$!
  wait "$p1" && wait "$p2"
}

apply_prometheus_manifests() {
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$PROM_DIR/monitoring-namespace.yaml"
  for m in "${PROM_MANIFEST_ORDER[@]}"; do
    [[ "$m" == "monitoring-namespace.yaml" ]] && continue
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$PROM_DIR/$m"
  done
}

apply_grafana_manifests() {
  for m in "${GRAFANA_MANIFEST_ORDER[@]}"; do
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$GRAFANA_DIR/$m"
  done
}

apply_mysql_cluster() {
  for m in "${MYSQL_MANIFEST_ORDER[@]}"; do
    if [[ "$m" == "pv.yaml" ]]; then
      kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply --validate=false -f "$MYSQL_DIR/$m"
    else
      kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$MYSQL_DIR/$m"
    fi
  done
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" rollout status statefulset/infra-mysql --timeout=600s
}

apply_redis_cluster() {
  for m in "${REDIS_MANIFEST_ORDER[@]}"; do
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$REDIS_DIR/$m"
  done
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" rollout status statefulset/infra-redis --timeout=600s
  if [[ -f "$REDIS_DIR/cluster-job.yaml" ]]; then
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" delete job infra-redis-cluster-create --ignore-not-found
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$REDIS_DIR/cluster-job.yaml"
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" wait --for=condition=complete --timeout=600s job/infra-redis-cluster-create
  fi
}

apply_kafka_cluster() {
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" delete statefulset infra-kafka --ignore-not-found
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" delete deployment redpanda-console --ignore-not-found
  for m in "${KAFKA_MANIFEST_ORDER[@]}"; do
    kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$KAFKA_DIR/$m"
  done
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" rollout status statefulset/infra-kafka --timeout=600s
  sleep 5
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" rollout status deployment/redpanda-console --timeout=600s
}

apply_kafka_connect() {
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" delete statefulset infra-kafka-connect --ignore-not-found
  
  # Auto-fix: Release PV if it's stuck in 'Released' state to allow re-binding
  if kubectl "${KUBECTL_CONTEXT_ARGS[@]}" get pv infra-kafka-connect-plugins-pv >/dev/null 2>&1; then
    if [[ $(kubectl "${KUBECTL_CONTEXT_ARGS[@]}" get pv infra-kafka-connect-plugins-pv -o jsonpath='{.status.phase}') == "Released" ]]; then
      printf "Fixing Released PV: infra-kafka-connect-plugins-pv\n"
      kubectl "${KUBECTL_CONTEXT_ARGS[@]}" patch pv infra-kafka-connect-plugins-pv -p '{"spec":{"claimRef": null}}'
    fi
  fi

  for m in "${KAFKA_CONNECT_MANIFEST_ORDER[@]}"; do
    if [[ "$m" == "pv.yaml" ]]; then
      kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply --validate=false -f "$KAFKA_CONNECT_DIR/$m"
    else
      kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$KAFKA_CONNECT_DIR/$m"
    fi
  done
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" rollout status statefulset/infra-kafka-connect --timeout=600s
}

apply_nacos_cluster() {
  for m in "${NACOS_MANIFEST_ORDER[@]}"; do
    if [[ "$m" == "pv.yaml" ]]; then
      kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply --validate=false -f "$NACOS_DIR/$m"
    else
      kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$NACOS_DIR/$m"
    fi
  done
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" rollout status statefulset/infra-nacos --timeout=600s
}

apply_infra_clusters() {
  run_with_log "infra-mysql" apply_mysql_cluster &
  run_with_log "infra-redis" apply_redis_cluster &
  run_with_log "infra-kafka" apply_kafka_cluster &
  run_with_log "infra-kafka-connect" apply_kafka_connect &
  run_with_log "infra-nacos" apply_nacos_cluster &
  local failures=0
  for pid in $(jobs -p); do wait "$pid" || failures=$((failures+1)); done
  return $failures
}

apply_monitoring_stack() {
  # Ensure the shared namespace is created first to avoid race conditions
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" apply -f "$PROM_DIR/monitoring-namespace.yaml" >/dev/null 2>&1
  
  run_with_log "monitoring-prometheus" apply_prometheus_manifests &
  run_with_log "monitoring-grafana" apply_grafana_manifests &
  wait
}

configure_argocd() {
  # Wait for namespace to exist
  until kubectl "${KUBECTL_CONTEXT_ARGS[@]}" get ns argocd >/dev/null 2>&1; do
    sleep 2
  done
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" rollout status deployment/argocd-server -n argocd --timeout=600s
  
  local port=$(find_free_port)
  printf "Port-forwarding ArgoCD to localhost:%s...\n" "$port"
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" -n argocd port-forward svc/argocd-server ${port}:443 >/dev/null 2>&1 &
  local pf_pid=$!
  
  # Ensure port-forward is killed on function exit
  trap "kill $pf_pid 2>/dev/null || true" RETURN EXIT

  # Wait for port to be ready
  local retry=0
  until nc -z localhost ${port} >/dev/null 2>&1 || [ $retry -gt 20 ]; do
    sleep 1
    ((retry++))
  done

  local pw=$(get_secret_value argocd argocd-initial-admin-secret password)
  
  # Login with retry
  retry=0
  until argocd login localhost:${port} --username admin --password "$pw" --insecure --grpc-web >/dev/null 2>&1 || [ $retry -gt 10 ]; do
    sleep 2
    ((retry++))
  done

  # Check and create app with better error visibility in log
  if ! argocd app get gitops --grpc-web >/dev/null 2>&1; then
    retry=0
    until argocd app create gitops --repo https://github.com/vincentf13/GitOps.git --path k8s --dest-server https://kubernetes.default.svc --dest-namespace default --sync-policy automated --grpc-web || [ $retry -gt 5 ]; do
      echo "Retrying ArgoCD app creation (attempt $((retry+1)))..."
      sleep 10
      ((retry++))
    done
  fi
}

main() {
  local start_ts=$(date +%s)
  parse_args "$@"
  KUBECTL_CONTEXT_ARGS=()
  [[ -n "$CONTEXT" ]] && KUBECTL_CONTEXT_ARGS=("--context" "$CONTEXT")
  KUBECTL_APP_ARGS=("${KUBECTL_CONTEXT_ARGS[@]}")
  [[ -n "$NAMESPACE" ]] && KUBECTL_APP_ARGS+=("--namespace" "$NAMESPACE")

  log_step "Ensuring nodes are schedulable"
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" taint nodes --all node-role.kubernetes.io/control-plane- 2>/dev/null || true
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" taint nodes --all node-role.kubernetes.io/master- 2>/dev/null || true

  require_cmd kubectl; require_cmd docker; ensure_directories

  # Pull images sequentially first to avoid concurrent pull issues and rate limiting
  run_with_log "docker-images" ensure_docker_images
  
  log_step "Applying foundational services"
  apply_infra_clusters & local p1=$!
  run_with_log "ingress-metrics" setup_ingress_and_metrics & local p2=$!
  apply_monitoring_stack & local p3=$!
  run_with_log "argocd-install" bash -c "kubectl create ns argocd --dry-run=client -o yaml | kubectl apply -f - && kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml" & local p4=$!
  run_with_log "argocd-config" configure_argocd & local p5=$!
  
  wait "$p1" "$p2" "$p3" "$p4" "$p5" || { printf '\nFoundational services failed.\n' >&2; exit 1; }

  log_step "Applying application"
  run_with_log "app-manifests" apply_application_manifests


  log_step "Final Summary"
  kubectl "${KUBECTL_CONTEXT_ARGS[@]}" get pods -A

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

  printf 'Execution time: %s seconds\n' "$(( $(date +%s) - start_ts ))"
}

main "$@"
