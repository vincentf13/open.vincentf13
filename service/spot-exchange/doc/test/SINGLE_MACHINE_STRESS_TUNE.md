# Swift Go 16 (SFG16-73) 極限壓測優化指南 (Arrow Lake Ultra 9 深度調優版)

## 0. 一鍵編譯與綁核啟動 (Master Startup Script)
此腳本會自動清理舊進程、Maven 編譯、Layertools 解壓並以正確的 P-core 分配啟動服務。
```powershell
# 1. 強制關閉舊 Java 進程 (釋放檔案鎖定)
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force; Start-Sleep 2

# 2. 編譯與打包
mvn -f C:\iProject\open.vincentf13\pom.xml clean package -DskipTests -pl service/spot-exchange/spot-matching,service/spot-exchange/spot-ws-api -am
if ($LASTEXITCODE -ne 0) { "Build Failed!"; return }

# 3. 啟動 Matching
$m_t = "C:\iProject\open.vincentf13\service\spot-exchange\spot-matching\target"; if(Test-Path "$m_t\extracted"){rm "$m_t\extracted" -Recurse -Force}; java -Djarmode=layertools -jar "$m_t\spot-matching.jar" extract --destination "$m_t\extracted/"
$e = Start-Process java -ArgumentList '"@C:\iProject\open.vincentf13\service\spot-exchange\doc\jvm\matching-low-latency.args" -cp "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;snapshot-dependencies/BOOT-INF/lib/*;spring-boot-loader/" open.vincentf13.service.spot.matching.MatchingApp' -WorkingDirectory "$m_t\extracted" -RedirectStandardError "C:\iProject\open.vincentf13\service\spot-exchange\doc\test\error_matching.log" -PassThru

# 4. 啟動 GateWay
$g_t = "C:\iProject\open.vincentf13\service\spot-exchange\spot-ws-api\target"; if(Test-Path "$g_t\extracted"){rm "$g_t\extracted" -Recurse -Force}; java -Djarmode=layertools -jar "$g_t\spot-ws-api.jar" extract --destination "$g_t\extracted/"
$g = Start-Process java -ArgumentList '"@C:\iProject\open.vincentf13\service\spot-exchange\doc\jvm\ws-api-throughput.args" -cp "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;snapshot-dependencies/BOOT-INF/lib/*;spring-boot-loader/" open.vincentf13.service.spot.ws.WsApiApp' -WorkingDirectory "$g_t\extracted" -RedirectStandardError "C:\iProject\open.vincentf13\service\spot-exchange\doc\test\error_gw.log" -PassThru

# 5. 結果檢查
Start-Sleep 5
if(!$e.HasExited){$e.ProcessorAffinity=7; "Matching Success!"}else{gc "C:\iProject\open.vincentf13\service\spot-exchange\doc\test\error_matching.log"}
if(!$g.HasExited){$g.ProcessorAffinity=120; "GateWay Success!"}else{gc "C:\iProject\open.vincentf13\service\spot-exchange\doc\test\error_gw.log"}

cd "C:\iProject\open.vincentf13\service\spot-exchange\doc\test"
# 使用原生 cmd start /affinity 強制將 k6 綁定在 E-cores (6-15)，Hex: FFC0
cmd.exe /c "start /B /WAIT /affinity FFC0 k6 run stress-test-ws.js"
```

壓測完成後，您可以再次訪問：
   * TPS 曲線：http://localhost:8082/api/test/metrics/tps
   * 飽和度：http://localhost:8082/api/test/metrics/saturation

## 1. 核心分配矩陣 (Thread-to-Core Mapping)
針對 Arrow Lake 物理單執行緒 (No HT) 特性，我們將核心執行緒與 P-cores 一一對應，以消除 L1/L2 Cache 競爭。

### 精準綁核啟動指令 (PowerShell)：

> **提示**：執行前請先進入測試腳本目錄：
> `cd service/spot-exchange/doc/test`

#### A. 撮合引擎端 (spot-matching)
分配 3 個獨立 P-core。
```powershell
$e = Start-Process java -ArgumentList '"@C:\iProject\open.vincentf13\service\spot-exchange\doc\jvm\matching-low-latency.args" -cp "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;snapshot-dependencies/BOOT-INF/lib/*;spring-boot-loader/" open.vincentf13.service.spot.matching.MatchingApp' -WorkingDirectory "C:\iProject\open.vincentf13\service\spot-exchange\spot-matching\target\extracted" -RedirectStandardError "C:\iProject\open.vincentf13\service\spot-exchange\doc\test\error_matching.log" -PassThru; Start-Sleep 5; if ($e.HasExited) { Get-Content "C:\iProject\open.vincentf13\service\spot-exchange\doc\test\error_matching.log" } else { $e.ProcessorAffinity = 7; "Success! PID: $($e.Id)" }
```

#### B. 網關端 (spot-ws-api)
分配 4 個 P-core。
```powershell
$g = Start-Process java -ArgumentList '"@C:\iProject\open.vincentf13\service\spot-exchange\doc\jvm\ws-api-throughput.args" -cp "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;snapshot-dependencies/BOOT-INF/lib/*;spring-boot-loader/" open.vincentf13.service.spot.gw.GateWayApp' -WorkingDirectory "C:\iProject\open.vincentf13\service\spot-exchange\spot-ws-api\target\extracted" -RedirectStandardError "C:\iProject\open.vincentf13\service\spot-exchange\doc\test\error_gw.log" -PassThru; Start-Sleep 5; if ($g.HasExited) { Get-Content "C:\iProject\open.vincentf13\service\spot-exchange\doc\test\error_gw.log" } else { $g.ProcessorAffinity = 120; "Success! PID: $($g.Id)" }
```

#### C. 壓測工具 (k6)
完全隔離到 E-cores，避免干擾 P-core 的 L3 Cache 命中率。
```powershell
cd "C:\iProject\open.vincentf13\service\spot-exchange\doc\test"
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

## 4. 性能飽和度驗證 (Saturation Proof)
要證明撮合引擎是否真正達到 **100% 實體極限**（而非僅僅是空轉），請在壓測期間查詢以下介面：

**GET** `http://localhost:8081/api/test/metrics/saturation`

### 指標解讀：
*   **`saturation_ratio`**：
    *   **接近 100%**：證明撮合引擎的 `doWork` 循環中，每一次 `poll` 都能拿到數據。這代表 **撮合引擎已完全飽和**，生產力達到極限。
    *   **低於 50%**：代表撮合引擎處理太快，大部分時間都在等待數據到達。瓶頸在於網關 JSON 解析或網路傳輸。
*   **`work_count`**：實際處理的消息數量。
*   **`poll_count`**：引擎檢查隊列的總次數。

透過此指標，您可以精確區分「系統忙碌」與「系統飽和」，從而定義出 Ultra 9 的真正性能上限。
