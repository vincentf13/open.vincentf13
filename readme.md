# 環境建立

1. 安裝 docker 倉庫
   docker build -t demo:latest .
2. 安裝 kind，
   並啟動k8s集群: kind create cluster --config .k8s/kind.yaml
3. 安裝 Helm，並配置 values.yaml，
   更新模板: helm upgrade --install demo k8s/helm
4. 應用 hpa.yaml:
   kubectl apply -f k8s/hpa.yaml
   驗證:
   kubectl get hpa demo-hpa
   kubectl describe hpa demo-hpa
5. 建置 ingress
   kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
   kubectl wait --namespace ingress-nginx --for=condition=Ready pods -l app.kubernetes.io/component=controller --timeout=120s
   建立ingress.yaml
   kubectl apply -f ingress.yaml
6. hosts檔加入
   127.0.0.1 demo.local
7. 鏡像載入 kind k8s : kind load docker-image demo:latest --name mycluster
8. 服務port轉發 : kubectl port-forward deployment/demo-helm 8081:8080
   Ingress port轉發 : kubectl --namespace ingress-nginx port-forward deploy/ingress-nginx-controller 8081:80
9. 測試 curl http://127.0.0.1:8081

# Docker 鏡像更新至K8S指令

## 一般群集（推到 Registry）

```bash
# 1) 建映像並推送
docker build -t localhost:5001/demo:1 .
docker push localhost:5001/demo:1
```

Helm 專案：

```bash
# 2) 調整 values.yaml
image:
  repository: localhost:5001/demo
  tag: "1"
  pullPolicy: IfNotPresent   # 或 Always

# 3) 套用
helm upgrade --install demo k8s/helm -f values.yaml
```

非 Helm：

```bash
# 2) 直接改 Deployment 的容器映像
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
```

Helm 專案：

```bash
# 3) values.yaml 改 tag: "2" 後升級
helm upgrade --install demo k8s/helm -f values.yaml
```

或不改 tag（僅替換同名映像）：

```bash
# 3) 滾動重啟讓 Pod 取用新層
kubectl rollout restart deploy/demo
```

驗證：

```bash
docker exec -it kind-control-plane crictl images | grep demo
kubectl get deploy demo -o jsonpath='{.spec.template.spec.containers[*].image}{"\n"}'
```

## 常見要點

環境為私庫需 pull secret：把 secret 填進 Deployment/values 的 `imagePullSecrets`。
未換 tag 仍想重新拉：設 `image.pullPolicy=Always`，或執行 `kubectl rollout restart deploy/<name>`。
零停機：確保 Deployment 的 `readinessProbe`/`livenessProbe` 已設定，`strategy.rollingUpdate` 為預設或按需調整。
