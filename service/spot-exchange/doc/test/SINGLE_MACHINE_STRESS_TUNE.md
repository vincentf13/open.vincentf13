# Swift Go 16 (SFG16-73) 極限壓測優化指南 (Arrow Lake Ultra 9 深度調優版)

## 0. 一鍵編譯與綁核啟動 (Master Startup Script)
此腳本已優化啟動順序：**嚴格執行「啟動 -> 立即綁核 -> 等待穩態 -> 下一個」**，確保物理核心分配不衝突。
```powershell
# ==============================================================================
# Aeron 交易系統自動化部署與啟動腳本 (高效能隔離版)
# ==============================================================================

# --- 0. 基礎路徑配置 ---
$base_path = "C:\iProject\open.vincentf13"
$aeron_dir = "$base_path\data\spot-exchange"
$doc_path  = "$base_path\service\spot-exchange\doc"

# 安全清理函數：防止空路徑誤刪
function Safe-Remove($path) {
    if (-not [string]::IsNullOrWhiteSpace($path) -and (Test-Path $path) -and $path -like "*open.vincentf13*") {
        Write-Host "正在安全清理目錄：「**$path**」..."
        Remove-Item $path -Recurse -Force
    }
}

# --- 1. 強制清理舊環境 ---
Write-Host "正在停止舊 Java 進程並清理共享記憶體..."
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2
Safe-Remove $aeron_dir

# --- 2. 執行 Maven 編譯與打包 ---
Write-Host "正在執行 Maven 編譯 (Skip Tests)..."
mvn -f "$base_path\pom.xml" clean package -DskipTests -pl service/spot-exchange/spot-matching,service/spot-exchange/spot-ws-api -am
if ($LASTEXITCODE -ne 0) { Write-Error "Maven 構建失敗，腳本終止。"; return }

# --- 3. 準備運行環境 (Layertools 解壓) ---
# Matching Engine 提取
$m_t = "$base_path\service\spot-exchange\spot-matching\target"
$m_dest = "$m_t\extracted"
Safe-Remove $m_dest
java -Djarmode=layertools -jar "$m_t\spot-matching.jar" extract --destination "$m_dest/"

# Gateway (WS-API) 提取
$g_t = "$base_path\service\spot-exchange\spot-ws-api\target"
$g_dest = "$g_t\extracted"
Safe-Remove $g_dest
java -Djarmode=layertools -jar "$g_t\spot-ws-api.jar" extract --destination "$g_dest/"

# --- 4. 啟動 獨立式 Media Driver (CPU 8-11, Mask: 3840) ---
Write-Host "Starting Standalone Media Driver on CPU 8-11..."
$aeron_jar_item = Get-ChildItem "$m_dest\dependencies\BOOT-INF\lib\aeron-all-*.jar" | Select-Object -First 1
$aeron_jar = $aeron_jar_item.FullName

$driver_args = @(
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
    "-Xms512m", "-Xmx512m",
    "-cp", "$aeron_jar",
    "-Daeron.threading.mode=DEDICATED",
    "-Daeron.conductor.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy",
    "-Daeron.sender.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy",
    "-Daeron.receiver.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy",
    "-Daeron.term.buffer.length=16m",
    "-Daeron.ipc.term.buffer.length=32m",
    "-Daeron.socket.so_sndbuf=2m",
    "-Daeron.socket.so_rcvbuf=2m",
    "-Daeron.dir=$aeron_dir",
    "io.aeron.driver.MediaDriver"
)
$driver = Start-Process java -ArgumentList $driver_args -PassThru
$driver.ProcessorAffinity = 3840
Write-Host "Media Driver (PID: 「**$($driver.Id)**」) 啟動成功，等待 5s..."
Start-Sleep 5

# --- 5. 啟動 Matching Engine (CPU 5-7, 12, Mask: 4320) ---
Write-Host "Starting Matching Engine on CPU 5-7 (+Core 12 for GC)..."
$matching_args = @(
    "@$doc_path\jvm\matching-low-latency.args",
    "-Daeron.driver.enabled=false",
    "-Daeron.dir=$aeron_dir",
    "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;snapshot-dependencies/BOOT-INF/lib/*;spring-boot-loader/",
    "open.vincentf13.service.spot.matching.MatchingApp"
)
$e = Start-Process java -ArgumentList $matching_args -WorkingDirectory $m_dest -RedirectStandardError "$doc_path\test\error_matching.log" -PassThru
$e.ProcessorAffinity = 4320
Write-Host "Matching (PID: 「**$($e.Id)**」) 啟動成功，等待 5s..."
Start-Sleep 5

# --- 6. 啟動 Gateway (WS-API) (CPU 0-4, 12, Mask: 4127) ---
Write-Host "Starting Gateway (WS-API) on CPU 0-4 (+Core 12 for GC)..."
$gw_args = @(
    "@$doc_path\jvm\ws-api-throughput.args",
    "-Daeron.driver.enabled=false",
    "-Daeron.dir=$aeron_dir",
    "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;snapshot-dependencies/BOOT-INF/lib/*;spring-boot-loader/",
    "open.vincentf13.service.spot.ws.WsApiApp"
)
$g = Start-Process java -ArgumentList $gw_args -WorkingDirectory $g_dest -RedirectStandardError "$doc_path\test\error_gw.log" -PassThru
$g.ProcessorAffinity = 4127
Write-Host "Gateway (PID: 「**$($g.Id)**」) 啟動成功，等待 5s..."
Start-Sleep 5

# --- 7. 啟動 k6 極限壓測 (CPU 13-15, Mask: 57344) ---
if ($e.HasExited) { Write-Error "Matching 失敗！請查看 $log_dir\matching_err.log"; return }
if ($g.HasExited) { Write-Error "Gateway 失敗！請檢查 $log_dir\gw_err.log"; return }

Write-Host "所有服務就緒。正在啟動 k6 平衡壓測模式 (200 VUs)..."
Set-Location "$base_path\service\spot-exchange\doc\test"

# 優化 k6 運行參數
$k6_args = @(
    "run",
    "--vus", "200",
    "--duration", "60s",
    "stress-test-ws.js"
)

$k6_proc = Start-Process k6 -ArgumentList $k6_args -RedirectStandardOutput "$log_dir\k6_out.log" -RedirectStandardError "$log_dir\k6_err.log" -PassThru
$k6_proc.ProcessorAffinity = 57344
Write-Host "k6 Turbo (PID: $($k6_proc.Id)) 正在 $doc_path\test 執行中..."
```
