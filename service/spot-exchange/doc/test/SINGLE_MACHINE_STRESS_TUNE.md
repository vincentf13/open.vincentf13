# Swift Go 16 (SFG16-73) 極限壓測優化指南 (Arrow Lake Ultra 9 深度調優版)

## 0. 一鍵編譯與綁核啟動 (Master Startup Script)
此腳本為系統最終極優化版本，已實作：
1. **OS 層級配置**：鎖定 0.5ms 高精度時鐘、強制「卓越效能」電源計畫、關閉網卡封包合併與省電模式。
2. **記憶體配置**：預分配 Aeron 磁碟空間，啟用 Large Pages (大頁記憶體)，Heap 容量收斂至 2GB。
3. **精確綁核與中斷監控**：徹底隔離業務核心 (Core 2-7) 與 OS/Netty Boss/Benchmark 核心，並於壓測期間自動收集核心中斷佔比與內部飽和度指標。

請將以下內容存為 `.ps1` 執行：

```powershell
# ==============================================================================
# Aeron 交易系統自動化部署與啟動腳本 (高效能隔離版 + OS 深度優化)
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

    [StructLayout(LayoutKind.Sequential)]
    private struct TOKEN_PRIVILEGES {
        public int PrivilegeCount;
        public long Luid;
        public int Attributes;
    }

    private const uint TOKEN_ADJUST_PRIVILEGES = 0x0020;
    private const uint TOKEN_QUERY = 0x0008;
    private const int SE_PRIVILEGE_ENABLED = 0x00000002;
    private const int SystemMemoryListInformation = 80;
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

            int command = MemoryPurgeStandbyList;
            int result = NtSetSystemInformation(SystemMemoryListInformation, ref command, sizeof(int));
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
$ultimateScheme = "fe8e30e8-5fe7-4e44-b2f9-e7b4495773eb" # 卓越效能

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

    if (!(Test-Path $log_path)) { New-Item -ItemType Directory -Force $log_path }

    # --- 1. 強制清理舊環境 ---
    Write-Host "正在停止舊 Java 進程並清理共享記憶體..."
    Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Seconds 3

    # 清空 Standby List：釋放 OS 快取佔用的物理頁框，整合連續記憶體供 Large Pages 使用
    Write-Host "正在清空 Standby List 以整合連續記憶體 (Large Pages)..."
    if ([MemoryCompactor]::PurgeStandbyList()) {
        Write-Host "Standby List 已清空，可用實體記憶體已最大化"
    } else {
        Write-Warning "無法清空 Standby List (需要管理員權限)，Large Pages 分配可能失敗"
    }
    Start-Sleep -Seconds 2

    Safe-Remove $aeron_dir

    # --- 1.1 檔案預分配 (消除磁碟擴展延遲) ---
    if (!(Test-Path $aeron_dir)) { New-Item -ItemType Directory -Force $aeron_dir }
    Write-Host "正在執行磁碟空間預分配 (WAL & State: 1GB)..."
    fsutil file createnew "$aeron_dir\wal-spot-exchange.dat" 1073741824 | Out-Null
    fsutil file createnew "$aeron_dir\matching-engine-state.dat" 1073741824 | Out-Null
    New-Item -ItemType Directory -Force "$aeron_dir\map" | Out-Null

    # --- 2. 執行 Maven 編譯與解壓 ---
    Write-Host "正在執行 Maven 構建與並行提取層級化 Jar..."
    mvn -f "$base_path\pom.xml" clean package -DskipTests -T 3C -pl service/spot-exchange/spot-matching,service/spot-exchange/spot-ws-api -am | Out-Null
    
    $m_t = "$base_path\service\spot-exchange\spot-matching\target"
    $m_dest = "$m_t\extracted"
    $g_t = "$base_path\service\spot-exchange\spot-ws-api\target"
    $g_dest = "$g_t\extracted"

    $job1 = Start-Job -ScriptBlock { param($t, $d) java -Djarmode=layertools -jar "$t\spot-matching.jar" extract --destination "$d/" } -ArgumentList $m_t, $m_dest
    $job2 = Start-Job -ScriptBlock { param($t, $d) java -Djarmode=layertools -jar "$t\spot-ws-api.jar" extract --destination "$d/" } -ArgumentList $g_t, $g_dest
    Wait-Job $job1, $job2 | Out-Null
    Remove-Job $job1, $job2

    # --- 3. 啟動 獨立式 Media Driver ---
    Write-Host "Starting Standalone Media Driver..."
    $aeron_jar_item = Get-ChildItem "$m_dest\dependencies\BOOT-INF\lib\aeron-all-*.jar" | Select-Object -First 1
    
    $driver_args = @(
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
        "-Xms512m", "-Xmx512m",
        "-cp", "$($aeron_jar_item.FullName)",
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
    $driver.ProcessorAffinity = 53251 # Mask for 0, 1, 12, 13, 14, 15
    $driver.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::High

    # --- 4. 啟動 Matching Engine (4GB, P-Core 2) ---
    Write-Host "Starting Matching Engine (4GB, P-Core 2)..."
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
    $e.MinWorkingSet = $e.MaxWorkingSet

    # --- 5. 啟動 Gateway (WS-API) (4GB, P-Cores 3-7) ---
    Write-Host "Starting Gateway (WS-API) (4GB, P-Cores 3-7)..."
    $gw_args = @(
        "@$doc_path\jvm\ws-api-throughput.args",
        "-Dspot.affinity.cores=3,4,5,6,7",
        "-Daeron.driver.enabled=false",
        "-Daeron.dir=$aeron_dir",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.ws.WsApiApp"
    )
    $g = Start-Process java -ArgumentList $gw_args -WorkingDirectory $g_dest -RedirectStandardError "$log_path\error_gw.log" -RedirectStandardOutput "$log_path\stdout_gw.log" -PassThru -WindowStyle Hidden
    $g.ProcessorAffinity = 49403 # 核心 3-7 (worker) + 共享 0, 1, 14, 15 (GC/JIT)
    $g.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::RealTime
    $g.MinWorkingSet = $g.MaxWorkingSet

    Write-Host "等待服務穩態 (20s)..."
    Start-Sleep -Seconds 20

    # --- 6. 啟動 Java Benchmark (Cores 8-11) ---
    Write-Host "Starting Java Benchmark (Cores 8-11)..."
    $bench_args = @(
        "@$doc_path\jvm\benchmark-low-latency.args",
        "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
        "open.vincentf13.service.spot.ws.benchmark.BenchmarkTool",
        "60000", "30", "60"
    )
    $bench = Start-Process java -ArgumentList $bench_args -WorkingDirectory $g_dest -PassThru -NoNewWindow -RedirectStandardOutput "$log_path\benchmark_result.log"
    $bench.ProcessorAffinity = 3840 # Cores 8, 9, 10, 11
    $bench.PriorityClass = [System.Diagnostics.ProcessPriorityClass]::High
    Write-Host "Benchmark (PID: $($bench.Id)) 已啟動，開始背景中斷監控..."

    # 背景監控核心 2-7 的中斷佔比
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

    # --- 7. 抓取內部指標數據 ---
    Write-Host "正在從接口抓取內部飽和度與 TPS 指標..."
    try {
        $saturation = Invoke-RestMethod -Uri "http://localhost:8082/api/test/metrics/saturation" -ErrorAction Stop
        $tps = Invoke-RestMethod -Uri "http://localhost:8082/api/test/metrics/tps" -ErrorAction Stop
        $saturation | ConvertTo-Json -Depth 10 | Out-File "$log_path\internal_saturation.json"
        $tps | ConvertTo-Json -Depth 10 | Out-File "$log_path\internal_tps.json"
    } catch {
        Write-Warning "抓取內部指標失敗: $($_.Exception.Message)"
    }

} finally {
    Write-Host ">>> [OS] 還原系統環境與網卡設定並清理進程..."
    if ([PSObject].Assembly.GetType("HighPrecisionTimer")) { [HighPrecisionTimer]::ResetResolution() }
    if ($currentScheme) { powercfg /setactive $currentScheme }
    if ($originalCoalescing) { Set-NetAdapterAdvancedProperty -Name $nicName -DisplayName "Packet Coalescing" -DisplayValue $originalCoalescing -ErrorAction SilentlyContinue }
    if ($originalMimo) { Set-NetAdapterAdvancedProperty -Name $nicName -DisplayName "MIMO Power Save Mode" -DisplayValue $originalMimo -ErrorAction SilentlyContinue }
    
    if ($driver -and !$driver.HasExited) { Stop-Process -Id $driver.Id -Force }
    if ($e -and !$e.HasExited) { Stop-Process -Id $e.Id -Force }
    if ($g -and !$g.HasExited) { Stop-Process -Id $g.Id -Force }

    Write-Host "分析結果已輸出至 $log_path\benchmark_result.log"
}
```
