# ==============================================================================
# Aeron 交易系統自動化部署與啟動腳本
# 用法：
#   .\gemini_stress_test_final.ps1              # 純壓測 (最低延遲)
#   .\gemini_stress_test_final.ps1 -Diagnose    # 壓測 + AI 診斷工具
# ==============================================================================
param([switch]$Diagnose)

$base_path = "C:\iProject\open.vincentf13"
$aeron_dir = "$base_path\data\spot-exchange"
$doc_path  = "$base_path\service\spot-exchange\doc"
$log_path  = "$doc_path\test\log"

# --- 0. 系統環境優化 C# 嵌入 ---
$NativeCode = @"
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

/// <summary>
/// 清空 Windows Standby List，釋放快取佔用的物理記憶體頁框，
/// 讓 OS 有最大機會提供連續大頁 (Large Pages) 給 JVM。
/// 需要管理員權限；非管理員時 graceful 失敗不影響後續流程。
/// </summary>
public class MemoryCompactor {
    [DllImport("ntdll.dll")]
    private static extern int NtSetSystemInformation(int InfoClass, ref int Info, int Length);

    [DllImport("advapi32.dll", SetLastError = true)]
    private static extern bool OpenProcessToken(IntPtr ProcessHandle, uint DesiredAccess, out IntPtr TokenHandle);

