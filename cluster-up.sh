#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR"

PORT_FORWARD_PIDS=()

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
  kubectl "$@" &
  local pid=$!
  PORT_FORWARD_PIDS+=("$pid")
  # give kubectl a moment to connect so we can detect early exit
  sleep 2
  if ! kill -0 "$pid" 2>/dev/null; then
    printf 'Failed to keep port-forward running: %s\n' "$description" >&2
    exit 1
  fi
  printf 'Port-forward running (pid %s).\n' "$pid"
}

main() {
  require_cmd kubectl
  require_cmd argocd
  require_cmd base64

  log_step "Applying core Kubernetes manifests"
  bash "$ROOT_DIR/apply-k8s.sh"

  local ingress_manifest="https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml"
  local metrics_manifest="https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml"
  log_step "Ensuring ingress controller"
  kubectl apply -f "$ingress_manifest"
  kubectl wait --namespace ingress-nginx --for=condition=Ready pods -l app.kubernetes.io/component=controller --timeout=120s

  log_step "Ensuring metrics-server"
  kubectl apply -f "$metrics_manifest"

  start_port_forward "Ingress controller 8081->80" --namespace ingress-nginx port-forward deploy/ingress-nginx-controller 8081:80
  start_port_forward "Argo CD API 8080->443" -n argocd port-forward svc/argocd-server 8080:443

  log_step "Logging into Argo CD"
  local argo_password
  argo_password=$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 --decode)
  argocd login localhost:8080 \
    --username admin \
    --password "$argo_password" \
    --insecure --grpc-web

  log_step "Configuring Argo CD application"
  if argocd app get gitops --grpc-web >/dev/null 2>&1; then
    printf 'Argo CD application "gitops" already exists; skipping create.\n'
  else
    argocd app create gitops \
      --repo https://github.com/Lilin-Li/GitOps.git \
      --path k8s \
      --dest-server https://kubernetes.default.svc \
      --dest-namespace default \
      --sync-policy automated --grpc-web
  fi

  start_port_forward "Argo CD UI (secondary) 8082->443" -n argocd port-forward svc/argocd-server 8082:443

  log_step "Applying monitoring stack"
  bash "$ROOT_DIR/apply-prometheus.sh"
  kubectl get pods -n monitoring

  start_port_forward "Prometheus UI 9090->9090" -n monitoring port-forward svc/prometheus 9090:9090
  start_port_forward "Alertmanager UI 9093->9093" -n monitoring port-forward svc/alertmanager 9093:9093

  printf '\nAll port-forwards are active. Press Ctrl+C to stop them when finished.\n'
  wait
}

main "$@"
