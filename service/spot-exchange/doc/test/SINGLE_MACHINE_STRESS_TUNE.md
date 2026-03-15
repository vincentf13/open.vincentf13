# Swift Go 16 (SFG16-73) 極限壓測優化指南 (Arrow Lake Ultra 9 深度調優版)

## 1. 核心分配矩陣 (Thread-to-Core Mapping)
針對 Arrow Lake 物理單執行緒 (No HT) 特性，我們將核心執行緒與 P-cores 一一對應，以消除 L1/L2 Cache 競爭。

### 精準綁核啟動指令 (PowerShell)：

> **提示**：執行前請先進入測試腳本目錄：
> `cd service/spot-exchange/doc/test`

#### A. 撮合引擎端 (spot-matching)
分配 3 個獨立 P-core。引用 `matching-low-latency.args` 配置。
```powershell
# 鎖定 Core 0 (Management), Core 1 (Matching), Core 2 (Aeron)
$Engine = Start-Process java -ArgumentList "@../jvm/matching-low-latency.args -jar ../../spot-matching/target/spot-matching.jar" -PassThru
$Engine.ProcessorAffinity = 7 
```

#### B. 網關端 (spot-ws-api)
分配 4 個 P-core。引用 `ws-api-throughput.args` 配置。
```powershell
# 鎖定 Core 3 (Aeron), Core 4-5 (Netty), Core 6 (Management)
$GW = Start-Process java -ArgumentList "@../jvm/ws-api-throughput.args -jar ../../spot-ws-api/target/spot-ws-api.jar" -PassThru
$GW.ProcessorAffinity = 120
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

## 3. 性能瓶頸判定 (Bottleneck Analysis)
由於系統採用 **Busy-Spin (忙等)** 策略以獲取最低延遲，核心 1-2 (Matching) 的 OS 佔用率通常會穩定在 100%，需結合 TPS 表現判定：

*   **TPS 飽和但網關核心 (Core 4-5) 未滿載**：
    *   說明 **Core 1 (Matching Engine)** 已達邏輯處理上限。
    *   或者 **Core 2 (WAL/Aeron 接收)** 的 I/O 吞吐已達實體 SSD/記憶體頻寬極限。
*   **TPS 飽和且網關核心 (Core 4-5) 接近 100%**：
    *   說明瓶頸在 **Netty JSON 解析** 或 WebSocket 流量處理。
    *   優化建議：增加網關核心分配，或改用更高效的二進制序列化。
*   **TPS 隨 k6 壓力增加而線性上升**：
    *   代表系統尚未觸達硬體極限，可繼續增加壓力測試。
