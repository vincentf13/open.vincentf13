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
argocd app get gitops --refresh --grpc-web
kubectl -n default get deploy demo -ojsonpath='{.spec.template.spec.containers[0].image}{"\n"}'

## 驗證 POD 目前運行的image
kubectl -n argocd port-forward svc/argocd-server 8082:443
訪問: https://127.0.0.1:8082/
帳號: admin
密碼: kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d
密碼尾端要去掉 %