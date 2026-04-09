# ==============================================================================
# WAL 模式壓測腳本 (基於 gemini_stress_test_final.ps1，啟用 WAL + Diagnose)
# 差異：spot.wal.bypass=false, spot.diagnose=true
# 用法：以管理員身份執行  .\wal_benchmark.ps1
# ==============================================================================

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

            int cmd1 = MemoryEmptyWorkingSets;
            NtSetSystemInformation(SystemMemoryListInformation, ref cmd1, sizeof(int));
            int cmd2 = MemoryFlushModifiedList;
            NtSetSystemInformation(SystemMemoryListInformation, ref cmd2, sizeof(int));
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

# 記錄目前的電源計畫
$schemeOutput = powercfg /getactivescheme
if ($schemeOutput -match "([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})") {
    $currentScheme = $matches[1]
} else {
    $currentScheme = "381b4222-f694-41f0-9685-ff5bb260df2e"
}
$ultimateScheme = "fe8e30e8-5fe7-4e44-b2f9-e7b4495773eb"

# 網卡屬性記錄
$nicName = (Get-NetAdapter | Where-Object Status -eq "Up" | Select-Object -First 1).Name
$originalCoalescing = (Get-NetAdapterAdvancedProperty -Name $nicName -DisplayName "Packet Coalescing" -ErrorAction SilentlyContinue).DisplayValue
$originalMimo = (Get-NetAdapterAdvancedProperty -Name $nicName -DisplayName "MIMO Power Save Mode" -ErrorAction SilentlyContinue).DisplayValue

# 網路限流器記錄
$mmProfilePath = "HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Multimedia\SystemProfile"
$originalThrottling = (Get-ItemProperty -Path $mmProfilePath -Name "NetworkThrottlingIndex" -ErrorAction SilentlyContinue).NetworkThrottlingIndex
$originalResponsiveness = (Get-ItemProperty -Path $mmProfilePath -Name "SystemResponsiveness" -ErrorAction SilentlyContinue).SystemResponsiveness

function Safe-Remove($path) {
    if (-not [string]::IsNullOrWhiteSpace($path) -and (Test-Path $path) -and $path -like "*open.vincentf13*") {
        Write-Host "正在安全清理目錄：「$path」..."
        Remove-Item $path -Recurse -Force
    }
}

