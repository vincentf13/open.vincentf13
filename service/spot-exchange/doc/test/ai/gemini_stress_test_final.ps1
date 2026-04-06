# ==============================================================================
# Aeron 交易系統自動化部署與啟動腳本 (Gemini 最終優化分析版)
# ==============================================================================

$base_path = "C:\iProject\open.vincentf13"
$aeron_dir = "$base_path\data\spot-exchange"
$doc_path  = "$base_path\service\spot-exchange\doc"
$log_path  = "$doc_path\test\log"

# --- 0. 系統環境優化 C# 嵌入 ---
$TimerCode = @"
using System;
using System.Runtime.InteropServices;

public class HighPrecisionTimer {
    [DllImport("ntdll.dll", SetLastError = true)]
    public static extern int NtSetTimerResolution(uint DesiredResolution, bool SetResolution, out uint CurrentResolution);

    public static void SetMaxResolution() {
        uint current;
        // 5000 units = 0.5ms (1 unit = 100ns)
        NtSetTimerResolution(5000, true, out current);
    }
    
    public static void ResetResolution() {
        uint current;
        NtSetTimerResolution(5000, false, out current);
    }
}
"@
if (-not ([PSObject].Assembly.GetType("HighPrecisionTimer"))) {
    Add-Type -TypeDefinition $TimerCode
}

# 記錄目前的電源計畫並準備切換 (相容性抓取)
$schemeOutput = powercfg /getactivescheme
if ($schemeOutput -match "([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})") {
    $currentScheme = $matches[1]
} else {
    $currentScheme = "381b4222-f694-41f0-9685-ff5bb260df2e" # 預設平衡模式
}
$ultimateScheme = "e9a42b02-d5df-448d-aa00-03f14749eb61" # 卓越效能 GUID

# 安全清理函數
function Safe-Remove($path) {
    if (-not [string]::IsNullOrWhiteSpace($path) -and (Test-Path $path) -and $path -like "*open.vincentf13*") {
        Write-Host "正在安全清理目錄：「**$path**」..."
        Remove-Item $path -Recurse -Force
    }
}

try {
    Write-Host ">>> [OS] 開啟高精度時鐘 (0.5ms) 並切換至卓越效能模式..."
    [HighPrecisionTimer]::SetMaxResolution()
    powercfg /setactive $ultimateScheme

    # 確保日誌目錄存在
    if (!(Test-Path $log_path)) { New-Item -ItemType Directory -Force $log_path }

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
    # 建立 map 目錄避免 IOException
    New-Item -ItemType Directory -Force "$aeron_dir\map" | Out-Null

    # --- 2. 啟動 獨立式 Media Driver (Mask: 61955) ---
    Write-Host "Starting Standalone Media Driver..."
    $m_dest = "$base_path\service\spot-exchange\spot-matching\target\extracted"
    $aeron_jar_item = Get-ChildItem "$m_dest\dependencies\BOOT-INF\lib\aeron-all-*.jar" | Select-Object -First 1
    $aeron_jar = $aeron_jar_item.FullName

    $driver_args = @(
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
        "-Xms512m", "-Xmx512m",
        "-XX:+UseLargePages",
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
    $driver = Start-Process java -ArgumentList $driver_args -PassThru -WindowStyle Hidden
    $driver.ProcessorAffinity = 61955
    $driver.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::High

    # --- 3. 啟動 Matching Engine (2GB) ---
    Write-Host "Starting Matching Engine (2GB)..."
    $matching_args = @(
        "-Xlog:gc+init", "-Xlog:pagesize",
        "@$doc_path\jvm\matching-low-latency.args",
        "-Xms2G", "-Xmx2G", 
        "-Dspot.affinity.cores=2",
        "-Daeron.driver.enabled=false",
        "-Daeron.dir=$aeron_dir",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.matching.MatchingApp"
    )
    $e = Start-Process java -ArgumentList $matching_args -WorkingDirectory $m_dest -RedirectStandardError "$log_path\error_matching.log" -RedirectStandardOutput "$log_path\stdout_matching.log" -PassThru -WindowStyle Hidden
    $e.ProcessorAffinity = 49159
    $e.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::RealTime
    $e.MinWorkingSet = $e.MaxWorkingSet

    # --- 4. 啟動 Gateway (WS-API) (2GB) ---
    Write-Host "Starting Gateway (WS-API) (2GB)..."
    $g_dest = "$base_path\service\spot-exchange\spot-ws-api\target\extracted"
    $gw_args = @(
        "-Xlog:gc+init", "-Xlog:pagesize",
        "@$doc_path\jvm\ws-api-throughput.args",
        "-Xms2G", "-Xmx2G",
        "-Dspot.affinity.cores=3,4,5,6,7,8",
        "-Daeron.driver.enabled=false",
        "-Daeron.dir=$aeron_dir",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.ws.WsApiApp"
    )
    $g = Start-Process java -ArgumentList $gw_args -WorkingDirectory $g_dest -RedirectStandardError "$log_path\error_gw.log" -RedirectStandardOutput "$log_path\stdout_gw.log" -PassThru -WindowStyle Hidden
    $g.ProcessorAffinity = 49659
    $g.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::RealTime
    $g.MinWorkingSet = $g.MaxWorkingSet

    Write-Host "等待服務穩態 (20s)..."
    Start-Sleep -Seconds 20

    # --- 5. 啟動 Java Benchmark ---
    Write-Host "Starting Java Benchmark..."
    $bench_args = @(
        "-Xlog:gc+init", "-Xlog:pagesize",
        "@$doc_path\jvm\benchmark-low-latency.args",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.ws.benchmark.BenchmarkTool",
        "60000",   # 6萬 orders/sec
        "30",      # 30秒測量
        "30"       # 30秒預熱
    )
    $bench = Start-Process java -ArgumentList $bench_args -WorkingDirectory $g_dest -PassThru -NoNewWindow -RedirectStandardOutput "$log_path\benchmark_result.log"
    $bench.ProcessorAffinity = 52227
    $bench.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::High
    Write-Host "Benchmark (PID: $($bench.Id)) 已啟動，等待完成..."
    $bench.WaitForExit()

} finally {
    Write-Host ">>> [OS] 還原系統環境並清理進程..."
    if ([PSObject].Assembly.GetType("HighPrecisionTimer")) {
        [HighPrecisionTimer]::ResetResolution()
    }
    if ($currentScheme) { powercfg /setactive $currentScheme }
    
    # 確保關閉所有啟動的服務
    if ($driver -and !$driver.HasExited) { Stop-Process -Id $driver.Id -Force }
    if ($e -and !$e.HasExited) { Stop-Process -Id $e.Id -Force }
    if ($g -and !$g.HasExited) { Stop-Process -Id $g.Id -Force }

    Write-Host "分析結果已輸出至 $log_path\benchmark_result.log"
}
