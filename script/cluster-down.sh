#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
K8S_DIR="$REPO_ROOT/k8s"

log_step() {
  printf '\n==> %s\n' "$1"
}

delete_resource() {
  local path="$1"
  if [[ -e "$path" ]]; then
    printf 'Deleting %s\n' "$path"
    kubectl delete -f "$path" --ignore-not-found=true --wait=false
  else
    printf 'Warning: %s not found, skipping.\n' "$path"
  fi
}

log_step "Deleting Application Services"
delete_resource "$K8S_DIR/service-exchange"
delete_resource "$K8S_DIR/service-template"
delete_resource "$K8S_DIR/service-test"
delete_resource "$K8S_DIR/ingress.yaml"

log_step "Deleting ArgoCD"
kubectl delete namespace argocd --ignore-not-found=true --wait=false

log_step "Deleting Monitoring Stack"
delete_resource "$K8S_DIR/infra-grafana"
delete_resource "$K8S_DIR/infra-prometheus"
kubectl delete namespace monitoring --ignore-not-found=true --wait=false

log_step "Deleting Infrastructure"
delete_resource "$K8S_DIR/infra-kafka-connect"
delete_resource "$K8S_DIR/infra-kafka"
delete_resource "$K8S_DIR/infra-redis"
delete_resource "$K8S_DIR/infra-mysql"
delete_resource "$K8S_DIR/infra-nacos"

log_step "Cluster cleanup initiated."
echo "Resources are being deleted in the background."
echo "You can check status with: kubectl get pods -A"
