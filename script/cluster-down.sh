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
  local preserve_data="$2"

  if [[ ! -e "$path" ]]; then
    printf 'Warning: %s not found, skipping.\n' "$path"
    return
  fi

  if [[ -d "$path" && "$preserve_data" == "true" ]]; then
    printf 'Deleting %s (Preserving Data)\n' "$path"
    # Find all yaml files excluding pv, pvc, and storageclass definitions
    find "$path" -maxdepth 1 -name "*.yaml" \
      ! -name "*pv.yaml" \
      ! -name "*pvc.yaml" \
      ! -name "*storageclass.yaml" \
      -exec kubectl delete -f {} --ignore-not-found=true --wait=false \;
  else
    printf 'Deleting %s\n' "$path"
    kubectl delete -f "$path" --ignore-not-found=true --wait=false
  fi
}

log_step "Deleting Application Services"
delete_resource "$K8S_DIR/service-exchange" "false"
delete_resource "$K8S_DIR/service-template" "false"
delete_resource "$K8S_DIR/service-test" "false"
delete_resource "$K8S_DIR/ingress.yaml" "false"

log_step "Deleting ArgoCD"
kubectl delete namespace argocd --ignore-not-found=true --wait=false

log_step "Deleting Monitoring Stack"
# Monitoring data (Prometheus TSDB) is usually ephemeral in dev, but can be preserved if needed.
# Here we delete it to keep a clean slate for metrics unless requested otherwise.
delete_resource "$K8S_DIR/infra-grafana" "false"
delete_resource "$K8S_DIR/infra-prometheus" "false"
kubectl delete namespace monitoring --ignore-not-found=true --wait=false

log_step "Deleting Infrastructure"
# Set preserve_data to true for infrastructure to keep MySQL/Kafka/Redis data
delete_resource "$K8S_DIR/infra-kafka-connect" "true"
delete_resource "$K8S_DIR/infra-kafka" "true"
delete_resource "$K8S_DIR/infra-redis" "true"
delete_resource "$K8S_DIR/infra-mysql" "true"
delete_resource "$K8S_DIR/infra-nacos" "true"

log_step "Cluster cleanup initiated."
echo "Resources are being deleted in the background."
echo "You can check status with: kubectl get pods -A"
