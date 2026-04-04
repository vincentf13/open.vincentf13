# 現貨交易系統 E2E 整合測試指南

## 測試範圍
驗證完整訂單生命週期：Auth → Deposit → Place Order → Partial Fill → Full Fill → Cancel → 最終結算

## 一鍵執行腳本
```powershell
# ==============================================================================
# E2E 整合測試自動化腳本
# 功能：清理舊數據 → 編譯 → 啟動 Media Driver / Matching / Gateway → 執行 E2E 測試
# ==============================================================================

# --- 0. 路徑配置 ---
$base_path = "C:\iProject\open.vincentf13"
$data_dir  = "$base_path\data\spot-exchange"
$doc_path  = "$base_path\service\spot-exchange\doc"
$test_dir  = "$doc_path\test"

function Safe-Remove($path) {
    if (-not [string]::IsNullOrWhiteSpace($path) -and (Test-Path $path) -and $path -like "*open.vincentf13*") {
        Write-Host "清理: $path"
        Remove-Item $path -Recurse -Force
    }
}

# --- 1. 停止舊進程 + 清理數據 ---
Write-Host "=== [1/6] 停止舊進程，清理數據 ==="
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
Get-Process -Name node -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -eq '' } | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2
Safe-Remove $data_dir

# --- 2. Maven 編譯 ---
Write-Host "=== [2/6] Maven 編譯 ==="
mvn -f "$base_path\pom.xml" clean package -DskipTests -T 3C -pl service/spot-exchange/spot-matching,service/spot-exchange/spot-ws-api -am
if ($LASTEXITCODE -ne 0) { Write-Error "Maven 編譯失敗"; return }

# --- 3. 解壓 Jar (並行) ---
Write-Host "=== [3/6] 解壓 Jar ==="
$m_t = "$base_path\service\spot-exchange\spot-matching\target"
$m_dest = "$m_t\extracted"
$g_t = "$base_path\service\spot-exchange\spot-ws-api\target"
$g_dest = "$g_t\extracted"
Safe-Remove $m_dest; Safe-Remove $g_dest

$job1 = Start-Job -ScriptBlock { param($t, $d) java -Djarmode=layertools -jar "$t\spot-matching.jar" extract --destination "$d/" } -ArgumentList $m_t, $m_dest
$job2 = Start-Job -ScriptBlock { param($t, $d) java -Djarmode=layertools -jar "$t\spot-ws-api.jar" extract --destination "$d/" } -ArgumentList $g_t, $g_dest
Wait-Job $job1, $job2 | Out-Null
Receive-Job $job1, $job2; Remove-Job $job1, $job2

# --- 4. 啟動 Media Driver ---
Write-Host "=== [4/6] 啟動 Media Driver ==="
$aeron_jar = (Get-ChildItem "$m_dest\dependencies\BOOT-INF\lib\aeron-all-*.jar" | Select-Object -First 1).FullName
$driver = Start-Process java -ArgumentList @(
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
    "-Xms512m", "-Xmx512m", "-cp", "$aeron_jar",
    "-Daeron.threading.mode=DEDICATED",
    "-Daeron.conductor.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy",
    "-Daeron.sender.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy",
    "-Daeron.receiver.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy",
    "-Daeron.term.buffer.length=64m", "-Daeron.ipc.term.buffer.length=128m",
    "-Daeron.dir=$data_dir", "io.aeron.driver.MediaDriver"
) -PassThru

# 等待 cnc.dat 就緒
$cnc = "$data_dir\cnc.dat"; $retry = 0
while (-not (Test-Path $cnc) -and $retry -lt 50) { Start-Sleep -Milliseconds 100; $retry++ }
Write-Host "Media Driver 就緒 ($($retry * 100) ms)"

# --- 5. 啟動 Matching + Gateway (並行) ---
Write-Host "=== [5/6] 啟動 Matching Engine + Gateway ==="
$matching_args = @(
    "@$doc_path\jvm\matching-low-latency.args",
    "-Daeron.driver.enabled=false", "-Daeron.dir=$data_dir",
    "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
    "open.vincentf13.service.spot.matching.MatchingApp"
)
$gw_args = @(
    "@$doc_path\jvm\ws-api-throughput.args",
    "-Daeron.driver.enabled=false", "-Daeron.dir=$data_dir",
    "-cp", "application/BOOT-INF/classes;application/BOOT-INF/lib/*;dependencies/BOOT-INF/lib/*;spring-boot-loader/",
    "open.vincentf13.service.spot.ws.WsApiApp"
)

$log_dir = "$test_dir\log"
if (-not (Test-Path $log_dir)) { New-Item -ItemType Directory -Path $log_dir | Out-Null }

$e = Start-Process java -ArgumentList $matching_args -WorkingDirectory $m_dest -RedirectStandardError "$log_dir\error_matching.log" -PassThru
$g = Start-Process java -ArgumentList $gw_args -WorkingDirectory $g_dest -RedirectStandardError "$log_dir\error_gw.log" -PassThru
Write-Host "Matching (PID: $($e.Id)), Gateway (PID: $($g.Id))"

# 等待 Gateway WebSocket 就緒
Write-Host "等待服務就緒..."
$retry = 0
while ($retry -lt 30) {
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:8080/ws/spot" -Method Get -ErrorAction SilentlyContinue
        if ($r.StatusCode -eq 400 -or $r.StatusCode -eq 101) { break }
    } catch { }
    Start-Sleep -Seconds 1; $retry++
}
if ($e.HasExited) { Write-Error "Matching 啟動失敗，檢查 $log_dir\error_matching.log"; return }
if ($g.HasExited) { Write-Error "Gateway 啟動失敗，檢查 $log_dir\error_gw.log"; return }
Write-Host "服務就緒 ($retry s)"

# --- 6. 執行 E2E 測試 ---
Write-Host "=== [6/6] 執行 E2E 整合測試 ==="
Set-Location $test_dir
node e2e-integration-test.js
```

