# Swift Go 16 (SFG16-73) 極限壓測優化指南 (Arrow Lake Ultra 9 深度調優版)

## 0. 一鍵編譯與綁核啟動 (Master Startup Script)
此腳本已優化啟動順序：**嚴格執行「啟動 -> 立即綁核 -> 等待穩態 -> 下一個」**，確保物理核心分配不衝突。
```powershell
# 1. 強制關閉舊 Java 進程並清理共享記憶體
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force; Start-Sleep 2
$aeron_dir = "C:/iProject/open.vincentf13/data/spot-exchange/aeron"
if(Test-Path $aeron_dir){ rm $aeron_dir -Recurse -Force }

# 2. 編譯與打包
mvn -f C:\iProject\open.vincentf13\pom.xml clean package -DskipTests -pl service/spot-exchange/spot-matching,service/spot-exchange/spot-ws-api -am
if ($LASTEXITCODE -ne 0) { "Build Failed!"; return }

# 3. 準備運行環境 (Layertools 解壓)
$m_t = "C:\iProject\open.vincentf13\service\spot-exchange\spot-matching\target"
$m_dest = "$m_t\extracted"
if(Test-Path $m_dest){ rm $m_dest -Recurse -Force }
java -Djarmode=layertools -jar "$m_t\spot-matching.jar" extract --destination "$m_dest/"

$g_t = "C:\iProject\open.vincentf13\service\spot-exchange\spot-ws-api\target"
$g_dest = "$g_t\extracted"
if(Test-Path $g_dest){ rm $g_dest -Recurse -Force }
java -Djarmode=layertools -jar "$g_t\spot-ws-api.jar" extract --destination "$g_dest/"

# --- 嚴格順序啟動開始 ---

# 4. 啟動 獨立式 Media Driver (CPU 0-1, Mask: 3)
"Starting Standalone Media Driver on CPU 0-1..."
$aeron_jar = (Get-ChildItem "$m_dest\dependencies\BOOT-INF\lib\aeron-all-*.jar").FullName
$driver_args = "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED", "-Xms512m", "-Xmx512m", "-cp", $aeron_jar, "-Daeron.threading.mode=DEDICATED", "-Daeron.conductor.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy", "-Daeron.sender.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy", "-Daeron.receiver.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy", "-Daeron.dir=$aeron_dir", "io.aeron.driver.MediaDriver"

$driver = Start-Process java -ArgumentList $driver_args -PassThru
$driver.ProcessorAffinity = 3
"Driver (PID: $($driver.Id)) bound to CPU 0-1. Waiting 5s..."
Start-Sleep 5 

# 5. 啟動 Matching (CPU 2-3, Mask: 12)
"Starting Matching Engine on CPU 2-3..."
$matching_args = "@C:\iProject\open.vincentf13\service\spot-exchange\doc\jvm\matching-low-latency.args", "-Daeron.driver.enabled=false", "-Daeron.dir=$aeron_dir", "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;snapshot-dependencies/BOOT-INF/lib/*;spring-boot-loader/", "open.vincentf13.service.spot.matching.MatchingApp"
$e = Start-Process java -ArgumentList $matching_args -WorkingDirectory $m_dest -RedirectStandardError "C:\iProject\open.vincentf13\service\spot-exchange\doc\test\error_matching.log" -PassThru
$e.ProcessorAffinity = 12
"Matching (PID: $($e.Id)) bound to CPU 2-3. Waiting 8s for index rebuild..."
Start-Sleep 8

# 6. 啟動 Gateway (WS-API) (CPU 4-7, 14-15, Mask: 49392)
"Starting Gateway (WS-API) on CPU 4-7, 14-15..."
$gw_args = "@C:\iProject\open.vincentf13\service\spot-exchange\doc\jvm\ws-api-throughput.args", "-Daeron.driver.enabled=false", "-Daeron.dir=$aeron_dir", "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;snapshot-dependencies/BOOT-INF/lib/*;spring-boot-loader/", "open.vincentf13.service.spot.ws.WsApiApp"
$g = Start-Process java -ArgumentList $gw_args -WorkingDirectory $g_dest -RedirectStandardError "C:\iProject\open.vincentf13\service\spot-exchange\doc\test\error_gw.log" -PassThru
$g.ProcessorAffinity = 49392
"Gateway (PID: $($g.Id)) bound to CPU 4-7, 14-15. Waiting 5s..."
Start-Sleep 5

# 7. 最終檢查與執行壓測
if($e.HasExited){ "Matching FAILED! Check error_matching.log"; return }
if($g.HasExited){ "Gateway FAILED! Check error_gw.log"; return }

"All services ready. Starting k6 stress test on E-cores (8-13)..."
CD "C:\iProject\open.vincentf13\service\spot-exchange\doc\test\"
# 鎖定 Core 8-13 (Mask: 16128)
$K6 = Start-Process k6 -ArgumentList "run stress-test-ws.js" -PassThru
$K6.ProcessorAffinity = 16128
```

## 1. 核心分配矩陣 (Thread-to-Core Mapping)
針對 Arrow Lake 物理單執行緒 (No HT) 特性，我們將核心執行緒與 P-cores 一一對應，以消除 L1/L2 Cache 競爭。

### 精準綁核啟動指令 (個別啟動參考)：

#### A. 撮合引擎端 (spot-matching)
分配 2 個獨立 P-core (Core 2, 3)。
```powershell
$e = Start-Process java -ArgumentList '"@C:\iProject\open.vincentf13\service\spot-exchange\doc\jvm\matching-low-latency.args" -cp "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;snapshot-dependencies/BOOT-INF/lib/*;spring-boot-loader/" open.vincentf13.service.spot.matching.MatchingApp' -WorkingDirectory "C:\iProject\open.vincentf13\service\spot-exchange\spot-matching\target\extracted" -RedirectStandardError "C:\iProject\open.vincentf13\service\spot-exchange\doc\test\error_matching.log" -PassThru; $e.ProcessorAffinity = 12; "Started PID: $($e.Id) on Core 2-3"
```

#### B. 網關端 (spot-ws-api)
分配 6 個核心 (4-7 用於 Netty/Aeron, 14-15 用於 Async WAL)。
```powershell
$g = Start-Process java -ArgumentList '"@C:\iProject\open.vincentf13\service\spot-exchange\doc\jvm\ws-api-throughput.args" -cp "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;snapshot-dependencies/BOOT-INF/lib/*;spring-boot-loader/" open.vincentf13.service.spot.ws.WsApiApp' -WorkingDirectory "C:\iProject\open.vincentf13\service\spot-exchange\spot-ws-api\target\extracted" -RedirectStandardError "C:\iProject\open.vincentf13\service\spot-exchange\doc\test\error_gw.log" -PassThru; $g.ProcessorAffinity = 49392; "Started PID: $($g.Id) on Core 4-7, 14-15"
```
