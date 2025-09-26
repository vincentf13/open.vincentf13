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
   docker tag myapp:latest <dockerhub-username>/demo:latest
   推送鏡像
   docker login
   docker push myusername/demo:latest
3. 安裝 kind，
   並建立 k8s 集群: kind create cluster
   驗證集群啟動: kubectl cluster-info --context kind-demo
4. 建置 ingress
   kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
   kubectl wait --namespace ingress-nginx --for=condition=Ready pods -l app.kubernetes.io/component=controller --timeout=120s
5. 執行k8s腳本:
   bash ./apply-k8s.sh
6. hosts 檔加入
   127.0.0.1 demo.local
7. 服務 port 轉發 : kubectl port-forward deployment/demo 8081:8080
   Ingress port 轉發 : kubectl --namespace ingress-nginx port-forward deploy/ingress-nginx-controller 8081:80
   檢查 Ingress path轉發 : kubectl describe ingress demo | sed -n '/Rules:/,$p'
8. 測試 curl http://127.0.0.1:8081

# GitOps CI/CD

## 配置 GitHub Action

自動打包、上傳鏡像，並更新 GitOps 庫 image TAG

## 開啟ArgoCD連線

kubectl -n argocd port-forward svc/argocd-server 8080:443

## 登入ArgoCLI

argocd login localhost:8080 --username admin
--password "$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d)"
--insecure --grpc-web

## 建立（repo 公開即可，不需先 repo add）

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
  1. 依序執行 kubectl apply -f k8s/monitoring-namespace.yaml、kubectl apply -f k8s/prometheus-rbac.yaml，接著把其餘監控檔案套用到 monitoring 命名空間（例如逐一 kubectl apply -f k8s/<file>.yaml）。                                                                                                                                        
  2. 以 kubectl -n monitoring get pods,svc、kubectl logs 驗證元件啟動，必要時 kubectl port-forward -n monitoring svc/prometheus 9090 查看 UI。                                                                                                                                                                                              
  3. 調整 k8s/alertmanager-configmap.yaml 的 receivers 與 k8s/prometheus-ingress.yaml 的網域/TLS 設定，然後重新套用對應資源。

## 腳本啟動
執行 bash ./apply-prometheus.sh（必要時加 --context <your-context>）部署整套監控堆疊，之後用 kubectl get pods -n monitoring 確認狀態。


## Prometheus 存取                                                                                                                                                                                                                                                                                                                           
                                                                                                                                                                                                                                                                                                                                            
  - 若已修改 k8s/prometheus/prometheus-ingress.yaml:13 的 host 為實際網域並配置 DNS→Ingress Controller，直接走 https://<你的網域>；未設 TLS 憑證可在 tls 區塊加入 secretName。                                                                                                                                                              
  - 無外網時可執行 kubectl port-forward -n monitoring svc/prometheus 9090:9090，瀏覽器開 http://localhost:9090；
  - Alertmanager 亦可用 kubectl port-forward -n monitoring svc/alertmanager 9093:9093，瀏覽器開 http://localhost:9093；  
  - 登入 Prometheus UI 後，Status → Targets 應看到 Kubernetes job 狀態；Alerts 頁面可觀察告警是否觸發。                                                                                                                                                                                                                                     
                                                                                                                                                                                                                                                                                                                                            
  測試告警                                                                                                                                                                                                                                                                                                                                  
                                                                                                                                                                                                                                                                                                                                            
  - TargetDown 規則（k8s/prometheus/prometheus-rules.yaml:17）會在某個 up 指標變 0 並持續 5 分鐘時觸發。可臨時 scale 任一被 Prometheus 抓取的服務至 0，例如 kubectl scale deployment demo --replicas=0，等待規則觸發，再 kubectl scale deployment demo --replicas=1 讓 alert 回復。                                                         
  - HighErrorRate 公式（同檔案 :25）看的是 http_requests_total{status=~"5.."}，可在目標服務上短暫製造 5xx（例如對 demo 應用送錯誤請求或加一段測試端點回傳 500）。若想立刻驗證，可暫時把 for: 10m 改為 for: 0m 並 kubectl apply -f k8s/prometheus/prometheus-rules.yaml。                                                                    
  - 在 Prometheus UI 的 Alerts 分頁確認告警 firing，再到 Alertmanager (http://localhost:9093/#/alerts 經 port-forward) 查看分派情況；若要演練通知，可在 k8s/prometheus/alertmanager-configmap.yaml:21 加入真實接收器（例如 Slack webhook）後重新套用。                                                                                      
                                                                                                                                                                                                                                                                                                                                            
  後續建議                                                                                                                                                                                                                                                                                                                                  
                                                                                                                                                                                                                                                                                                                                            
  1. 用 kubectl get pods -n monitoring 確認 Prometheus/Alertmanager 運行情況，必要時 kubectl logs 排查。                                                                                                                                                                                                                                    
  2. 想長期保留資料可將 prometheus-deployment.yaml:56 的 emptyDir 換成 PVC；告警規則可再拆多個 ConfigMap。 