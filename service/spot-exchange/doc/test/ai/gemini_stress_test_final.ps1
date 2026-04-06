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
$ultimateScheme = "fe8e30e8-5fe7-4e44-b2f9-e7b4495773eb" # 卓越效能 GUID (更新為實際生成的 GUID)

# 網卡屬性記錄與切換
$nicName = (Get-NetAdapter | Where-Object Status -eq "Up" | Select-Object -First 1).Name
$originalCoalescing = (Get-NetAdapterAdvancedProperty -Name $nicName -DisplayName "Packet Coalescing" -ErrorAction SilentlyContinue).DisplayValue
$originalMimo = (Get-NetAdapterAdvancedProperty -Name $nicName -DisplayName "MIMO Power Save Mode" -ErrorAction SilentlyContinue).DisplayValue

# 安全清理函數
function Safe-Remove($path) {
    if (-not [string]::IsNullOrWhiteSpace($path) -and (Test-Path $path) -and $path -like "*open.vincentf13*") {
        Write-Host "正在安全清理目錄：「**$path**」..."
        Remove-Item $path -Recurse -Force
    }
}

try {
    Write-Host ">>> [OS] 開啟高精度時鐘、關閉網卡省電與封包合併、切換至卓越效能..."
    [HighPrecisionTimer]::SetMaxResolution()
    powercfg /setactive $ultimateScheme
    
    # 關閉網卡干擾屬性
    if ($originalCoalescing) { Set-NetAdapterAdvancedProperty -Name $nicName -DisplayName "Packet Coalescing" -DisplayValue "Disabled" -ErrorAction SilentlyContinue }
    if ($originalMimo) { Set-NetAdapterAdvancedProperty -Name $nicName -DisplayName "MIMO Power Save Mode" -DisplayValue "No SMPS" -ErrorAction SilentlyContinue }

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

    # --- 2. 啟動 獨立式 Media Driver (專屬 12, 13 + 共享 0, 1, 14, 15 | Mask: 53251) ---
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
        "-Daeron.conductor.affinity=0",
        "-Daeron.sender.affinity=12",
        "-Daeron.receiver.affinity=13",
        "-Daeron.term.buffer.length=64m",
        "-Daeron.ipc.term.buffer.length=256m",
        "-Daeron.dir=$aeron_dir",
        "io.aeron.driver.MediaDriver"
    )
    $driver = Start-Process java -ArgumentList $driver_args -PassThru -WindowStyle Hidden
    $driver.ProcessorAffinity = 53251
    $driver.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::High

    # --- 3. 啟動 Matching Engine (8GB, P-Core 2 | Mask: 4) ---
    Write-Host "Starting Matching Engine (8GB, P-Core 2)..."
    $matching_args = @(
        "-Xlog:gc+init", "-Xlog:pagesize",
        "@$doc_path\jvm\matching-low-latency.args",
        "-Xms8G", "-Xmx8G", 
        "-Dspot.affinity.cores=2",
        "-Daeron.driver.enabled=false",
        "-Daeron.dir=$aeron_dir",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.matching.MatchingApp"
    )
    $e = Start-Process java -ArgumentList $matching_args -WorkingDirectory $m_dest -RedirectStandardError "$log_path\error_matching.log" -RedirectStandardOutput "$log_path\stdout_matching.log" -PassThru -WindowStyle Hidden
    $e.ProcessorAffinity = 4 # 僅核心 2
    $e.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::RealTime
    $e.MinWorkingSet = $e.MaxWorkingSet

    # --- 4. 啟動 Gateway (WS-API) (4GB, P-Cores 3-7 | Mask: 248) ---
    Write-Host "Starting Gateway (WS-API) (4GB, P-Cores 3-7)..."
    $g_dest = "$base_path\service\spot-exchange\spot-ws-api\target\extracted"
    $gw_args = @(
        "@$doc_path\jvm\ws-api-throughput.args",
        "-Dspot.affinity.cores=3,4,5,6,7",
        "-Daeron.driver.enabled=false",
        "-Daeron.dir=$aeron_dir",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.ws.WsApiApp"
    )
    $g = Start-Process java -ArgumentList $gw_args -WorkingDirectory $g_dest -RedirectStandardError "$log_path\error_gw.log" -RedirectStandardOutput "$log_path\stdout_gw.log" -PassThru -WindowStyle Hidden
    $g.ProcessorAffinity = 248 # 核心 3, 4, 5, 6, 7
    $g.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::RealTime
    $g.MinWorkingSet = $g.MaxWorkingSet

    Write-Host "等待服務穩態 (20s)..."
    Start-Sleep -Seconds 20

    # --- 5. 啟動 Java Benchmark (與核心 8-11 綁定，避免搶佔 2-7) ---
    Write-Host "Starting Java Benchmark (Cores 8-11)..."
    $bench_args = @(
        "@$doc_path\jvm\benchmark-low-latency.args",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.ws.benchmark.BenchmarkTool",
        "60000",   # 6萬 orders/sec
        "30",      # 30秒測量
        "30"       # 30秒預熱
    )
    $bench = Start-Process java -ArgumentList $bench_args -WorkingDirectory $g_dest -PassThru -NoNewWindow -RedirectStandardOutput "$log_path\benchmark_result.log"
    $bench.ProcessorAffinity = 3840 # 核心 8, 9, 10, 11
    $bench.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::High
    Write-Host "Benchmark (PID: $($bench.Id)) 已啟動，開始背景中斷監控..."

    # 背景監控核心 2-7 的中斷佔比 (每 5 秒取樣一次)
    $interruptLog = "$log_path\interrupt_stats.log"
    "Time, Core, InterruptTime(%)" | Out-File $interruptLog
    $monitorTask = Start-Job -ScriptBlock {
        param($logFile)
        while($true) {
            $samples = (Get-Counter "\Processor(*)\% Interrupt Time").CounterSamples | Where-Object { $_.InstanceName -match "^[2-7]$" }
            $timestamp = Get-Date -Format "HH:mm:ss"
            foreach($s in $samples) {
                "$timestamp, Core $($s.InstanceName), $($s.CookedValue.ToString('F2'))" | Out-File $logFile -Append
            }
            Start-Sleep -Seconds 5
        }
    } -ArgumentList $interruptLog

    $bench.WaitForExit()
    Stop-Job $monitorTask
    Remove-Job $monitorTask

    # --- 6. 抓取內部指標數據 (追加分析) ---
    Write-Host "正在從接口抓取內部飽和度與 TPS 指標..."
    try {
        $saturation = Invoke-RestMethod -Uri "http://localhost:8082/api/test/metrics/saturation" -ErrorAction Stop
        $tps = Invoke-RestMethod -Uri "http://localhost:8082/api/test/metrics/tps" -ErrorAction Stop
        $saturation | ConvertTo-Json -Depth 10 | Out-File "$log_path\internal_saturation.json"
        $tps | ConvertTo-Json -Depth 10 | Out-File "$log_path\internal_tps.json"
        Write-Host "內部指標抓取成功，已存至 $log_path"
    } catch {
        Write-Warning "抓取內部指標失敗: $($_.Exception.Message)"
    }

} finally {
    Write-Host ">>> [OS] 還原系統環境、網卡省電設定並清理進程..."
    if ([PSObject].Assembly.GetType("HighPrecisionTimer")) {
        [HighPrecisionTimer]::ResetResolution()
    }
    if ($currentScheme) { powercfg /setactive $currentScheme }
    
    # 還原網卡屬性
    if ($originalCoalescing) { Set-NetAdapterAdvancedProperty -Name $nicName -DisplayName "Packet Coalescing" -DisplayValue $originalCoalescing -ErrorAction SilentlyContinue }
    if ($originalMimo) { Set-NetAdapterAdvancedProperty -Name $nicName -DisplayName "MIMO Power Save Mode" -DisplayValue $originalMimo -ErrorAction SilentlyContinue }
    
    # 確保關閉所有啟動的服務
    if ($driver -and !$driver.HasExited) { Stop-Process -Id $driver.Id -Force }
    if ($e -and !$e.HasExited) { Stop-Process -Id $e.Id -Force }
    if ($g -and !$g.HasExited) { Stop-Process -Id $g.Id -Force }

    Write-Host "分析結果已輸出至 $log_path\benchmark_result.log"
}
