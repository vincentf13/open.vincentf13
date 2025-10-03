# The goal of this project
This project demonstrates how to set up a local Kubernetes cluster and implement CI/CD automation using GitHub Actions and ArgoCD. It also provisions essential infrastructure within the cluster, such as monitoring and alerting, and provides stress testing scripts to validate basic features like autoscaling and alert notifications.

The goal of this project is to help developers easily build their own local development and testing environments.

# 
# 初始建置

## 安裝Docker
1. 安裝 Docker
   linux:
   ```bash
      curl -fsSL https://get.docker.com | sh
      sudo usermod -aG docker $USER
   ```
   windows: 安裝 Desktop desktop，並啟動

## build與push鏡像到dockhub
1. build docker 鏡像
   docker build -t service-test:latest service/service-test
   docker tag service-test:latest \<dockerhub-user>/service-test:latest
   docker build -t service-template:latest service/service-template
   docker tag service-template:latest \<dockerhub-user>/service-template:latest
2. 推送鏡像
   docker login
   docker push \<dockerhub-user>/service-test:latest

## k8s集群與元件建置
   1. 調整 deployment 配置
      k8s/service-test/deployment.yaml 配置鏡像
      spec.template.spec.containers[0].image:  \<dockerhub-user>/service-test:latest
      k8s/service-template/deployment.yaml 配置鏡像
      spec.template.spec.containers[0].image:  \<dockerhub-user>/service-template:latest
2. 安裝 kind
   
   使用 kind 建立 k8s 集群: kind create cluster
   驗證 k8s 集群啟動: kubectl cluster-info --context kind-service-test
3. 建置與啟動 ingress Controller
   kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
   kubectl wait --namespace ingress-nginx --for=condition=Ready pods -l app.kubernetes.io/component=controller --timeout=120s

4. 執行k8s腳本:
   bash ./script/cluster-up.sh --only-k8s
5. 驗證  
   Ingress port 轉發 : 
   kubectl --namespace ingress-nginx port-forward deploy/ingress-nginx-controller 8081:80
    
   測試 curl http://127.0.0.1:8081
   應可正確訪問到服務接口。


## 安裝 metric sever : 
   水平擴容必備
   kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
   
   下面指令執行時，注意不要把參數截掉到了
   
```
kubectl -n kube-system patch deploy metrics-server --type='json' -p='[
  {"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"},
  {"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-preferred-address-types=InternalIP,Hostname,ExternalIP"}
]'
```

kubectl -n kube-system rollout status deploy/metrics-server
   
   驗證:  
   kubectl get hpa  
   過一陣子看到數值如 `cpu: 25%/80%` 即正常。
## GitOps CI/CD 建置

### CI

GitAction腳本：.github/workflows/ci-cd.yaml
push code到GitHub後，會跑這個腳本，自動掃描有哪些微服務有代碼改動，
平行打包、build docker鏡像後，push 到 您的私人docker hub。

