# The goal of this project
This is a demo project that demonstrates how to set up a local Kubernetes cluster and implement CI/CD automation using GitHub Actions and ArgoCD. It also provisions essential infrastructure within the cluster, such as monitoring and alerting, and provides stress testing scripts to validate basic features like autoscaling and alert notifications.

The goal of this project is to help developers easily build their own local development and testing environments.

# 
# k8s集群與元件建置

1. 安裝 Docker
   linux:

   ```bash
      curl -fsSL https://get.docker.com | sh
      sudo usermod -aG docker $USER
   ```

   windows: 安裝 Desktop desktop，並啟動
   
   
2. 配置環境變數
	Bash 使用者
```bash
# Bash 使用者
echo 'export DOCKER_USER=victorf13' >> ~/.bashrc
source ~/.bashrc


# Zsh 使用者（如使用 Oh My Zsh）
echo 'export DOCKER_USER=victorf13' >> ~/.zshrc
source ~/.zshrc


# 驗證
echo $DOCKER_USER
````

   
3. build docker 鏡像
   docker build -t demo:latest .
   docker tag demo:latest victorf13/demo:latest
   
   推送鏡像
   docker login
   docker push victorf13/demo:latest
   
   k8s/deployment.yaml 配置鏡像
   spec.template.spec.containers[0].image:  victorf13/demo:latest
4. 安裝 kind，
   並建立 k8s 集群: kind create cluster
   驗證集群啟動: kubectl cluster-info --context kind-demo
5. 建置與啟動 ingress Controller
   kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
   kubectl wait --namespace ingress-nginx --for=condition=Ready pods -l app.kubernetes.io/component=controller --timeout=120s
6. 安裝 metric sever : 水平擴容必備
   kubectl delete -n kube-system deploy metrics-server
   kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
   kubectl -n kube-system patch deployment metrics-server \
   --type='json' -p='[
   {"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"},
   {"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-preferred-address
   types=InternalIP,Hostname,ExternalIP"}
   ]'
   
   kubectl
   
   驗證:  
   kubectl get hpa  
   看到看到數值如 `cpu: 25%/80%` 即正常。

7. 執行k8s腳本:
   bash ./apply-k8s.sh
8. 驗證
   
   服務 port 轉發 :
   kubectl port-forward deployment/demo 8081:8080
   
   Ingress port 轉發 : 
   kubectl --namespace ingress-nginx port-forward deploy/ingress-nginx-controller 8081:80
   
   檢查 Ingress path轉發 : kubectl describe ingress demo | sed -n '/Rules:/,$p'
   
   測試 curl http://127.0.0.1:8081

# GitOps CI/CD 建置

## GitHub Action建置

自動打包、上傳鏡像，並更新 GitOps 庫 image TAG

## 建置ArgoCD
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

## 開啟ArgoCD連線

kubectl -n argocd port-forward svc/argocd-server 8080:443

## 登入ArgoCLI

```
argocd login localhost:8080 \
--username admin \
--password "$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d)" \
--insecure --grpc-web
```

## 監聽 Git Repo

argocd app create gitops \
  --repo https://github.com/Lilin-Li/GitOps.git \
  --path k8s \
  --dest-server https://kubernetes.default.svc \
  --dest-namespace default \
  --sync-policy automated --grpc-web

## 驗證
argocd app list --grpc-web
## 拉取更新
argocd app get gitops --refresh --grpc-web
kubectl -n default get deploy demo -ojsonpath='{.spec.template.spec.containers[0].image}{"\n"}'

## 驗證 POD 目前運行的image
kubectl -n argocd port-forward svc/argocd-server 8082:443
訪問: https://127.0.0.1:8082/
帳號: admin
密碼: kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d
密碼尾端要去掉 %


# Prometheus監控與告警

## 手動啟動
  1. 依序執行 
     kubectl apply -f k8s/monitoring-namespace.yaml
     kubectl apply -f k8s/prometheus-rbac.yaml
     接著把其餘監控檔案套用到 monitoring 命名空間（例如逐一 kubectl apply -f k8s/\<file>.yaml）。                                                                                                                                        
  2. 以 kubectl -n monitoring get pods,svc、
     kubectl logs 驗證元件啟動。
     必要時 kubectl port-forward -n monitoring svc/prometheus 9090 查看 UI。
  3. 調整 k8s/alertmanager-configmap.yaml 的 receivers 
     與 k8s/prometheus-ingress.yaml 的網域/TLS 設定，
     然後重新套用對應資源。

## 腳本啟動
執行 bash ./apply-prometheus.sh（必要時加 --context \<your-context>）部署整套監控堆疊，之後用 kubectl get pods -n monitoring 確認狀態。


## Prometheus 存取
  - 若已修改 k8s/prometheus/prometheus-ingress.yaml:13 的 host 為實際網域並配置 DNS→Ingress Controller，直接走 https://<你的網域>；未設 TLS 憑證可在 tls 區塊加入 secretName。  
  - 無外網時可執行 kubectl port-forward -n monitoring svc/prometheus 9090:9090，瀏覽器開 http://localhost:9090；
  - Alertmanager 亦可用 kubectl port-forward -n monitoring svc/alertmanager 9093:9093，瀏覽器開 http://localhost:9093；  
  - 登入 Prometheus UI 後，Status → Targets 應看到 Kubernetes job 狀態；  
  測試告警                                                                                                                                                                                                                                                                                                                                                                              
  - 在 Prometheus UI 的 Alerts 分頁確認告警 firing，再到 Alertmanager (http://localhost:9093/#/alerts 經 port-forward) 查看分派情況；若要演練通知，可在 k8s/prometheus/alertmanager-configmap.yaml:21 加入真實接收器（例如 Slack webhook）後重新套用。                                                                                      
  後續建議 
  1. 想長期保留資料可將 prometheus-deployment.yaml:56 的 emptyDir 換成 PVC；告警規則可再拆多個 ConfigMap


# Grafana建置

## 建置
 1. 加入官方 repo：helm repo add grafana https://grafana.github.io/helm-charts && helm repo update
2. 在 monitoring namespace 安裝：
```
kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -
         helm upgrade --install grafana grafana/grafana \
           --namespace monitoring \
           --set service.type=ClusterIP \
           --set persistence.enabled=false \
           --set adminPassword=admin123
