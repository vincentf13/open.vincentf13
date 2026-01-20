import os

# 定義要處理的服務列表
services = [
    "exchange-account",
    "exchange-admin",
    "exchange-auth",
    "exchange-gateway",
    "exchange-market",
    "exchange-matching",
    "exchange-order",
    "exchange-position",
    "exchange-risk",
    "exchange-user",
    "exchange-web"
]

project_root = "/mnt/c/iProject/open.vincentf13"
template_path = os.path.join(project_root, "service/service-template/Dockerfile")

# 讀取模板內容
try:
    with open(template_path, "r") as f:
        template_content = f.read()
except FileNotFoundError:
    print(f"Error: Template not found at {template_path}")
    exit(1)

print("Generating Dockerfiles...")

for svc in services:
    svc_dir = os.path.join(project_root, "service/exchange", svc)
    dockerfile_path = os.path.join(svc_dir, "Dockerfile")
    
    # 確保服務目錄存在
    if not os.path.exists(svc_dir):
        print(f"Skipping {svc}: Directory not found.")
        continue
        
    # 替換 JAR 檔名稱
    # 模板中的 JAR 名稱是 service-template-0.0.1-SNAPSHOT.jar
    # 我們將其替換為 {svc}-0.0.1-SNAPSHOT.jar
    new_jar_name = f"{svc}-0.0.1-SNAPSHOT.jar"
    new_content = template_content.replace("service-template-0.0.1-SNAPSHOT.jar", new_jar_name)
    
    # 寫入 Dockerfile
    with open(dockerfile_path, "w") as f:
        f.write(new_content)
    
    print(f"Created Dockerfile for {svc}")

print("Done.")