## 手動執行步驟
若不使用一鍵腳本：
1. 清除舊數據：`Remove-Item C:\iProject\open.vincentf13\data\spot-exchange -Recurse -Force`
2. 啟動 `MatchingApp` 與 `WsApiApp`（IDE 或命令列）
3. `cd service/spot-exchange/doc/test && npm install ws && node e2e-integration-test.js`

## 測試階段說明

| 階段 | 操作 | 驗證內容 |
|------|------|----------|
| 1. 認證與充值 | A 充值 100,000 USDT；B 充值 10 BTC | 可用餘額正確 |
| 2. 掛單與凍結 | A 買 1 BTC @ 60,000 | 凍結 60,000 USDT，狀態 NEW(0) |
| 3. 部分成交 | B 賣 0.4 BTC @ 60,000 | A 成交 0.4，狀態 PARTIALLY_FILLED(1)；B 獲 24,000 USDT |
| 4. 完全成交 | B 賣 1 BTC @ 60,000 (0.6 撮合 + 0.4 掛單) | A 狀態 FILLED(2)；B 有 0.4 BTC 凍結 |
| 5. 撤單與解凍 | B 撤銷 0.4 BTC 賣單 | 狀態 CANCELED(3)；凍結歸零 |
| 最終結算 | 驗證雙方所有資產 | A: 40,000 USDT + 1 BTC；B: 60,000 USDT + 9 BTC |

## 監控接口
- TPS 歷史：`GET http://localhost:8082/api/test/metrics/tps`
- 系統飽和度：`GET http://localhost:8082/api/test/metrics/saturation`
- 餘額查詢：`GET http://localhost:8082/api/test/balance?userId={id}&assetId={id}`
- 訂單查詢：`GET http://localhost:8082/api/test/order_by_cid?userId={id}&cid={cid}`