```

3. 取出 admin 密碼：
   kubectl get secret grafana -n monitoring -o jsonpath='{.data.admin-password}' | base64 -d
   去掉尾端的% 即是密碼
4. kubectl port-forward svc/grafana -n monitoring 3000:80 &
   瀏覽器開 http://localhost:3000

## 接入Prometheus 數據

  - 先 port-forward Grafana：kubectl -n monitoring port-forward svc/grafana 3000:80，瀏覽 http://localhost:3000 登入（預設 admin/安裝時設定的密碼）。
  - 左側齒輪 → Data sources → Add data source → 選 Prometheus。
  - HTTP > URL 填寫 Prometheus 服務位址。如果在同一 Kubernetes 叢集，可用 http://prometheus.monitoring.svc:9090 ；若透過本機 port-forward，填 http://
  localhost:9090。
  - Access 選 Server（Grafana 在叢集內能直接打到 Service），或 Browser（只在本機測試時透過瀏覽器連線本地 port-forward）。
  - 保留預設的 Scrape interval / Query timeout，點 Save & test 確認連線成功，底下會顯示 Data source is working。
  - 接著就能在 Dashboard 中使用 PromQL 查詢，例如 process_cpu_usage{app="demo"} 或 sum(rate(container_cpu_usage_seconds_total{namespace="default"}
  [5m]))。




# K6壓力測試

```bash
k6 run ./k6/k6.js
```

#
# 快速啟動集群

## 腳本啟動
完成建置動作後，每次執行腳本: bash cluster-up.sh

## 手動啟動
手動如下操作，即可啟動整個集群

1. 開啟docker desktop
   啟動k8s容器
2. K8s集群配置
   
   檢查k8s 集群啟動 : 
   kubectl cluster-info 
   
   Apply配置:
   bash ./apply-k8s.sh
   
   啟動Ingress Controller
   kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
   kubectl wait --namespace ingress-nginx --for=condition=Ready pods -l app.kubernetes.io/component=controller --timeout=120s
   
   metric server
   kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

   
   驗證
   kubectl --namespace ingress-nginx port-forward deploy/ingress-nginx-controller 8081:80 &
   
   瀏覽器開 http://localhost:8081
1. ArgoCD
   
   開啟ArgoCD連線
   kubectl -n argocd port-forward svc/argocd-server 8080:443 &
   
   登入ArgoCD
```
argocd login localhost:8080 \
--username admin \
--password "$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d)" \
--insecure --grpc-web
   
```
   監聽Reop
```
argocd app create gitops \
--repo https://github.com/Lilin-Li/GitOps.git \
--path k8s \
--dest-server https://kubernetes.default.svc \
--dest-namespace default \
--sync-policy automated --grpc-web
```
   
   ArgoWeb
   kubectl -n argocd port-forward svc/argocd-server 8082:443 &
   訪問: https://127.0.0.1:8082/
   帳號: admin
   密碼: kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d
   密碼尾端要去掉 %
   
3. 監控與告警
   bash ./apply-prometheus.sh
   kubectl get pods -n monitoring
   kubectl port-forward -n monitoring svc/prometheus 9090:9090 &， 瀏覽器開 http://localhost:9090
   kubectl port-forward -n monitoring svc/alertmanager 9093:9093 &， http://localhost:9093
