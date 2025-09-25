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
9. 測試 curl http://127.0.0.1:8081

# GitOps CI/CD

## 登入ArgoCLI
argocd login localhost:8080 --username admin --password 9D-SCCv1WAgPWRV6% --insecure
