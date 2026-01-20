#!/usr/bin/env bash
set -e

# Get the directory of the script and project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# List of services to process
SERVICES=(
  "exchange-account"
  "exchange-admin"
  "exchange-auth"
  "exchange-gateway"
  "exchange-market"
  "exchange-matching"
  "exchange-order"
  "exchange-position"
  "exchange-risk"
  "exchange-user"
  "exchange-web"
)

echo "Starting build and redeploy process for Exchange services..."

for SVC in "${SERVICES[@]}"; do
  echo "--------------------------------------------------"
  echo "Processing $SVC..."

  SERVICE_DIR="$PROJECT_ROOT/service/exchange/$SVC"
  DOCKERFILE="$SERVICE_DIR/Dockerfile"

  # Check if the service directory exists
  if [[ ! -d "$SERVICE_DIR" ]]; then
    echo "Directory not found: $SERVICE_DIR. Skipping."
    continue
  fi
  
  # Check if Dockerfile exists
  if [[ ! -f "$DOCKERFILE" ]]; then
    echo "Dockerfile not found in $SERVICE_DIR. Skipping."
    continue
  fi

  # Docker Build
  # Context is SERVICE_DIR
  echo "Building Docker image: $SVC:local"
  docker build -t "$SVC:local" -f "$DOCKERFILE" "$SERVICE_DIR"

  # Kubernetes Update
  echo "Updating Kubernetes deployment..."
  # We use '|| true' to prevent script failure if the deployment doesn't exist yet
  if kubectl get deployment "$SVC" >/dev/null 2>&1; then
      kubectl set image "deploy/$SVC" "$SVC=$SVC:local"
      echo "Deployment updated."
  else
      echo "Deployment '$SVC' not found in current namespace. Skipping K8s update."
  fi

done

echo "--------------------------------------------------"
echo "Redeploy process completed."