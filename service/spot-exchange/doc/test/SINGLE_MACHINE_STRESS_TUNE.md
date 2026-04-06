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

# --- 1.1 檔案預分配 (消除磁碟擴展延遲) ---
if (!(Test-Path $aeron_dir)) { New-Item -ItemType Directory -Force $aeron_dir }
Write-Host "正在執行磁碟空間預分配 (WAL & State: 1GB)..."
fsutil file createnew "$aeron_dir\wal-spot-exchange.dat" 1073741824 | Out-Null
fsutil file createnew "$aeron_dir\matching-engine-state.dat" 1073741824 | Out-Null

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

# --- 4. 啟動 獨立式 Media Driver (專屬 9, 12, 13 + 共享 0,1,14,15 | Mask: 61955) ---
Write-Host "Starting Standalone Media Driver on Dedicated CPUs 9, 12, 13 + Shared Pool..."
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
    "-Daeron.conductor.affinity=9",
    "-Daeron.sender.affinity=12",
    "-Daeron.receiver.affinity=13",
    "-Daeron.term.buffer.length=64m",
    "-Daeron.ipc.term.buffer.length=256m",
    "-Daeron.dir=$aeron_dir",
    "io.aeron.driver.MediaDriver"
)
$driver = Start-Process java -ArgumentList $driver_args -PassThru
$driver.ProcessorAffinity = 61955

# --- 5. & 6. 並行啟動 Engine 與 Gateway ---
# ===== CPU 核心重疊分配表 (Arrow Lake Ultra 9, 16 Logical Processors) =====
# 策略：Worker 租用專屬核，GC 線程在專屬核用盡後於共享核心漂移
#
# Media Driver:                 Dedicated 9, 12, 13 + Shared     mask=61955
# Matching Engine:              Dedicated 2         + Shared     mask=49159
# Gateway:                      Dedicated 3-8       + Shared     mask=49659
# Benchmark Tool:               Dedicated 10, 11                 mask=52227
# ===========================================================================
Write-Host "正在並行啟動 Matching Engine 與 Gateway (WS-API)..."

# Matching Engine 參數
$matching_args = @(
    "@$doc_path\jvm\matching-low-latency.args",
    "-Dspot.affinity.cores=2",
    "-Daeron.driver.enabled=false",
    "-Daeron.dir=$aeron_dir",
    "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
    "open.vincentf13.service.spot.matching.MatchingApp"
)

# Gateway 參數
$gw_args = @(
    "@$doc_path\jvm\ws-api-throughput.args",
    "-Dspot.affinity.cores=3,4,5,6,7,8",
    "-Daeron.driver.enabled=false",
    "-Daeron.dir=$aeron_dir",
    "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
    "open.vincentf13.service.spot.ws.WsApiApp"
)

# 非同步啟動並套用重疊掩碼
$e = Start-Process java -ArgumentList $matching_args -WorkingDirectory $m_dest -RedirectStandardError "$doc_path\test\error_matching.log" -PassThru
$e.ProcessorAffinity = 49159

$g = Start-Process java -ArgumentList $gw_args -WorkingDirectory $g_dest -RedirectStandardError "$doc_path\test\error_gw.log" -PassThru
$g.ProcessorAffinity = 49659

Write-Host "服務已發射：Matching (PID: $($e.Id)), Gateway (PID: $($g.Id))"
Write-Host "提醒：請確保代碼內部已使用 Thread Affinity 將 Worker 鎖定在專屬核 (ME:2, GW:3-8, MD:12,13)。"

# --- 7. 啟動 Java Benchmark (Mask: 52227) ---
$log_dir = "$doc_path\test\log"
if (-not (Test-Path $log_dir)) { New-Item -ItemType Directory -Path $log_dir }

Write-Host "所有服務就緒。正在啟動 Java Benchmark..."

# Benchmark 參數：rate(orders/sec) duration(sec) warmup(sec)
$bench_args = @(
    "@$doc_path\jvm\benchmark-low-latency.args",
    "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
    "open.vincentf13.service.spot.ws.benchmark.BenchmarkTool",
    "60000",   # 6萬 orders/sec
    "60",     # 60秒測量
    "60"       # 60秒預熱
)
$bench = Start-Process java -ArgumentList $bench_args -WorkingDirectory $g_dest -PassThru -NoNewWindow    
Start-Sleep -Milliseconds 500
$bench.ProcessorAffinity = 52227
Write-Host "Benchmark (PID: $($bench.Id)) 已綁核，等待完成..."
$bench.WaitForExit()
Write-Host "Benchmark 完成 (Exit code: $($bench.ExitCode))"
```
