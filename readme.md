# k8s集群與元件建置

1. 安裝 Docker
   linux:

   ```bash
      curl -fsSL https://get.docker.com | sh
      sudo usermod -aG docker $USER
   ```

   windows: 安裝 Desktop desktop，並啟動
2. build docker 鏡像
   docker build -t demo:latest .
3. 安裝 kind，
   並建立 k8s 集群: kind create cluster --config k8s/kind.yaml
   驗證集群啟動: kubectl cluster-info --context kind-demo
4. 鏡像載入 kind k8s : kind load docker-image demo:latest --name mycluster
5. 建置 ingress
   kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
   kubectl wait --namespace ingress-nginx --for=condition=Ready pods -l app.kubernetes.io/component=controller --timeout=120s
6. 執行k8s腳本:
   bash ./apply-k8s.sh
7. hosts 檔加入
   127.0.0.1 demo.local
8. 服務 port 轉發 : kubectl port-forward deployment/demo 8081:8080
   Ingress port 轉發 : kubectl --namespace ingress-nginx port-forward deploy/ingress-nginx-controller 8081:80
   檢查 Ingress path轉發 : kubectl describe ingress demo | sed -n '/Rules:/,$p'
1. 測試 curl http://127.0.0.1:8081

# Docker 鏡像更新至 K8S 指令

## 一般群集（推到 Registry）

```bash
# 1) 建映像並推送
docker build -t localhost:5001/demo:1 .
docker push localhost:5001/demo:1

# 2) 更新 Deployment 物件（修改鏡像位址）
kubectl set image deploy/demo demo=localhost:5001/demo:1
```

驗證：

```bash
kubectl rollout status deploy/demo
kubectl get deploy demo -o jsonpath='{.spec.template.spec.containers[*].image}{"\n"}'
```

## kind 開發叢集（本機載入）

```bash
# 1) 建映像
docker build -t demo:2 .

# 2) 載入到 kind 節點（名稱視你的叢集）
kind load docker-image demo:2 --name mycluster

# 3) 讓 Deployment 重新載入鏡像（僅本地 kind）
kubectl rollout restart deploy/demo
```

驗證：

```bash
docker exec -it kind-control-plane crictl images | grep demo
kubectl get deploy demo -o jsonpath='{.spec.template.spec.containers[*].image}{"\n"}'
```

## 常見要點

環境為私庫需 pull secret：把 secret 填進 Deployment 的 `spec.template.spec.imagePullSecrets`。
未換 tag 仍想重新拉：設 `imagePullPolicy=Always`，或執行 `kubectl rollout restart deploy/demo`。
零停機：確保 Deployment 的 `readinessProbe`/`livenessProbe` 已設定，`strategy.rollingUpdate` 為預設或按需調整。