注意事項:
1. .github/workflows/ci-cd.yaml 腳本內有說明 git hub需要事先配置的變數。
2. 需將每個微服務目錄，配置在.github/workflows/ci-cd.yaml 開頭的配置中。
3. 你需要在同一個GitHub倉庫開一個名稱為 GitOps的倉庫，並且與我的GitOps倉庫( https://github.com/vincentf13/GitOps )有相同目錄結構與文件。Git Action 跑完CI後，會更新此倉庫內的K8s配置，更新 image的tag，供後面Argo 偵測變更，自動拉此image，部屬到您的 k8s。
### CD

#### 建置ArgoCD
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

#### 開啟ArgoCD連線

kubectl -n argocd port-forward svc/argocd-server 8080:443

#### 登入ArgoCLI

```
argocd login localhost:8080 \
--username admin \
--password "$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d)" \
--insecure --grpc-web
```

#### 監聽 Git Repo

argocd app create gitops \
  --repo https://github.com/<GITHUB_ACCOUNT>/GitOps.git \
  --path k8s \
  --dest-server https://kubernetes.default.svc \
  --dest-namespace default \
  --sync-policy automated --grpc-web

#### 驗證
argocd app list --grpc-web

#### 開啟argo Web
kubectl -n argocd port-forward svc/argocd-server 8082:443
訪問: https://127.0.0.1:8082/
帳號: admin
密碼: kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d
密碼尾端要去掉 %


### 操作說明

完成以上配置後，以後push代碼到你的git倉庫，等CI跑完，就可以到 argo Web ，同步套用最新的k8s配置，自動部屬到你的本地k8s。

## Prometheus監控與告警

1. 安裝helm
2. 執行以下命令 
```
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm upgrade --install mon prometheus-community/kube-prometheus-stack \
  --set grafana.adminPassword=admin

```
  
3. 腳本啟動
   執行 bash ./script/cluster-up.sh --only-prometheus 部署整套監控堆疊。
   使用 kubectl get pods -n monitoring 確認狀態。


4. 訪問 Prometheus 
  - 若已修改 k8s/infra-prometheus/prometheus-ingress.yaml:13 的 host 為實際網域並配置 DNS→Ingress Controller，直接走 https://<你的網域>；未設 TLS 憑證可在 tls 區塊加入 secretName。  
  - 無外網時可執行 kubectl port-forward -n monitoring svc/prometheus 9090:9090，瀏覽器開 http://localhost:9090；
  - Alertmanager 亦可用 kubectl port-forward -n monitoring svc/alertmanager 9093:9093，瀏覽器開 http://localhost:9093；  
  - 登入 Prometheus UI 後，Status → Targets 應看到 Kubernetes job 狀態；  
  
4. 測試告警                                                                                                                                                      
  - 在 Prometheus UI 的 Alerts 分頁確認告警 firing，
    再到 Alertmanager (http://localhost:9093/#/alerts 經 port-forward) 查看分派情況；
    若要演練通知，可在 k8s/infra-prometheus/alertmanager-configmap.yaml:21 加入真實接收器（例如 Slack webhook）後重新套用。                                                                                      
5. 後續建議 
    想長期保留資料可將 prometheus-deployment.yaml:56 的 emptyDir 換成 PVC；告警規則可再拆多個 ConfigMap


## Grafana建置

1. 建立/更新資源
```
kubectl apply -f k8s/infra-prometheus/monitoring-namespace.yaml
kubectl apply -f k8s/infra-grafana/
```
   - `grafana-secret.yaml` 預設建立 `admin` / `admin123` 帳密，可自行調整後重新套用。
   - `grafana-configmap.yaml` 會自動掛載 Prometheus DataSource (`http://prometheus.monitoring.svc:9090`)。

2. 取得管理員密碼
```
kubectl get secret grafana-admin -n monitoring -o jsonpath='{.data.admin-password}' | base64 -d
```

3. 本地測試可透過 port-forward:
```
kubectl -n monitoring port-forward svc/grafana 3000:3000
```

4. 造訪 http://localhost:3000 ，使用步驟 2 的密碼登入，Grafana 會自動載入 Prometheus Datasource；若需長期保存 Dashboard，請將 `grafana-deployment.yaml` 的 `emptyDir` 改成 PVC。


# 一鍵啟動腳本


初始建置完成後，後續即可一鍵執行此腳本，一鍵啟動本地所有基礎設施。
bash ./script/cluster-up.sh

腳本執行後，可直接訪問以下服務:
K8S Ingress入口 :http://127.0.0.1:8081/
ArgoCD: https://127.0.0.1:8082/
Promethous: http://localhost:9090/
AlertManager: http://localhost:9093/#/alerts
Grafana: http://localhost:3000/

並且代碼推送至您的GitHub，自動執行CI/CD，更新你的本地K8s。



# 工具

## 檢查 Ingress path轉發
  kubectl describe ingress service-test | sed -n '/Rules:/,$p'  
## K6壓力測試

1. 安裝 K6
2. 使用
```bash
k6 run ./integration/simulators/src/main/resources/k6/k6.js
```
