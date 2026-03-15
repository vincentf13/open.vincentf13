# Swift Go 16 (SFG16-73) 極限壓測優化指南 (Arrow Lake Ultra 9 深度調優版)

## 1. 核心分配矩陣 (Thread-to-Core Mapping)
針對 Arrow Lake 物理單執行緒 (No HT) 特性，我們將核心執行緒與 P-cores 一一對應，以消除 L1/L2 Cache 競爭。

### 精準綁核啟動指令 (PowerShell)：

#### A. 撮合引擎端 (spot-matching)
分配 2 個獨立 P-core 給撮合主循環與接收器。
```powershell
# 鎖定 Core 1 (Affinity 2) 用於撮合引擎，Core 2 (Affinity 4) 用於 Aeron 接收
$Engine = Start-Process java -ArgumentList "-Xms12g -Xmx12g -XX:+UseZGC -XX:+AlwaysPreTouch -jar spot-matching.jar" -PassThru
$Engine.ProcessorAffinity = 6  # (2 + 4 = 6, 涵蓋兩個核心，內部會依序分配)
```

#### B. 網關端 (spot-ws-api)
分配 3 個 P-core 給 Aeron 發送器與 Netty Worker 組（負責 JSON 解析）。
```powershell
# 鎖定 Core 3 (Affinity 8) 用於 Aeron 發送，Core 4-5 (Affinity 48) 用於 Netty Worker
$GW = Start-Process java -ArgumentList "-Xms8g -Xmx8g -XX:+UseZGC -XX:+AlwaysPreTouch -jar spot-ws-api.jar" -PassThru
$GW.ProcessorAffinity = 56 # (8 + 16 + 32 = 56)
```

#### C. 壓測工具 (k6)
完全隔離到 E-cores，避免干擾 P-core 的 L3 Cache 命中率。
```powershell
# 鎖定 Core 6-15 (Affinity 65472)
$K6 = Start-Process k6 -ArgumentList "run stress-test-ws.js" -PassThru
$K6.ProcessorAffinity = 65472
```

## 2. 代碼級別優化
*   **Netty 執行緒數設定**：
    啟動 `spot-ws-api` 時，建議透過環境變數或啟動參數限制 Netty 的 Worker 數為 2，以匹配分配的核心數：
    `-Dnetty.worker.count=2`
*   **Aeron 隔離**：
    目前的 `Worker` 類別會自動佔用單核，配合 `ProcessorAffinity` 設定，OS 將確保它們在正確的 P-core 上全速運轉。

## 3. 性能解讀
*   **若 TPS 上不去且 Core 1 (Matching Engine) 負載不到 100%**：說明瓶頸在 **Core 4-5 (Netty JSON 解析)** 或 **Core 2 (WAL 寫入速度)**。
*   **若 Core 1 滿載且延遲穩定**：這就是 **Ultra 9 285H 的業務邏輯處理極限**。
