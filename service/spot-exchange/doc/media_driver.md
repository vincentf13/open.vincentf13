  請複製以下內容到對應的 VM options：
   * MatchingApp: @service/spot-exchange/doc/jvm/matching-low-latency.args
   * WsApiApp: @service/spot-exchange/doc/jvm/ws-api-throughput.args

```powershell
# 1. 嚴格變數定義
$m_t = "C:\iProject\open.vincentf13\service\spot-exchange\spot-matching\target"
$m_dest = Join-Path $m_t "extracted"
$aeron_dir = "C:\iProject\open.vincentf13\data\spot-exchange\aeron"

# 2. 安全閘門：如果變數為空或長度過短，直接中斷執行
if ([string]::IsNullOrWhiteSpace($m_dest) -or $m_dest.Length -lt 10) {
    Write-Error "危險：目標路徑無效或過短，腳本已停止以防止誤刪。"
    exit
}

# 3. 執行清理（加入特定資料夾名稱過濾，增加雙重保險）
if (Test-Path $m_dest) {
    if ($m_dest -like "*extracted*") {
        Write-Host "正在安全清理目錄：「**$m_dest**」..."
        Remove-Item $m_dest -Recurse -Force
    }
}

# 4. 執行 Layer 提取
if (Test-Path "$m_t\spot-matching.jar") {
    java -Djarmode=layertools -jar "$m_t\spot-matching.jar" extract --destination "$m_dest/"
} else {
    Write-Error "找不到 spot-matching.jar，請先執行 mvn package。"
    exit
}

# 5. 確保 Aeron 目錄存在並清理舊的信號文件（防止殘留干擾）
if (-not (Test-Path $aeron_dir)) {
    New-Item -ItemType Directory -Path $aeron_dir -Force | Out-Null
} else {
    # 僅清理信號文件，保留目錄
    Remove-Item (Join-Path $aeron_dir "*") -Force -Recurse -ErrorAction SilentlyContinue
}

# 6. 啟動 Media Driver
Write-Host "Starting Standalone Media Driver at $aeron_dir..."
$aeron_jar_item = Get-ChildItem "$m_dest\dependencies\BOOT-INF\lib\aeron-all-*.jar" | Select-Object -First 1

if ($null -eq $aeron_jar_item) {
    Write-Error "找不到 aeron-all jar，請檢查提取路徑。"
    exit
}

$aeron_jar = $aeron_jar_item.FullName
$driver_args = @(
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
    "-Xms512m",
    "-Xmx512m",
    "-cp", "$aeron_jar",
    "-Daeron.dir=$aeron_dir",
    "io.aeron.driver.MediaDriver"
)

$driver = Start-Process java -ArgumentList $driver_args -PassThru
```