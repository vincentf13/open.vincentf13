#!/usr/bin/env bash
set -e

# 用途：
# 自動化重新建置並部署所有 Exchange 微服務 (平行化優化版)。
#
# 功能：
# 1. 平行執行所有服務的 Docker Build。
# 2. 平行載入映像檔到 Kind 叢集。
# 3. 批次更新 Kubernetes Deployment。
#
# 使用方式：
# ./script/redeploy-exchange.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$PROJECT_ROOT/log"
mkdir -p "$LOG_DIR"

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

# 取得 Kind 節點列表 (一次性取得)
NODES=$(docker ps --format "{{.Names}}" | grep "^desktop-" || true)

# 顏色定義
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "Starting PARALLEL build and manual redeploy process for Exchange services..."

# 函數：處理單一服務 (Build + Load + Update)
process_service() {
  local SVC=$1
  local SERVICE_DIR="$PROJECT_ROOT/service/exchange/$SVC"
  local DOCKERFILE="$SERVICE_DIR/Dockerfile"
  local LOG_FILE="$LOG_DIR/${SVC}.log"

  echo "[$SVC] Processing... (Logs: $LOG_FILE)" > "$LOG_FILE"

  if [[ ! -d "$SERVICE_DIR" || ! -f "$DOCKERFILE" ]]; then
    echo "[$SVC] Directory or Dockerfile not found. Skipping." >> "$LOG_FILE"
    return 0
  fi

  # 1. Docker Build
  echo "[$SVC] Building..." >> "$LOG_FILE"
  if ! docker build -t "$SVC:local" -f "$DOCKERFILE" "$SERVICE_DIR" >> "$LOG_FILE" 2>&1; then
    echo "[$SVC] Build FAILED. See $LOG_FILE"
    return 1
  fi

  # 2. Load into Kind (Manual Method - Fast Parallel)
  echo "[$SVC] Loading into Kind nodes..." >> "$LOG_FILE"
  local TMP_DIR="$PROJECT_ROOT/tmp"
  mkdir -p "$TMP_DIR"
  local TEMP_TAR="$TMP_DIR/${SVC}_$(date +%s)_$$.tar"
  if ! docker save -o "$TEMP_TAR" "$SVC:local" >> "$LOG_FILE" 2>&1; then
    echo "[$SVC] Docker save FAILED." >> "$LOG_FILE"
    return 1
  fi

  for NODE in $NODES; do
      echo "  -> Importing into $NODE..." >> "$LOG_FILE"
      docker cp "$TEMP_TAR" "$NODE:/$(basename "$TEMP_TAR")" >> "$LOG_FILE" 2>&1
      docker exec "$NODE" ctr -n k8s.io images import "/$(basename "$TEMP_TAR")" >> "$LOG_FILE" 2>&1
      docker exec "$NODE" rm "/$(basename "$TEMP_TAR")" >> "$LOG_FILE" 2>&1
  done
  rm -f "$TEMP_TAR"

  # 3. K8s Update
  echo "[$SVC] Updating Deployment..." >> "$LOG_FILE"
  if kubectl get deployment "$SVC" >/dev/null 2>&1; then
      kubectl set image "deploy/$SVC" "$SVC=$SVC:local" >> "$LOG_FILE" 2>&1
      echo "[$SVC] Deployment updated." >> "$LOG_FILE"
  else
      echo "[$SVC] Deployment not found. Skipping update." >> "$LOG_FILE"
  fi

  echo -e "${GREEN}[$SVC] Completed successfully.${NC}"
}

# 匯出變數供子 shell 使用
export -f process_service
export PROJECT_ROOT
export LOG_DIR
export NODES
export GREEN RED NC

# 平行執行
PIDS=()
for SVC in "${SERVICES[@]}"; do
  process_service "$SVC" &
  PIDS+=($!)
done

# 等待所有任務完成
FAILURES=0
for PID in "${PIDS[@]}"; do
  wait "$PID" || FAILURES=$((FAILURES+1))
done

echo "--------------------------------------------------"
if [ $FAILURES -eq 0 ]; then
  echo -e "${GREEN}All services images built and loaded successfully!${NC}"
  echo "Triggering rollout restart for all Exchange services..."
  kubectl rollout restart deployment -l 'app in (exchange-account, exchange-admin, exchange-auth, exchange-gateway, exchange-market, exchange-matching, exchange-order, exchange-position, exchange-risk, exchange-user)'
  echo "Check Kubernetes status with: kubectl get pods"
else
  echo -e "${RED}$FAILURES services failed to redeploy. Check logs in $LOG_DIR${NC}"
  exit 1
fi
