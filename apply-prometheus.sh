#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROM_DIR="$SCRIPT_DIR/k8s/prometheus"

usage() {
  cat <<'USAGE'
Usage: apply-prometheus.sh [--context NAME]

Applies the monitoring stack manifests in k8s/prometheus/ in a safe order:
  1. monitoring-namespace.yaml
  2. prometheus-rbac.yaml (cluster-scope RBAC)
  3. Prometheus ConfigMaps, Deployment, Service
  4. Alertmanager ConfigMap, StatefulSet, Service
  5. prometheus-ingress.yaml (optional external access)

Options:
  -c, --context    kubeconfig context to use
  -h, --help       show this message
USAGE
}

if ! command -v kubectl >/dev/null 2>&1; then
  printf 'kubectl command not found. Please install kubectl and try again.\n' >&2
  exit 1
fi

if [[ ! -d "$PROM_DIR" ]]; then
  printf 'Prometheus manifest directory not found: %s\n' "$PROM_DIR" >&2
  exit 1
fi

CONTEXT=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -c|--context)
      [[ $# -lt 2 ]] && { printf 'Missing value for %s\n' "$1" >&2; exit 1; }
      CONTEXT="$2"
      shift 2
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

kubectl_args=()
[[ -n "$CONTEXT" ]] && kubectl_args+=("--context" "$CONTEXT")

manifest_order=(
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

for manifest in "${manifest_order[@]}"; do
  file="$PROM_DIR/$manifest"
  if [[ ! -f "$file" ]]; then
    printf 'Missing manifest: %s\n' "$file" >&2
    exit 1
  fi
done

apply_manifest() {
  local manifest_file=$1
  printf 'Applying %s\n' "$manifest_file"
  kubectl apply "${kubectl_args[@]}" -f "$manifest_file"
}

apply_manifest "$PROM_DIR/monitoring-namespace.yaml"
# Namespace creation might lag slightly; retry once if later apply fails on namespace
for manifest in "${manifest_order[@]}"; do
  [[ $manifest == monitoring-namespace.yaml ]] && continue
  apply_manifest "$PROM_DIR/$manifest"
done

printf '\nPrometheus stack applied. Verify with:\n'
printf '  kubectl %s get pods -n monitoring\n' "${CONTEXT:+--context $CONTEXT }"
