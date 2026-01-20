#!/usr/bin/env bash
set -e

# 用途：
# 自動化重新建置並部署所有 Exchange 微服務。
#
# 功能：
# 1. 遍歷所有 Exchange 服務 (Account, Admin, Auth, Gateway, Market, Matching, Order, Position, Risk, User)。
# 2. 為每個服務執行 Docker Build (標籤為 :local)。
# 3. 將建置好的映像檔載入 Kind 叢集節點 (支援 desktop 叢集)。
# 4. 更新 Kubernetes Deployment 以觸發滾動更新 (Rollout)。
#
# 使用方式：
# 直接執行腳本即可，無須參數。
# ./script/redeploy-exchange.sh

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

  # Load image into Kind cluster
  if command -v kind >/dev/null 2>&1; then
      echo "Loading image into Kind cluster 'desktop' manually..."
      TEMP_TAR="${SVC}.tar"
      docker save -o "$TEMP_TAR" "$SVC:local"
      
      # Find all Kind nodes for this cluster
      NODES=$(docker ps --format "{{.Names}}" | grep "^desktop-")
      
      for NODE in $NODES; do
          echo "  -> Importing into $NODE..."
          # Copy tar to the node container
          docker cp "$TEMP_TAR" "$NODE:/${TEMP_TAR}"
          # Import image using containerd (ctr)
          # We use namespace 'k8s.io' which is standard for Kind/K8s
          docker exec "$NODE" ctr -n k8s.io images import "/${TEMP_TAR}" >/dev/null
          # Clean up inside the node
          docker exec "$NODE" rm "/${TEMP_TAR}"
      done
      
      # Clean up local tar
      rm "$TEMP_TAR"
  fi

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