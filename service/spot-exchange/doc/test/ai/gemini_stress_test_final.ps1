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

# Windows Search 服務記錄（實測為 P12 DPC 主要噪音源，壓測時暫停，finally 還原）
$wsearchStatus = (Get-Service WSearch -ErrorAction SilentlyContinue).Status
$wsearchStartType = (Get-Service WSearch -ErrorAction SilentlyContinue).StartType

# 安全清理函數
function Safe-Remove($path) {
    if (-not [string]::IsNullOrWhiteSpace($path) -and (Test-Path $path) -and $path -like "*open.vincentf13*") {
        Write-Host "正在安全清理目錄：「**$path**」..."
        Remove-Item $path -Recurse -Force
    }
}

try {
    Write-Host ">>> [OS] 開啟高精度時鐘、清理實體記憶體、關閉網卡省電與封包合併、停用網路限流、切換至卓越效能..."
    [HighPrecisionTimer]::SetMaxResolution()
    [MemoryCompactor]::PurgeStandbyList() | Out-Null
    powercfg /setactive $ultimateScheme

    # 停用網路限流器 (NetworkThrottlingIndex=0xFFFFFFFF, SystemResponsiveness=0)
    Set-ItemProperty -Path $mmProfilePath -Name "NetworkThrottlingIndex" -Value 0xFFFFFFFF -ErrorAction SilentlyContinue
    Set-ItemProperty -Path $mmProfilePath -Name "SystemResponsiveness" -Value 0 -ErrorAction SilentlyContinue

    # 關閉網卡干擾屬性
    if ($originalCoalescing) { Set-NetAdapterAdvancedProperty -Name $nicName -DisplayName "Packet Coalescing" -DisplayValue "Disabled" -ErrorAction SilentlyContinue }
    if ($originalMimo) { Set-NetAdapterAdvancedProperty -Name $nicName -DisplayName "MIMO Power Save Mode" -DisplayValue "No SMPS" -ErrorAction SilentlyContinue }

    # 暫停 Windows Search（實測為 P12 DPC 主要噪音源，壓測完 finally 還原）
    if ($wsearchStatus -eq 'Running') {
        Write-Host ">>> [OS] 暫停 Windows Search 服務（壓測完還原）..."
        Stop-Service WSearch -Force -ErrorAction SilentlyContinue
    }

    # 確保日誌目錄存在
    if (!(Test-Path $log_path)) { New-Item -ItemType Directory -Force $log_path }

    # --- 1. 強制清理舊環境 ---
    Write-Host "正在停止舊 Java 進程並清理共享記憶體..."
    Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Seconds 3

    Safe-Remove $aeron_dir

    # --- 1.1 建立資料目錄 ---
    if (!(Test-Path $aeron_dir)) { New-Item -ItemType Directory -Force $aeron_dir }
    # 建立 map / wal 目錄避免 IOException
    New-Item -ItemType Directory -Force "$aeron_dir\map" | Out-Null
    New-Item -ItemType Directory -Force "$aeron_dir\wal" | Out-Null

    # --- 2. 執行 Maven 編譯與解壓 ---
    Write-Host "正在執行 Maven 構建與並行提取層級化 Jar..."
    mvn -f "$base_path\pom.xml" clean package -DskipTests -T 3C -pl service/spot-exchange/spot-matching,service/spot-exchange/spot-ws-api -am
    if ($LASTEXITCODE -ne 0) { throw "Maven 編譯失敗 (exit code: $LASTEXITCODE)，中止腳本" }

    $m_t = "$base_path\service\spot-exchange\spot-matching\target"
    $m_dest = "$m_t\extracted"
    $g_t = "$base_path\service\spot-exchange\spot-ws-api\target"
    $g_dest = "$g_t\extracted"

    # 清空舊 extracted 目錄，避免 layertools 不清理殘留檔案造成新舊混用
    Remove-Item -Recurse -Force $m_dest -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force $g_dest -ErrorAction SilentlyContinue

    $job1 = Start-Job -ScriptBlock { param($t, $d) java -Djarmode=layertools -jar "$t\spot-matching.jar" extract --destination "$d/" } -ArgumentList $m_t, $m_dest
    $job2 = Start-Job -ScriptBlock { param($t, $d) java -Djarmode=layertools -jar "$t\spot-ws-api.jar" extract --destination "$d/" } -ArgumentList $g_t, $g_dest
    Wait-Job $job1, $job2 | Out-Null
    Remove-Job $job1, $job2

    # --- 3. 啟動 獨立式 Media Driver (conductor=P11, sender=E4 backoff, receiver=E5 backoff) ---
    # Intel Core Ultra 9 285H 實機拓撲 (coreinfo 驗證)：
    #   P-core  0, 1, 10, 11, 12, 13 (Lion Cove, 3MB private L2 per core, 共享 24MB L3)
    #   E-core  2, 3, 4, 5            (Skymont cluster 1, 共享 4MB L2, 在 L3)
    #   E-core  6, 7, 8, 9            (Skymont cluster 2, 共享 4MB L2, 在 L3)
    #   LP E-core 14, 15              (Skymont LP, SoC tile, 共享 2MB L2, 不在 L3)
    # Conductor 管理 publication position counter，影響 tryClaim 尾端延遲，BusySpin pin P11。
    # IPC-only 下 sender/receiver 本質 idle (~0% CPU)，已用 Backoff 不燒核；
    # 不再 pin，讓它們在 driver mask {4,5,14,15} 內漂移，省 affinity slot 與設定。
    Write-Host "Starting Standalone Media Driver (P11:conductor pinned, sender/receiver drift in E-cluster1+LP)..."
    $aeron_jar_item = Get-ChildItem "$m_dest\dependencies\BOOT-INF\lib\aeron-all-*.jar" | Select-Object -First 1
    $aeron_jar = $aeron_jar_item.FullName

    $driver_args = @(
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
        "-Xms512m", "-Xmx512m",
        "-cp", "$aeron_jar",
        "-Daeron.threading.mode=DEDICATED",
        "-Daeron.conductor.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy",
        "-Daeron.sender.idle.strategy=org.agrona.concurrent.BackoffIdleStrategy",
        "-Daeron.receiver.idle.strategy=org.agrona.concurrent.BackoffIdleStrategy",
        "-Daeron.conductor.affinity=11",
        "-Daeron.term.buffer.length=64m",
        "-Daeron.ipc.term.buffer.length=256m",
        "-Daeron.dir=$aeron_dir",
        "io.aeron.driver.MediaDriver"
    )
    $driver = Start-Process java -ArgumentList $driver_args -PassThru -WindowStyle Hidden -RedirectStandardOutput "$log_path\stdout_driver.log" -RedirectStandardError "$log_path\error_driver.log"
    $driver.ProcessorAffinity = 51200  # P11 (conductor busyspin) + LP14,15 (sender/receiver drift, Backoff idle); E5 移除讓給 gateway netty[0]，E4 移除避免與 gateway netty[3] 競爭
    $driver.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::High

    # --- 4. 併行啟動 Gateway + Matching Engine (全熱 Worker 在真 P-core) ---
    Write-Host "Starting Gateway (P1:gateway-sender P10:gateway-receiver E5:netty[0] E2:netty[1] E3:netty[2] E4:netty[3]) + Matching (P13:MatchingReceiver)..."

    # Gateway: gateway-sender=P1, gateway-receiver=P10, netty[0]=E5, netty[1..3]=E2/E3/E4 (AffinityUtil pool 順序), GC/JIT=E-cluster1+LP14-15
    # 註：實測 P12 的 DPC avg 1.02% max 7.69%（同 P0 等級），P1 avg 0.12% 是最乾淨 P-core。
    # 詳見 doc/DPC_BASELINE.md。busy-spin 線程放 P1，netty[0] 放 E5（接受時鐘較低但 DPC 0.14% 乾淨）。
    # E5 swap 前提：driver/matching mask 已移除 E5，避免跨進程 Aeron / Matching GC drift 干擾 netty[0]。
    # 註：P0 完全排除（Windows DPC 噪音核心），所有 Java process 都不使用 P0
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
        "-Dspot.affinity.cores=1,10,5,2,3,4",
        "-Dspot.wal.bypass=false",
        "-Daeron.driver.enabled=false",
        "-Daeron.dir=$aeron_dir",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.ws.WsApiApp"
    )
    $g = Start-Process java -ArgumentList $gw_args -WorkingDirectory $g_dest -RedirectStandardError "$log_path\error_gw.log" -RedirectStandardOutput "$log_path\stdout_gw.log" -PassThru -WindowStyle Hidden
    $g.ProcessorAffinity = 50238  # P1 (gateway-sender) + P10 (gateway-receiver) + E-cluster1 {2,3,4,5} (netty[0..3] + GC) + LP14,15 (GC); P12 排除讓給 matching GC 獨享；P0 排除避 DPC
    $g.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::RealTime

    # Matching Engine: MatchingReceiver Worker=P13 (離 P0 避 Windows DPC 噪音), GC/JIT=E-cluster1{2,3,4,5}+LP14,15
    $matching_args_file = "$doc_path\jvm\matching-low-latency.args"
    if ($Diagnose) {
        # diagnose 模式：關 PerfDisableSharedMem 讓 jcmd 可 attach matching 進程
        $matching_args_file = "$log_path\matching-diagnose.args"
        (Get-Content "$doc_path\jvm\matching-low-latency.args") -replace '^\s*-XX:\+PerfDisableSharedMem', '# -XX:+PerfDisableSharedMem  # disabled for jcmd attach' | Set-Content $matching_args_file
    }
    $matching_jfr_args = if ($Diagnose) { @("-XX:FlightRecorderOptions=stackdepth=64") } else { @() }
    $matching_args = @(
        "@$matching_args_file"
    ) + $matching_jfr_args + @(
        "-Dspot.affinity.cores=13",
        "-Daeron.driver.enabled=false",
        "-Daeron.dir=$aeron_dir",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.matching.MatchingApp"
    )
    $e = Start-Process java -ArgumentList $matching_args -WorkingDirectory $m_dest -RedirectStandardError "$log_path\error_matching.log" -RedirectStandardOutput "$log_path\stdout_matching.log" -PassThru -WindowStyle Hidden
    $e.ProcessorAffinity = 61440  # P12 + P13 + LP14,15; P13 pin matching-receiver, P12 給 ZGC concurrent drift (P12 DPC 已被 WSearch 處理), E5 移除完全讓給 gateway netty[0], E2/E3/E4 排除避免干擾 gateway netty[1..3]
    $e.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::RealTime

    Write-Host "等待服務啟動 (12s)..."
    Start-Sleep -Seconds 12

    # --- 預熱 ZGC：短暫高壓跑 15 秒，強制 ZGC Warmup 在正式壓測前完成 ---
    Write-Host "Pre-warming ZGC (15s burst at 60K/sec)..."
    $preWarm = Start-Process java -ArgumentList @(
        "@$doc_path\jvm\benchmark-low-latency.args",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.ws.benchmark.BenchmarkTool",
        "60000", "1", "15"
    ) -WorkingDirectory $g_dest -PassThru -WindowStyle Hidden -RedirectStandardOutput "$log_path\prewarm_result.log"
    $preWarm.ProcessorAffinity = 960  # E-cluster2 {6,7,8,9} (benchmark 獨享，整個 4MB L2)
    $preWarm.WaitForExit()
    Write-Host "Pre-warm 完成，等待穩定 (3s)..."
    Start-Sleep -Seconds 3

    # --- 6. 啟動 Java Benchmark (E-cluster2 {6-9} 整個 4 核獨享，獨佔 4MB L2) ---
    Write-Host "Starting Java Benchmark (E-cluster2: E6-E9)..."
    $bench_args = @(
        "@$doc_path\jvm\benchmark-low-latency.args",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.ws.benchmark.BenchmarkTool",
        "60000",   # 6萬 orders/sec (跨所有連線總和)
        "20",      # 20秒測量
        "45",      # 45秒預熱 (確保 ZGC Old cycle 在 measure 前觸發穩態)
        "4"        # 4 條 WS 連線，每條 15K/sec，分散 per-channel 出口瓶頸
    )
    $bench = Start-Process java -ArgumentList $bench_args -WorkingDirectory $g_dest -PassThru -NoNewWindow -RedirectStandardOutput "$log_path\benchmark_result.log"
    $bench.ProcessorAffinity = 960 # E-cluster2 {6,7,8,9} (benchmark 獨享)
    $bench.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::High
    Write-Host "Benchmark (PID: $($bench.Id)) 已啟動"

    # --- Warmup 結束前重置內部指標 (排除 warmup 數據污染) ---
    $resetJob = Start-Job -ScriptBlock {
        Start-Sleep -Seconds 43  # warmup 45s，提前 2s 重置
        try { Invoke-RestMethod -Uri "http://localhost:8082/api/test/metrics/reset" -Method POST -ErrorAction Stop | Out-Null }
        catch { } # 靜默失敗
    }

    # --- [Diagnose] JFR allocation profiling on matching (measure phase only) ---
    if ($Diagnose) {
        # 必須用與 target JVM 同版本的 jcmd / jps（matching 跑 JDK 23）。
        # 也必須用 jps 找真實 PID — Start-Process java 拿到的是 launcher PID，真 JVM 是子進程。
        $jcmdPath = "C:\Program Files\Java\jdk-23\bin\jcmd.exe"
        $jpsPath  = "C:\Program Files\Java\jdk-23\bin\jps.exe"
        $jfrJob = Start-Job -ScriptBlock {
            param($jcmdExe, $jpsExe, $jfrFile, $debugLog)
            Start-Sleep -Seconds 45  # 等 warmup 結束 → 進入 measure
            $matchingPid = (& $jpsExe -l | Select-String "MatchingApp" | ForEach-Object { ($_ -split '\s+')[0] }) | Select-Object -First 1
            "[$(Get-Date -Format HH:mm:ss)] Resolved matching PID via jps: $matchingPid" | Out-File $debugLog -Append
            if (-not $matchingPid) { "no matching PID found" | Out-File $debugLog -Append; return }
            "[$(Get-Date -Format HH:mm:ss)] JFR.start..." | Out-File $debugLog -Append
            $startOut = & $jcmdExe $matchingPid JFR.start name=alloc settings=profile 2>&1
            $startOut | Out-File $debugLog -Append
            Start-Sleep -Seconds 22  # measure 20s + 2s buffer
            "[$(Get-Date -Format HH:mm:ss)] JFR.stop..." | Out-File $debugLog -Append
            $stopOut = & $jcmdExe $matchingPid JFR.stop name=alloc filename=$jfrFile 2>&1
            $stopOut | Out-File $debugLog -Append
        } -ArgumentList $jcmdPath, $jpsPath, "$log_path\matching-alloc.jfr", "$log_path\jfr_debug.log"
        Write-Host ">>> [Diagnose] JFR alloc profile 已排程 (jcmd: $jcmdPath)"
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
                $int = (Get-Counter "\Processor(*)\% Interrupt Time").CounterSamples | Where-Object { $_.InstanceName -match "^[0-5]$" }
                $dpc = (Get-Counter "\Processor(*)\% DPC Time").CounterSamples | Where-Object { $_.InstanceName -match "^[0-5]$" }
                $priv = (Get-Counter "\Processor(*)\% Privileged Time").CounterSamples | Where-Object { $_.InstanceName -match "^[0-5]$" }
                $cs = (Get-Counter "\System\Context Switches/sec").CounterSamples[0].CookedValue
                foreach($core in @("0","1","2","3","4","5")) {
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
    if ($jfrJob) { Wait-Job $jfrJob -Timeout 30; Receive-Job $jfrJob | Out-String | Out-File "$log_path\jfr_debug.log" -Append; Remove-Job $jfrJob -ErrorAction SilentlyContinue }

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
        Write-Host "正在抓取 heap histogram (gw + matching)..."
        # 必須與 target JVM (JDK 23) 同版本，default PATH 的 jps/jcmd 是 JDK 21 會 attach 失敗
        $jcmd23 = "C:\Program Files\Java\jdk-23\bin\jcmd.exe"
        $jps23  = "C:\Program Files\Java\jdk-23\bin\jps.exe"
        try {
            $jpsOutput = & $jps23 -l
            $gwPid = ($jpsOutput | Select-String "WsApiApp" | ForEach-Object { ($_ -split '\s+')[0] }) | Select-Object -First 1
            $matchingPid = ($jpsOutput | Select-String "MatchingApp" | ForEach-Object { ($_ -split '\s+')[0] }) | Select-Object -First 1
            if ($gwPid) {
                & $jcmd23 $gwPid GC.class_histogram -all 2>&1 | Select-Object -First 80 | Out-File "$log_path\heap_gw_measure.log"
                Write-Host "Heap histogram (gw, PID: $gwPid) -> heap_gw_measure.log"
            }
            if ($matchingPid) {
                & $jcmd23 $matchingPid GC.class_histogram -all 2>&1 | Select-Object -First 80 | Out-File "$log_path\heap_matching_measure.log"
                Write-Host "Heap histogram (matching, PID: $matchingPid) -> heap_matching_measure.log"
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

    # 還原 Windows Search 服務
    if ($wsearchStatus -eq 'Running' -and (Get-Service WSearch -ErrorAction SilentlyContinue).Status -ne 'Running') {
        Write-Host ">>> [OS] 還原 Windows Search 服務..."
        Start-Service WSearch -ErrorAction SilentlyContinue
    }

    # 確保關閉所有啟動的服務
    if ($driver -and !$driver.HasExited) { Stop-Process -Id $driver.Id -Force }
    if ($e -and !$e.HasExited) { Stop-Process -Id $e.Id -Force }
    if ($g -and !$g.HasExited) { Stop-Process -Id $g.Id -Force }

    Write-Host "分析結果已輸出至 $log_path\benchmark_result.log"
}
