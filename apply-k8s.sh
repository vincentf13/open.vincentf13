#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S_DIR="$SCRIPT_DIR/k8s"

usage() {
  cat <<'USAGE'
Usage: apply-k8s.sh [--context NAME] [--namespace NAME]

Applies the project's Kubernetes manifests in the correct order:
  1. deployment.yaml
  2. service.yaml
  3. hpa.yaml
  4. ingress.yaml

Options:
  -c, --context    kubeconfig context to use
  -n, --namespace  namespace for the resources (defaults to cluster default)
  -h, --help       show this message
USAGE
}

if ! command -v kubectl >/dev/null 2>&1; then
  printf 'kubectl command not found. Please install kubectl and try again.\n' >&2
  exit 1
fi

CONTEXT=""
NAMESPACE=""

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

MANIFESTS=(
  deployment.yaml
  service.yaml
  hpa.yaml
  ingress.yaml
)

for manifest in "${MANIFESTS[@]}"; do
  file="$K8S_DIR/$manifest"
  if [[ ! -f "$file" ]]; then
    printf 'Missing manifest: %s\n' "$file" >&2
    exit 1
  fi

done

kubectl_args=()
[[ -n "$CONTEXT" ]] && kubectl_args+=("--context" "$CONTEXT")
[[ -n "$NAMESPACE" ]] && kubectl_args+=("--namespace" "$NAMESPACE")

for manifest in "${MANIFESTS[@]}"; do
  file="$K8S_DIR/$manifest"
  printf 'Applying %s\n' "$file"
  kubectl apply "${kubectl_args[@]}" -f "$file"
done

kubectl apply -f \
https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