    [DllImport("advapi32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern bool LookupPrivilegeValue(string lpSystemName, string lpName, out long lpLuid);

    [DllImport("advapi32.dll", SetLastError = true)]
    private static extern bool AdjustTokenPrivileges(IntPtr TokenHandle, bool DisableAllPrivileges,
        ref TOKEN_PRIVILEGES NewState, int BufferLength, IntPtr PreviousState, IntPtr ReturnLength);

    [DllImport("kernel32.dll")]
    private static extern IntPtr GetCurrentProcess();

    [StructLayout(LayoutKind.Sequential, Pack = 4)]
    private struct TOKEN_PRIVILEGES {
        public int PrivilegeCount;
        public long Luid;
        public int Attributes;
    }

    private const uint TOKEN_ADJUST_PRIVILEGES = 0x0020;
    private const uint TOKEN_QUERY = 0x0008;
    private const int SE_PRIVILEGE_ENABLED = 0x00000002;
    private const int SystemMemoryListInformation = 80;
    private const int MemoryEmptyWorkingSets = 1;
    private const int MemoryFlushModifiedList = 2;
    private const int MemoryPurgeStandbyList = 4;

    /// <summary>
    /// 三階段物理記憶體整合：
    ///   1. EmptyWorkingSets  — 將所有進程的 Working Set 頁框釋回 Standby/Modified List
    ///   2. FlushModifiedList — 將 Modified 頁框寫入磁碟後釋放為 Free
    ///   3. PurgeStandbyList  — 將 Standby 頁框直接釋放為 Free
    /// 三步完成後，OS 可用的連續物理頁框最大化，Large Pages 分配成功率最高。
    /// </summary>
    public static bool PurgeStandbyList() {
        try {
            IntPtr token;
            if (!OpenProcessToken(GetCurrentProcess(), TOKEN_ADJUST_PRIVILEGES | TOKEN_QUERY, out token))
                return false;

            TOKEN_PRIVILEGES tp = new TOKEN_PRIVILEGES();
            tp.PrivilegeCount = 1;
            tp.Attributes = SE_PRIVILEGE_ENABLED;
            if (!LookupPrivilegeValue(null, "SeProfileSingleProcessPrivilege", out tp.Luid))
                return false;

            AdjustTokenPrivileges(token, false, ref tp, 0, IntPtr.Zero, IntPtr.Zero);

            // Phase 1: 清空所有進程 Working Set → 頁框移入 Standby/Modified
            int cmd1 = MemoryEmptyWorkingSets;
            NtSetSystemInformation(SystemMemoryListInformation, ref cmd1, sizeof(int));

            // Phase 2: 將 Modified 頁框刷入磁碟 → 釋放為 Free
            int cmd2 = MemoryFlushModifiedList;
            NtSetSystemInformation(SystemMemoryListInformation, ref cmd2, sizeof(int));

            // Phase 3: 清空 Standby List → 釋放為 Free
            int cmd3 = MemoryPurgeStandbyList;
            int result = NtSetSystemInformation(SystemMemoryListInformation, ref cmd3, sizeof(int));
            return result == 0;
        } catch {
            return false;
        }
    }
}
"@
if (-not ([PSObject].Assembly.GetType("HighPrecisionTimer"))) {
    Add-Type -TypeDefinition $NativeCode
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

# 網路限流器記錄與切換
$mmProfilePath = "HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Multimedia\SystemProfile"
$originalThrottling = (Get-ItemProperty -Path $mmProfilePath -Name "NetworkThrottlingIndex" -ErrorAction SilentlyContinue).NetworkThrottlingIndex
$originalResponsiveness = (Get-ItemProperty -Path $mmProfilePath -Name "SystemResponsiveness" -ErrorAction SilentlyContinue).SystemResponsiveness

# 安全清理函數
function Safe-Remove($path) {
    if (-not [string]::IsNullOrWhiteSpace($path) -and (Test-Path $path) -and $path -like "*open.vincentf13*") {
        Write-Host "正在安全清理目錄：「**$path**」..."
        Remove-Item $path -Recurse -Force
    }
}

try {
    Write-Host ">>> [OS] 開啟高精度時鐘、關閉網卡省電與封包合併、停用網路限流、切換至卓越效能..."
    [HighPrecisionTimer]::SetMaxResolution()
    powercfg /setactive $ultimateScheme

    # 停用網路限流器 (NetworkThrottlingIndex=0xFFFFFFFF, SystemResponsiveness=0)
    Set-ItemProperty -Path $mmProfilePath -Name "NetworkThrottlingIndex" -Value 0xFFFFFFFF -ErrorAction SilentlyContinue
    Set-ItemProperty -Path $mmProfilePath -Name "SystemResponsiveness" -Value 0 -ErrorAction SilentlyContinue

    # 關閉網卡干擾屬性
    if ($originalCoalescing) { Set-NetAdapterAdvancedProperty -Name $nicName -DisplayName "Packet Coalescing" -DisplayValue "Disabled" -ErrorAction SilentlyContinue }
    if ($originalMimo) { Set-NetAdapterAdvancedProperty -Name $nicName -DisplayName "MIMO Power Save Mode" -DisplayValue "No SMPS" -ErrorAction SilentlyContinue }

    # 確保日誌目錄存在
    if (!(Test-Path $log_path)) { New-Item -ItemType Directory -Force $log_path }

    # --- 1. 強制清理舊環境 ---
    Write-Host "正在停止舊 Java 進程並清理共享記憶體..."
    Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Seconds 3

    Safe-Remove $aeron_dir

    # --- 1.1 檔案預分配 (消除磁碟擴展延遲) ---
    if (!(Test-Path $aeron_dir)) { New-Item -ItemType Directory -Force $aeron_dir }
    Write-Host "正在執行磁碟空間預分配 (WAL & State: 1GB)..."
    fsutil file createnew "$aeron_dir\wal-spot-exchange.dat" 1073741824 | Out-Null
    fsutil file createnew "$aeron_dir\matching-engine-state.dat" 1073741824 | Out-Null
    # 建立 map 目錄避免 IOException
    New-Item -ItemType Directory -Force "$aeron_dir\map" | Out-Null

    # --- 2. 執行 Maven 編譯與解壓 ---
    Write-Host "正在執行 Maven 構建與並行提取層級化 Jar..."
    mvn -f "$base_path\pom.xml" clean package -DskipTests -T 3C -pl service/spot-exchange/spot-matching,service/spot-exchange/spot-ws-api -am
    if ($LASTEXITCODE -ne 0) { throw "Maven 編譯失敗 (exit code: $LASTEXITCODE)，中止腳本" }

    $m_t = "$base_path\service\spot-exchange\spot-matching\target"
    $m_dest = "$m_t\extracted"
    $g_t = "$base_path\service\spot-exchange\spot-ws-api\target"
    $g_dest = "$g_t\extracted"

    $job1 = Start-Job -ScriptBlock { param($t, $d) java -Djarmode=layertools -jar "$t\spot-matching.jar" extract --destination "$d/" } -ArgumentList $m_t, $m_dest
    $job2 = Start-Job -ScriptBlock { param($t, $d) java -Djarmode=layertools -jar "$t\spot-ws-api.jar" extract --destination "$d/" } -ArgumentList $g_t, $g_dest
    Wait-Job $job1, $job2 | Out-Null
    Remove-Job $job1, $job2

    # --- 3. 啟動 獨立式 Media Driver (專屬 12, 13 + 共享 0, 1, 14, 15 | Mask: 61443) ---
    Write-Host "Starting Standalone Media Driver..."
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
        "-Daeron.conductor.affinity=7",
        "-Daeron.sender.affinity=12",
        "-Daeron.receiver.affinity=13",
        "-Daeron.term.buffer.length=64m",
        "-Daeron.ipc.term.buffer.length=256m",
        "-Daeron.dir=$aeron_dir",
        "io.aeron.driver.MediaDriver"
    )
    $driver = Start-Process java -ArgumentList $driver_args -PassThru -WindowStyle Hidden
    $driver.ProcessorAffinity = 61571  # 核心 0, 1, 7, 12, 13, 14, 15 (conductor 移至專屬 P-core 7)
    $driver.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::High

    # --- 4. 併行啟動 Gateway + Matching Engine ---
    Write-Host "Starting Gateway (WS-API) (4GB, P-Cores 3-7) + Matching Engine (4GB, P-Core 2)..."

    # Gateway
    $gw_args_file = "$doc_path\jvm\ws-api-throughput.args"
    $gw_diagnose_flags = @()
    if ($Diagnose) {
        $gw_args_file = "$log_path\ws-api-diagnose.args"
        (Get-Content "$doc_path\jvm\ws-api-throughput.args") -replace '^\s*-XX:\+PerfDisableSharedMem', '# -XX:+PerfDisableSharedMem  # disabled for jcmd attach' | Set-Content $gw_args_file
        $gw_diagnose_flags = @("-Dspot.diagnose=true")
    }
    $gw_args = @(
        "@$gw_args_file"
    ) + $gw_diagnose_flags + @(
        "-Xlog:gc*:file=$log_path\gc_gw.log:time,uptime,level,tags",
        "-Dspot.affinity.cores=3,4,5,6",
        "-Dspot.wal.bypass=true",
        "-Daeron.driver.enabled=false",
        "-Daeron.dir=$aeron_dir",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.ws.WsApiApp"
    )
    $g = Start-Process java -ArgumentList $gw_args -WorkingDirectory $g_dest -RedirectStandardError "$log_path\error_gw.log" -RedirectStandardOutput "$log_path\stdout_gw.log" -PassThru -WindowStyle Hidden
    $g.ProcessorAffinity = 49275 # 核心 3-6 (worker) + 共享 0, 1, 14, 15 (GC/JIT)
    $g.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::RealTime

    # Matching Engine
    $matching_args = @(
        "@$doc_path\jvm\matching-low-latency.args",
        "-Dspot.affinity.cores=2",
        "-Daeron.driver.enabled=false",
        "-Daeron.dir=$aeron_dir",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.matching.MatchingApp"
    )
    $e = Start-Process java -ArgumentList $matching_args -WorkingDirectory $m_dest -RedirectStandardError "$log_path\error_matching.log" -RedirectStandardOutput "$log_path\stdout_matching.log" -PassThru -WindowStyle Hidden
    $e.ProcessorAffinity = 49159 # 核心 2 (worker) + 共享 0, 1, 14, 15 (GC/JIT)
    $e.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::RealTime

    Write-Host "等待服務啟動 (20s)..."
    Start-Sleep -Seconds 20

    # --- 預熱 ZGC：短暫高壓跑 15 秒，強制 ZGC Warmup 在正式壓測前完成 ---
    Write-Host "Pre-warming ZGC (15s burst at 60K/sec)..."
    $preWarm = Start-Process java -ArgumentList @(
        "@$doc_path\jvm\benchmark-low-latency.args",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.ws.benchmark.BenchmarkTool",
        "60000", "1", "25"
    ) -WorkingDirectory $g_dest -PassThru -WindowStyle Hidden -RedirectStandardOutput "$log_path\prewarm_result.log"
    $preWarm.ProcessorAffinity = 3840
    $preWarm.WaitForExit()
    Write-Host "Pre-warm 完成，等待穩定 (5s)..."
    Start-Sleep -Seconds 5

    # --- 6. 啟動 Java Benchmark (與核心 8-11 綁定，避免搶佔 2-7) ---
    Write-Host "Starting Java Benchmark (Cores 8-11)..."
    $bench_args = @(
        "@$doc_path\jvm\benchmark-low-latency.args",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.ws.benchmark.BenchmarkTool",
        "60000",   # 6萬 orders/sec
        "30",      # 30秒測量
        "60"       # 60秒預熱 (ZGC Warmup 已在 pre-warm 階段完成)
    )
    $bench = Start-Process java -ArgumentList $bench_args -WorkingDirectory $g_dest -PassThru -NoNewWindow -RedirectStandardOutput "$log_path\benchmark_result.log"
    $bench.ProcessorAffinity = 3840 # 核心 8, 9, 10, 11
    $bench.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::High
    Write-Host "Benchmark (PID: $($bench.Id)) 已啟動"

    # --- Warmup 結束前重置內部指標 (排除 warmup 數據污染) ---
    $resetJob = Start-Job -ScriptBlock {
        Start-Sleep -Seconds 58  # warmup 60s，提前 2s 重置
        try { Invoke-RestMethod -Uri "http://localhost:8082/api/test/metrics/reset" -Method POST -ErrorAction Stop | Out-Null }
        catch { } # 靜默失敗
    }

    # --- [Diagnose] 背景中斷監控 (僅 -Diagnose 模式，避免影響延遲) ---
    if ($Diagnose) {
        Write-Host ">>> [Diagnose] 啟用 OS 監控 (Interrupt, DPC, Privileged, Context Switches)..."
        $osStatsLog = "$log_path\os_stats.log"
        "Time, Core, Interrupt(%), DPC(%), Privileged(%)" | Out-File $osStatsLog
        $monitorTask = Start-Job -ScriptBlock {
            param($logFile)
            while($true) {
                $timestamp = Get-Date -Format "HH:mm:ss"
                $int = (Get-Counter "\Processor(*)\% Interrupt Time").CounterSamples | Where-Object { $_.InstanceName -match "^[2-6]$" }
                $dpc = (Get-Counter "\Processor(*)\% DPC Time").CounterSamples | Where-Object { $_.InstanceName -match "^[2-6]$" }
                $priv = (Get-Counter "\Processor(*)\% Privileged Time").CounterSamples | Where-Object { $_.InstanceName -match "^[2-6]$" }
                $cs = (Get-Counter "\System\Context Switches/sec").CounterSamples[0].CookedValue
                foreach($core in @("2","3","4","5","6")) {
                    $i = ($int | Where-Object { $_.InstanceName -eq $core }).CookedValue
                    $d = ($dpc | Where-Object { $_.InstanceName -eq $core }).CookedValue
                    $p = ($priv | Where-Object { $_.InstanceName -eq $core }).CookedValue
                    "$timestamp, Core $core, $($i.ToString('F2')), $($d.ToString('F2')), $($p.ToString('F2'))" | Out-File $logFile -Append
                }
                "$timestamp, ContextSwitches/sec, $($cs.ToString('F0'))" | Out-File $logFile -Append
                Start-Sleep -Seconds 5
            }
        } -ArgumentList $osStatsLog
    }

    $bench.WaitForExit()

    # --- 清理背景 Jobs ---
    if ($resetJob) { Wait-Job $resetJob -Timeout 5; Remove-Job $resetJob -ErrorAction SilentlyContinue }
    if ($monitorTask) { Stop-Job $monitorTask; Remove-Job $monitorTask }

    # --- 7. 抓取內部指標 (每次都做，Gateway 還活著) ---
    Write-Host "正在抓取內部指標..."
    try {
        $saturation = Invoke-RestMethod -Uri "http://localhost:8082/api/test/metrics/saturation" -ErrorAction Stop
        $tps = Invoke-RestMethod -Uri "http://localhost:8082/api/test/metrics/tps" -ErrorAction Stop
        $saturation | ConvertTo-Json -Depth 10 | Out-File "$log_path\internal_saturation.json"
        $tps | ConvertTo-Json -Depth 10 | Out-File "$log_path\internal_tps.json"
        Write-Host "內部指標抓取成功"
    } catch {
        Write-Warning "抓取內部指標失敗: $($_.Exception.Message)"
    }

    # --- 8. 抓取診斷數據 (僅 -Diagnose 模式) ---
    if ($Diagnose) {
        Write-Host "正在抓取 heap histogram..."
        try {
            $gwPid = (jps -l | Select-String "WsApiApp" | ForEach-Object { ($_ -split '\s+')[0] }) | Select-Object -First 1
            if ($gwPid) {
                jcmd $gwPid GC.class_histogram -all 2>&1 | Select-Object -First 80 | Out-File "$log_path\heap_gw_measure.log"
                Write-Host "Heap histogram 已存至 heap_gw_measure.log (PID: $gwPid)"
            } else {
                Write-Warning "jps 找不到 WsApiApp 進程"
            }
        } catch {
            Write-Warning "jcmd 失敗: $($_.Exception.Message)"
        }
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

    # 還原網路限流器
    if ($null -ne $originalThrottling) { Set-ItemProperty -Path $mmProfilePath -Name "NetworkThrottlingIndex" -Value $originalThrottling -ErrorAction SilentlyContinue }
    if ($null -ne $originalResponsiveness) { Set-ItemProperty -Path $mmProfilePath -Name "SystemResponsiveness" -Value $originalResponsiveness -ErrorAction SilentlyContinue }

    # 確保關閉所有啟動的服務
    if ($driver -and !$driver.HasExited) { Stop-Process -Id $driver.Id -Force }
    if ($e -and !$e.HasExited) { Stop-Process -Id $e.Id -Force }
    if ($g -and !$g.HasExited) { Stop-Process -Id $g.Id -Force }

    Write-Host "分析結果已輸出至 $log_path\benchmark_result.log"
}
