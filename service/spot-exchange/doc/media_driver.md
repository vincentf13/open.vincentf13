```
# 啟動 獨立式 Media Driver (CPU 0-1, Mask: 3)
$m_t = "C:\iProject\open.vincentf13\service\spot-exchange\spot-matching\target"
$m_dest = "$m_t\extracted"
if(Test-Path $m_dest){ rm $m_dest -Recurse -Force }
java -Djarmode=layertools -jar "$m_t\spot-matching.jar" extract --destination "$m_dest/"

"Starting Standalone Media Driver on CPU 0-1..."
# 直接定位 aeron-all jar，避免 classpath 解析問題
$aeron_jar = (Get-ChildItem "$m_dest\dependencies\BOOT-INF\lib\aeron-all-*.jar").FullName
$driver_args = "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED", "-Xms512m", "-Xmx512m", "-cp", $aeron_jar, "-Daeron.dir=$aeron_dir", "io.aeron.driver.MediaDriver"

$driver = Start-Process java -ArgumentList $driver_args -PassThru
```