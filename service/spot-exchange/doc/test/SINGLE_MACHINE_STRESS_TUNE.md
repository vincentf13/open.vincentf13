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

# --- 2. 執行並行 Maven 編譯 ---
Write-Host "正在執行並行 Maven 編譯 (Threads=1C, Skip Tests)..."
# 使用 -T 1C 開啟並行編譯，顯著縮短構建時間
mvn -f "$base_path\pom.xml" clean package -DskipTests -T 3C -pl service/spot-exchange/spot-matching,service/spot-exchange/spot-ws-api -am
if ($LASTEXITCODE -ne 0) { Write-Error "Maven 構建失敗，腳本終止。"; return }

# --- 3. 準備運行環境 (並行 Layertools 解壓) ---
Write-Host "正在並行提取層級化 Jar 內容 (layertools)..."
$m_t = "$base_path\service\spot-exchange\spot-matching\target"
$m_dest = "$m_t\extracted"
$g_t = "$base_path\service\spot-exchange\spot-ws-api\target"
$g_dest = "$g_t\extracted"

Safe-Remove $m_dest
Safe-Remove $g_dest

# 並行執行解壓任務
$job1 = Start-Job -ScriptBlock { param($t, $d) java -Djarmode=layertools -jar "$t\spot-matching.jar" extract --destination "$d/" } -ArgumentList $m_t, $m_dest
$job2 = Start-Job -ScriptBlock { param($t, $d) java -Djarmode=layertools -jar "$t\spot-ws-api.jar" extract --destination "$d/" } -ArgumentList $g_t, $g_dest
Wait-Job $job1, $job2 | Out-Null
Receive-Job $job1, $job2
Remove-Job $job1, $job2

# --- 4. 啟動 獨立式 Media Driver (CPU 8-11, Mask: 3840) ---
Write-Host "Starting Standalone Media Driver on CPU 8-11..."
$aeron_jar_item = Get-ChildItem "$m_dest\dependencies\BOOT-INF\lib\aeron-all-*.jar" | Select-Object -First 1
$aeron_jar = $aeron_jar_item.FullName

# 極限優化 Aeron 緩衝區，適配 Ultra 9 高併發
$driver_args = @(
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
    "-Xms512m", "-Xmx512m",
    "-cp", "$aeron_jar",
    "-Daeron.threading.mode=DEDICATED",
    "-Daeron.conductor.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy",
    "-Daeron.sender.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy",
    "-Daeron.receiver.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy",
    "-Daeron.term.buffer.length=64m",
    "-Daeron.ipc.term.buffer.length=128m",
    "-Daeron.socket.so_sndbuf=4m",
    "-Daeron.socket.so_rcvbuf=4m",
    "-Daeron.dir=$aeron_dir",
    "io.aeron.driver.MediaDriver"
)
$driver = Start-Process java -ArgumentList $driver_args -PassThru
$driver.ProcessorAffinity = 1792

# --- 智慧等待 Media Driver 初始化 (Polling cnc.dat) ---
Write-Host "等待 Aeron 共享記憶體初始化..."
$cnc_file = "$aeron_dir\cnc.dat"
$retry = 0
while (-not (Test-Path $cnc_file) -and $retry -lt 50) {
    Start-Sleep -Milliseconds 100
    $retry++
}
Write-Host "Media Driver 就緒 (耗時: $($retry * 100) ms)。"

# --- 5. & 6. 並行啟動 Engine 與 Gateway ---
Write-Host "正在並行啟動 Matching Engine 與 Gateway (WS-API)..."

# Matching Engine 參數
$matching_args = @(
    "@$doc_path\jvm\matching-low-latency.args",
    "-Daeron.driver.enabled=false",
    "-Daeron.dir=$aeron_dir",
    "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
    "open.vincentf13.service.spot.matching.MatchingApp"
)

# Gateway 參數
$gw_args = @(
    "@$doc_path\jvm\ws-api-throughput.args",
    "-Daeron.driver.enabled=false",
    "-Daeron.dir=$aeron_dir",
    "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
    "open.vincentf13.service.spot.ws.WsApiApp"
)

# 非同步啟動並立即綁核
$e = Start-Process java -ArgumentList $matching_args -WorkingDirectory $m_dest -RedirectStandardError "$doc_path\test\error_matching.log" -PassThru
$e.ProcessorAffinity = 96

$g = Start-Process java -ArgumentList $gw_args -WorkingDirectory $g_dest -RedirectStandardError "$doc_path\test\error_gw.log" -PassThru
$g.ProcessorAffinity = 36895

Write-Host "雙核服務已併發發射：Matching (PID: $($e.Id)), Gateway (PID: $($g.Id))"

# --- 智慧等待與狀態檢測 ---
Write-Host "正在等待服務進入穩態 (監測 HTTP 8080)..."
$retry = 0
while ($retry -lt 30) {
    try {
        $check = Invoke-WebRequest -Uri "http://127.0.0.1:8080/ws/spot" -Method Get -ErrorAction SilentlyContinue
        if ($check.StatusCode -eq 400 -or $check.StatusCode -eq 101) { break } 
    } catch { }
    Start-Sleep -Seconds 1
    $retry++
}
Write-Host "服務就緒 (耗時: $retry s)，開始壓測。"

# --- 7. 啟動 k6 極限壓測 ---
if ($e.HasExited) { Write-Error "Matching 失敗！"; return }
if ($g.HasExited) { Write-Error "Gateway 失敗！"; return }

$log_dir = "$doc_path\test\log"
if (-not (Test-Path $log_dir)) { New-Item -ItemType Directory -Path $log_dir }

Write-Host "所有服務就緒。正在啟動 k6 超級壓測模式 (100 VUs, 4-Cores Dedicated)..."
Set-Location "$base_path\service\spot-exchange\doc\test"

# 設置 Go 運行時使用 4 個核心 (7, 11, 13, 14)
$env:GOMAXPROCS = "4"

# 優化 k6 運行參數
$k6_args = @(
    "run",
    "--vus", "100",
    "--duration", "300s",
    "stress-test-ws.js"
)

$k6_proc = Start-Process k6 -ArgumentList $k6_args -RedirectStandardOutput "$log_dir\k6_out.log" -RedirectStandardError "$log_dir\k6_err.log" -PassThru
$k6_proc.ProcessorAffinity = 26752
Write-Host "k6 Turbo (PID: $($k6_proc.Id)) 已鎖定 Core 7, 11, 13, 14，火力全開中..."
```
