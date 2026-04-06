# ==============================================================================
# Aeron 交易系統自動化部署與啟動腳本 (Gemini 輕量驗證版 V2)
# ==============================================================================

$base_path = "C:\iProject\open.vincentf13"
$aeron_dir = "$base_path\data\spot-exchange"
$doc_path  = "$base_path\service\spot-exchange\doc"

# --- 1. 強制清理舊環境 ---
Write-Host "正在停止舊 Java 進程..."
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2

# --- 2. 啟動 獨立式 Media Driver ---
Write-Host "Starting Standalone Media Driver..."
$aeron_jar = "$base_path\service\spot-exchange\spot-matching\target\extracted\dependencies\BOOT-INF\lib\aeron-all-1.44.0.jar"

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

# --- 3. 啟動 Matching Engine (4GB) ---
Write-Host "Starting Matching Engine (4GB)..."
$m_dest = "$base_path\service\spot-exchange\spot-matching\target\extracted"
$matching_args = @(
    "-Xlog:gc+init", "-Xlog:pagesize",
    "@$doc_path\jvm\matching-low-latency.args",
    "-Xms4G", "-Xmx4G",  # 置於 @args 之後以覆蓋 8G 設定
    "-Dspot.affinity.cores=2",
    "-Daeron.driver.enabled=false",
    "-Daeron.dir=$aeron_dir",
    "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
    "open.vincentf13.service.spot.matching.MatchingApp"
)
$e = Start-Process java -ArgumentList $matching_args -WorkingDirectory $m_dest -RedirectStandardError "$doc_path\test\error_matching.log" -RedirectStandardOutput "$doc_path\test\stdout_matching.log" -PassThru -WindowStyle Hidden
$e.ProcessorAffinity = 49159

# --- 4. 啟動 Gateway (WS-API) (4GB) ---
Write-Host "Starting Gateway (WS-API) (4GB)..."
$g_dest = "$base_path\service\spot-exchange\spot-ws-api\target\extracted"
$gw_args = @(
    "-Xlog:gc+init", "-Xlog:pagesize",
    "@$doc_path\jvm\ws-api-throughput.args",
    "-Xms4G", "-Xmx4G",  # 置於 @args 之後以覆蓋 10G 設定
    "-Dspot.affinity.cores=3,4,5,6,7,8",
    "-Daeron.driver.enabled=false",
    "-Daeron.dir=$aeron_dir",
    "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
    "open.vincentf13.service.spot.ws.WsApiApp"
)
$g = Start-Process java -ArgumentList $gw_args -WorkingDirectory $g_dest -RedirectStandardError "$doc_path\test\error_gw.log" -RedirectStandardOutput "$doc_path\test\stdout_gw.log" -PassThru -WindowStyle Hidden
$g.ProcessorAffinity = 49659

Start-Sleep -Seconds 20

# --- 5. 啟動 Java Benchmark ---
Write-Host "Starting Java Benchmark..."
$bench_args = @(
    "-Xlog:gc+init", "-Xlog:pagesize",
    "@$doc_path\jvm\benchmark-low-latency.args",
    "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
    "open.vincentf13.service.spot.ws.benchmark.BenchmarkTool",
    "60000",   # 6萬 orders/sec
    "15",      # 15秒測量
    "15"       # 15秒預熱
)
$bench = Start-Process java -ArgumentList $bench_args -WorkingDirectory $g_dest -PassThru -NoNewWindow -RedirectStandardOutput "$doc_path\test\benchmark_result.log"
$bench.ProcessorAffinity = 52227
Write-Host "Benchmark (PID: $($bench.Id)) 已啟動，等待完成..."
$bench.WaitForExit()

# --- 6. 清理 ---
Write-Host "壓測完成，正在關閉服務..."
Get-Process -Id $driver.Id, $e.Id, $g.Id -ErrorAction SilentlyContinue | Stop-Process -Force

Write-Host "分析結果已輸出至 $doc_path\test\benchmark_result.log"