try {
    Write-Host ">>> [OS] 開啟高精度時鐘、關閉網卡省電、停用網路限流、切換卓越效能..."
    [HighPrecisionTimer]::SetMaxResolution()
    powercfg /setactive $ultimateScheme

    Set-ItemProperty -Path $mmProfilePath -Name "NetworkThrottlingIndex" -Value 0xFFFFFFFF -ErrorAction SilentlyContinue
    Set-ItemProperty -Path $mmProfilePath -Name "SystemResponsiveness" -Value 0 -ErrorAction SilentlyContinue

    if ($originalCoalescing) { Set-NetAdapterAdvancedProperty -Name $nicName -DisplayName "Packet Coalescing" -DisplayValue "Disabled" -ErrorAction SilentlyContinue }
    if ($originalMimo) { Set-NetAdapterAdvancedProperty -Name $nicName -DisplayName "MIMO Power Save Mode" -DisplayValue "No SMPS" -ErrorAction SilentlyContinue }

    if (!(Test-Path $log_path)) { New-Item -ItemType Directory -Force $log_path }

    # --- 1. 強制清理舊環境 ---
    Write-Host "正在停止舊 Java 進程並清理共享記憶體..."
    Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Seconds 3

    Safe-Remove $aeron_dir

    # --- 1.1 檔案預分配 ---
    if (!(Test-Path $aeron_dir)) { New-Item -ItemType Directory -Force $aeron_dir }
    Write-Host "正在執行磁碟空間預分配 (WAL & State: 1GB)..."
    fsutil file createnew "$aeron_dir\wal-spot-exchange.dat" 1073741824 | Out-Null
    fsutil file createnew "$aeron_dir\matching-engine-state.dat" 1073741824 | Out-Null
    New-Item -ItemType Directory -Force "$aeron_dir\map" | Out-Null

    # --- 1.2 WAL 目錄預建立 (Chronicle Queue 需要) ---
    New-Item -ItemType Directory -Force "$aeron_dir\wal" | Out-Null

    # --- 2. Maven 編譯 ---
    Write-Host "正在執行 Maven 構建..."
    mvn -f "$base_path\pom.xml" clean package -DskipTests -T 3C -pl service/spot-exchange/spot-matching,service/spot-exchange/spot-ws-api -am
    if ($LASTEXITCODE -ne 0) { throw "Maven 編譯失敗 (exit code: $LASTEXITCODE)" }

    $m_t = "$base_path\service\spot-exchange\spot-matching\target"
    $m_dest = "$m_t\extracted"
    $g_t = "$base_path\service\spot-exchange\spot-ws-api\target"
    $g_dest = "$g_t\extracted"

    $job1 = Start-Job -ScriptBlock { param($t, $d) java -Djarmode=layertools -jar "$t\spot-matching.jar" extract --destination "$d/" } -ArgumentList $m_t, $m_dest
    $job2 = Start-Job -ScriptBlock { param($t, $d) java -Djarmode=layertools -jar "$t\spot-ws-api.jar" extract --destination "$d/" } -ArgumentList $g_t, $g_dest
    Wait-Job $job1, $job2 | Out-Null
    Remove-Job $job1, $job2

    # --- 3. 啟動獨立式 Media Driver ---
    Write-Host "Starting Standalone Media Driver (P5:conductor, E6:sender, E7:receiver)..."
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
        "-Daeron.conductor.affinity=5",
        "-Daeron.sender.affinity=6",
        "-Daeron.receiver.affinity=7",
        "-Daeron.term.buffer.length=64m",
        "-Daeron.ipc.term.buffer.length=256m",
        "-Daeron.dir=$aeron_dir",
        "io.aeron.driver.MediaDriver"
    )
    $driver = Start-Process java -ArgumentList $driver_args -PassThru -WindowStyle Hidden
    $driver.ProcessorAffinity = 50208
    $driver.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::High

    # --- 4. 啟動 Gateway (WAL 模式 + Diagnose) + Matching Engine ---
    Write-Host "Starting Gateway [WAL MODE + DIAGNOSE] (P1:WalSender P2:ReportRecv P3-4:Netty) + Matching Engine (P0)..."

    # Gateway: WAL 模式 (spot.wal.bypass=false) + 診斷模式 (spot.diagnose=false)
    $gw_args_file = "$log_path\ws-api-diagnose.args"
    (Get-Content "$doc_path\jvm\ws-api-throughput.args") -replace '^\s*-XX:\+PerfDisableSharedMem', '# -XX:+PerfDisableSharedMem  # disabled for jcmd attach' | Set-Content $gw_args_file

    $gw_args = @(
        "@$gw_args_file",
        "-Dspot.diagnose=false",
        "-Xlog:gc*:file=$log_path\gc_gw_wal.log:time,uptime,level,tags",
        "-Dspot.affinity.cores=1,2,3,4,5,6",
        "-Dspot.wal.bypass=false",
        "-Daeron.driver.enabled=false",
        "-Daeron.dir=$aeron_dir",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.ws.WsApiApp"
    )
    $g = Start-Process java -ArgumentList $gw_args -WorkingDirectory $g_dest -RedirectStandardError "$log_path\error_gw_wal.log" -RedirectStandardOutput "$log_path\stdout_gw_wal.log" -PassThru -WindowStyle Hidden
    $g.ProcessorAffinity = 52254
    $g.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::RealTime

    # Matching Engine
    $matching_args = @(
        "@$doc_path\jvm\matching-low-latency.args",
        "-Dspot.affinity.cores=0",
        "-Daeron.driver.enabled=false",
        "-Daeron.dir=$aeron_dir",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.matching.MatchingApp"
    )
    $e = Start-Process java -ArgumentList $matching_args -WorkingDirectory $m_dest -RedirectStandardError "$log_path\error_matching_wal.log" -RedirectStandardOutput "$log_path\stdout_matching_wal.log" -PassThru -WindowStyle Hidden
    $e.ProcessorAffinity = 49921
    $e.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::RealTime

    Write-Host "等待服務啟動 (20s)..."
    Start-Sleep -Seconds 20

    # --- 5. 預熱 ZGC ---
    Write-Host "Pre-warming ZGC (15s burst at 60K/sec)..."
    $preWarm = Start-Process java -ArgumentList @(
        "@$doc_path\jvm\benchmark-low-latency.args",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.ws.benchmark.BenchmarkTool",
        "60000", "1", "25"
    ) -WorkingDirectory $g_dest -PassThru -WindowStyle Hidden -RedirectStandardOutput "$log_path\prewarm_result_wal.log"
    $preWarm.ProcessorAffinity = 15360
    $preWarm.WaitForExit()
    Write-Host "Pre-warm 完成，等待穩定 (5s)..."
    Start-Sleep -Seconds 5

    # --- 6. 啟動 Benchmark ---
    Write-Host "Starting Java Benchmark [WAL MODE] (E-Cores 10-13)..."
    $bench_args = @(
        "@$doc_path\jvm\benchmark-low-latency.args",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.ws.benchmark.BenchmarkTool",
        "60000",   # 6萬 orders/sec
        "30",      # 30秒測量
        "60"       # 60秒預熱
    )
    $bench = Start-Process java -ArgumentList $bench_args -WorkingDirectory $g_dest -PassThru -NoNewWindow -RedirectStandardOutput "$log_path\benchmark_result_wal.log"
    $bench.ProcessorAffinity = 15360
    $bench.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::High
    Write-Host "Benchmark (PID: $($bench.Id)) 已啟動 — WAL 模式"

    # --- Warmup 結束前重置指標 ---
    $resetJob = Start-Job -ScriptBlock {
        Start-Sleep -Seconds 58
        try { Invoke-RestMethod -Uri "http://localhost:8082/api/test/metrics/reset" -Method POST -ErrorAction Stop | Out-Null }
        catch { }
    }

    $bench.WaitForExit()

    # --- 清理背景 Jobs ---
    if ($resetJob) { Wait-Job $resetJob -Timeout 5; Remove-Job $resetJob -ErrorAction SilentlyContinue }

    # --- 7. 抓取內部指標 ---
    Write-Host "正在抓取內部指標 (WAL 模式)..."
    try {
        $saturation = Invoke-RestMethod -Uri "http://localhost:8082/api/test/metrics/saturation" -ErrorAction Stop
        $tps = Invoke-RestMethod -Uri "http://localhost:8082/api/test/metrics/tps" -ErrorAction Stop
        $saturation | ConvertTo-Json -Depth 10 | Out-File "$log_path\internal_saturation_wal.json"
        $tps | ConvertTo-Json -Depth 10 | Out-File "$log_path\internal_tps_wal.json"
        Write-Host "內部指標已存至 internal_saturation_wal.json / internal_tps_wal.json"
    } catch {
        Write-Warning "抓取內部指標失敗: $($_.Exception.Message)"
    }

    # --- 8. Heap histogram ---
    Write-Host "正在抓取 heap histogram..."
    try {
        $gwPid = (jps -l | Select-String "WsApiApp" | ForEach-Object { ($_ -split '\s+')[0] }) | Select-Object -First 1
        if ($gwPid) {
            jcmd $gwPid GC.class_histogram -all 2>&1 | Select-Object -First 80 | Out-File "$log_path\heap_gw_wal.log"
            Write-Host "Heap histogram 已存至 heap_gw_wal.log (PID: $gwPid)"
        }
    } catch {
        Write-Warning "jcmd 失敗: $($_.Exception.Message)"
    }

} finally {
    Write-Host ">>> [OS] 還原系統環境並清理進程..."
    if ([PSObject].Assembly.GetType("HighPrecisionTimer")) {
        [HighPrecisionTimer]::ResetResolution()
    }
    if ($currentScheme) { powercfg /setactive $currentScheme }

    if ($originalCoalescing) { Set-NetAdapterAdvancedProperty -Name $nicName -DisplayName "Packet Coalescing" -DisplayValue $originalCoalescing -ErrorAction SilentlyContinue }
    if ($originalMimo) { Set-NetAdapterAdvancedProperty -Name $nicName -DisplayName "MIMO Power Save Mode" -DisplayValue $originalMimo -ErrorAction SilentlyContinue }

    if ($null -ne $originalThrottling) { Set-ItemProperty -Path $mmProfilePath -Name "NetworkThrottlingIndex" -Value $originalThrottling -ErrorAction SilentlyContinue }
    if ($null -ne $originalResponsiveness) { Set-ItemProperty -Path $mmProfilePath -Name "SystemResponsiveness" -Value $originalResponsiveness -ErrorAction SilentlyContinue }

    if ($driver -and !$driver.HasExited) { Stop-Process -Id $driver.Id -Force }
    if ($e -and !$e.HasExited) { Stop-Process -Id $e.Id -Force }
    if ($g -and !$g.HasExited) { Stop-Process -Id $g.Id -Force }

    Write-Host ""
    Write-Host "=========================================="
    Write-Host "WAL 模式壓測完成！結果檔案："
    Write-Host "  - benchmark_result_wal.log  (E2E 延遲分佈)"
    Write-Host "  - internal_saturation_wal.json (指標/延遲分解)"
    Write-Host "  - internal_tps_wal.json    (TPS 歷史)"
    Write-Host "  - gc_gw_wal.log            (GC 日誌)"
    Write-Host "  - heap_gw_wal.log          (Heap histogram)"
    Write-Host "目錄：$log_path"
    Write-Host "=========================================="
}
